/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.loom.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.*;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.llm.LLMGateway;
import com.loom.llm.LLMRequest;
import com.loom.llm.LLMResponse;
import com.loom.mcp.MCPClient;
import com.loom.storage.repository.*;
import com.loom.transport.SSEManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes a single-agent workflow node:
 * <ol>
 *   <li>Loads skill markdown from {@link SkillRepository}</li>
 *   <li>Fetches workspace knowledge scoped to the session's workspaceId</li>
 *   <li>Calls {@link ContextBuilder#build} to assemble the {@link LLMRequest}</li>
 *   <li>Streams tokens via {@link LLMGateway#send}, broadcasting
 *       {@link EventType#AGENT_TOKEN} events</li>
 *   <li>Handles tool_use calls via {@link MCPClient}</li>
 *   <li>Persists output via {@link ReportWriter} and broadcasts
 *       {@link EventType#AGENT_REPORT_WRITTEN}</li>
 *   <li>Broadcasts {@link EventType#SESSION_COMPLETED} or
 *       {@link EventType#SESSION_FAILED}</li>
 * </ol>
 */
@Slf4j
public class AgentRuntime {

    private final LLMGateway                llmGateway;
    private final MCPClient                 mcpClient;
    private final SSEManager                sseManager;
    private final SkillRepository           skillRepository;
    private final WorkspaceKnowledgeRepository knowledgeRepository;
    private final AgentExecutionRepository  executionRepository;
    private final SessionRepository         sessionRepository;
    private final WorkflowRepository        workflowRepository;
    private final AgentRepository           agentRepository;
    private final ContextBuilder            contextBuilder;
    private final ReportWriter              reportWriter;
    private final ObjectMapper              mapper = new ObjectMapper();

    public AgentRuntime(LLMGateway llmGateway,
                        MCPClient mcpClient,
                        SSEManager sseManager,
                        SkillRepository skillRepository,
                        WorkspaceKnowledgeRepository knowledgeRepository,
                        AgentExecutionRepository executionRepository,
                        SessionRepository sessionRepository,
                        WorkflowRepository workflowRepository,
                        AgentRepository agentRepository) {
        this.llmGateway         = llmGateway;
        this.mcpClient          = mcpClient;
        this.sseManager         = sseManager;
        this.skillRepository    = skillRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.executionRepository = executionRepository;
        this.sessionRepository  = sessionRepository;
        this.workflowRepository = workflowRepository;
        this.agentRepository    = agentRepository;
        this.contextBuilder     = new ContextBuilder(mcpClient);
        this.reportWriter       = new ReportWriter(knowledgeRepository);
    }

    /**
     * Runs the full execution chain for the given session:
     * sets status to RUNNING, iterates over workflow nodes in order,
     * then marks the session COMPLETED or FAILED.
     *
     * @param sessionId    id of the session to run
     * @param inputContext the user-supplied input context for the first node
     */
    public void execute(String sessionId, String inputContext) {
        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("AgentRuntime: session not found: {}", sessionId);
            broadcast(sessionId, EventType.SESSION_FAILED, Map.of("error", "session not found"));
            return;
        }
        Session session = sessionOpt.get();
        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        broadcast(sessionId, EventType.SESSION_STARTED, Map.of());

        // Load the workflow definition and get ordered nodes
        Optional<WorkflowDefinition> wfOpt = workflowRepository.findById(session.getWorkflowDefinitionId());
        if (wfOpt.isEmpty()) {
            failSession(session, "workflow definition not found");
            return;
        }
        WorkflowDefinition workflow = wfOpt.get();
        List<WorkflowNode> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            failSession(session, "workflow has no nodes");
            return;
        }

        // Chain execution: each node's output becomes the next node's input
        String contextCarry = inputContext;
        for (WorkflowNode node : nodes) {
            if (node.getNodeType() != NodeType.WORKER) continue; // skip START/END nodes

            Optional<AgentDefinition> agentOpt = agentRepository.findById(node.getAgentDefinitionId());
            if (agentOpt.isEmpty()) {
                failSession(session, "agent not found: " + node.getAgentDefinitionId());
                return;
            }
            AgentDefinition agent = agentOpt.get();

            contextCarry = runNode(session, node, agent, contextCarry);
            if (contextCarry == null) {
                // runNode already marked session FAILED
                return;
            }
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);
        broadcast(sessionId, EventType.SESSION_COMPLETED, Map.of());
    }

    /**
     * Runs a single workflow node for one agent. Returns the agent's output
     * (to be used as input for the next node), or {@code null} on failure.
     */
    private String runNode(Session session, WorkflowNode node,
                           AgentDefinition agent, String inputContext) {
        // 1. Create execution record (status RUNNING)
        AgentExecution execution = new AgentExecution();
        execution.setSessionId(session.getId());
        execution.setNodeId(node.getId());
        execution.setAgentDefinitionId(agent.getId());
        execution.setStatus(AgentExecutionStatus.RUNNING);
        execution.setInputContext(inputContext);
        execution.setStartedAt(System.currentTimeMillis());
        executionRepository.save(execution);

        try {
            // 2. Load skill content
            String skillContent = null;
            if (agent.getSkillId() != null) {
                skillContent = skillRepository.findById(agent.getSkillId())
                        .map(s -> s.getContent())
                        .orElse(null);
            }

            // 3. Fetch workspace knowledge
            List<WorkspaceKnowledge> knowledge = session.getWorkspaceId() != null
                    ? knowledgeRepository.findByWorkspaceId(session.getWorkspaceId())
                    : Collections.emptyList();

            // 4. Build LLM request
            LLMRequest request = contextBuilder.build(agent, session, skillContent, inputContext, knowledge);

            // 5. Stream LLM response, collecting full output
            StringBuilder outputBuilder = new StringBuilder();
            final boolean[] hadError     = { false };
            final boolean[] hadToolCall  = { false };
            final LLMRequest[] current   = { request };

            llmGateway.send(current[0], response -> {
                switch (response.getType()) {
                    case LLMResponse.TOKEN:
                        outputBuilder.append(response.getContent());
                        broadcast(session.getId(), EventType.AGENT_TOKEN,
                                Map.of("token", response.getContent(),
                                       "agentId", agent.getId(),
                                       "nodeId", node.getId()));
                        break;

                    case LLMResponse.TOOL_CALL: {
                        String toolName = response.getToolName();
                        Map<String, Object> toolInput = response.getToolInput() != null
                                ? response.getToolInput()
                                : Collections.emptyMap();
                        broadcast(session.getId(), EventType.AGENT_TOOL_CALL,
                                Map.of("toolName", toolName, "toolInput", toolInput));
                        String toolResult = mcpClient.execute(toolName, toolInput);
                        broadcast(session.getId(), EventType.AGENT_TOOL_RESULT,
                                Map.of("toolName", toolName, "result", toolResult));
                        current[0] = appendToolResult(current[0], toolName, toolResult);
                        hadToolCall[0] = true;
                        break;
                    }

                    case LLMResponse.ERROR:
                        log.warn("LLM error for session={} node={}: {}",
                                session.getId(), node.getId(), response.getContent());
                        hadError[0] = true;
                        break;

                    case LLMResponse.DONE:
                        break;

                    default:
                        log.warn("Unknown LLMResponse type '{}' for session={}",
                                response.getType(), session.getId());
                }
            });

            if (hadError[0]) {
                markExecutionFailed(execution, "LLM returned an error");
                failSession(session, "LLM error during node " + node.getId());
                return null;
            }

            // Follow-up turn if there was a tool call
            if (hadToolCall[0]) {
                llmGateway.send(current[0], response -> {
                    if (response.getType().equals(LLMResponse.TOKEN)) {
                        outputBuilder.append(response.getContent());
                        broadcast(session.getId(), EventType.AGENT_TOKEN,
                                Map.of("token", response.getContent(),
                                       "agentId", agent.getId(),
                                       "nodeId", node.getId()));
                    } else if (response.getType().equals(LLMResponse.ERROR)) {
                        hadError[0] = true;
                    }
                });
            }

            if (hadError[0]) {
                markExecutionFailed(execution, "LLM returned an error (follow-up turn)");
                failSession(session, "LLM error during follow-up for node " + node.getId());
                return null;
            }

            String output = outputBuilder.toString();

            // 6. Persist execution as COMPLETED
            execution.setStatus(AgentExecutionStatus.COMPLETED);
            execution.setOutput(output);
            execution.setReport(output);
            execution.setCompletedAt(System.currentTimeMillis());
            executionRepository.save(execution);

            // 7. Write report to workspace knowledge
            WorkspaceKnowledge knowledge2 = reportWriter.write(
                    execution, agent.getName(), session.getWorkspaceId(), output);
            broadcast(session.getId(), EventType.AGENT_REPORT_WRITTEN,
                    Map.of("knowledgeId", knowledge2.getId(),
                           "title",       knowledge2.getTitle(),
                           "agentId",     agent.getId()));

            return output;

        } catch (Exception e) {
            log.error("Unexpected error during node execution session={} node={}",
                    session.getId(), node.getId(), e);
            markExecutionFailed(execution, e.getMessage());
            failSession(session, "unexpected error: " + e.getMessage());
            return null;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void markExecutionFailed(AgentExecution execution, String reason) {
        execution.setStatus(AgentExecutionStatus.FAILED);
        execution.setOutput(reason);
        execution.setCompletedAt(System.currentTimeMillis());
        executionRepository.save(execution);
    }

    private void failSession(Session session, String reason) {
        session.setStatus(SessionStatus.FAILED);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);
        broadcast(session.getId(), EventType.SESSION_FAILED, Map.of("error", reason));
    }

    private LLMRequest appendToolResult(LLMRequest prev, String toolName, String toolResult) {
        List<com.loom.llm.LLMMessage> history =
                prev.getHistory() != null
                        ? new ArrayList<>(prev.getHistory())
                        : new ArrayList<>();
        if (history.isEmpty()) {
            history.add(new com.loom.llm.LLMMessage("user", prev.getUserPrompt()));
        }
        history.add(new com.loom.llm.LLMMessage("assistant", "[Called tool: " + toolName + "]"));
        return LLMRequest.builder()
                .model(prev.getModel())
                .systemPrompt(prev.getSystemPrompt())
                .userPrompt("Tool result for " + toolName + ": " + toolResult)
                .history(history)
                .tools(prev.getTools())
                .maxTokens(prev.getMaxTokens())
                .build();
    }

    private void broadcast(String sessionId, EventType type, Map<String, Object> data) {
        try {
            WorkflowEvent event = WorkflowEvent.builder()
                    .eventType(type)
                    .sessionId(sessionId)
                    .data(mapper.valueToTree(data))
                    .build();
            sseManager.broadcast(event);
        } catch (Exception e) {
            log.error("Failed to broadcast {} for session={}", type, sessionId, e);
        }
    }
}
