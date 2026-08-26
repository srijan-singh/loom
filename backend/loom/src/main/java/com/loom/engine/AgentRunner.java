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
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.llm.LLMGateway;
import com.loom.llm.LLMRequest;
import com.loom.llm.LLMResponse;
import com.loom.mcp.MCPClient;
import com.loom.transport.SSEManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives a single-agent LLM loop for a session:
 * <ol>
 *   <li>Send the request to the LLM via {@link LLMGateway#send}.</li>
 *   <li>Forward every TOKEN → {@link EventType#AGENT_TOKEN} SSE event.</li>
 *   <li>On TOOL_CALL: invoke {@link MCPClient}, broadcast
 *       {@link EventType#AGENT_TOOL_CALL} + {@link EventType#AGENT_TOOL_RESULT},
 *       then loop back with the result appended to history (one-turn).</li>
 *   <li>On DONE: broadcast {@link EventType#SESSION_COMPLETED}.</li>
 *   <li>On ERROR: broadcast {@link EventType#SESSION_FAILED}.</li>
 * </ol>
 *
 * <p>Execution is dispatched to a background thread so the HTTP handler returns
 * immediately while the SSE stream continues asynchronously.
 */
@Slf4j
public class AgentRunner {

    private final LLMGateway  llmGateway;
    private final MCPClient   mcpClient;
    private final SSEManager  sseManager;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public AgentRunner(LLMGateway llmGateway, MCPClient mcpClient, SSEManager sseManager) {
        this.llmGateway = llmGateway;
        this.mcpClient  = mcpClient;
        this.sseManager = sseManager;
        this.mapper     = new ObjectMapper();
        this.executor   = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "agent-runner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submits the LLM call to a background thread and returns immediately.
     *
     * @param sessionId the SSE channel identifier used for all emitted events
     * @param request   the fully-formed LLM request
     */
    public void runAsync(String sessionId, LLMRequest request) {
        executor.submit(() -> runBlocking(sessionId, request));
    }

    // ── internal blocking run ─────────────────────────────────────────────────

    void runBlocking(String sessionId, LLMRequest request) {
        log.info("AgentRunner starting for session={}", sessionId);
        broadcast(sessionId, EventType.SESSION_STARTED,
                Map.of("model", request.getModel() != null ? request.getModel() : ""));

        // Holds the mutable request so we can inject tool results and do one follow-up turn.
        final LLMRequest[] current     = { request };
        final boolean[]    hadToolCall = { false };
        final boolean[]    hadError    = { false };

        // First LLM turn — collect tokens, tool calls, and errors.
        // DONE here does NOT end the session; we may need a follow-up turn.
        llmGateway.send(current[0], response ->
                handleFirstTurn(sessionId, current, hadToolCall, hadError, response));

        if (hadError[0]) {
            // Error already broadcast inside handleFirstTurn; nothing more to do.
            return;
        }

        if (hadToolCall[0]) {
            // Second (follow-up) turn: the model now has the tool result in history.
            // DONE here ends the session normally.
            llmGateway.send(current[0], response ->
                    handleFinalTurn(sessionId, response));
        } else {
            // No tool call — first turn was the only turn; session is complete.
            broadcast(sessionId, EventType.SESSION_COMPLETED, Map.of());
        }
    }

    /** Handles events from the first LLM turn. DONE is silent (no SESSION_COMPLETED yet). */
    private void handleFirstTurn(String sessionId,
                                 LLMRequest[] current,
                                 boolean[] hadToolCall,
                                 boolean[] hadError,
                                 LLMResponse response) {
        switch (response.getType()) {

            case LLMResponse.TOKEN:
                broadcast(sessionId, EventType.AGENT_TOKEN,
                        Map.of("token", response.getContent()));
                break;

            case LLMResponse.TOOL_CALL: {
                String toolName           = response.getToolName();
                Map<String, Object> input = response.getToolInput();

                broadcast(sessionId, EventType.AGENT_TOOL_CALL,
                        Map.of("toolName", toolName, "toolInput", input));

                String toolResult = mcpClient.execute(toolName, input);

                broadcast(sessionId, EventType.AGENT_TOOL_RESULT,
                        Map.of("toolName", toolName, "result", toolResult));

                // Build the follow-up request with the tool result in history.
                current[0]     = appendToolResult(current[0], toolName, toolResult);
                hadToolCall[0] = true;
                break;
            }

            case LLMResponse.DONE:
                // Intentionally silent — session completion is decided after the stream ends.
                break;

            case LLMResponse.ERROR:
                log.warn("LLM error for session={}: {}", sessionId, response.getContent());
                broadcast(sessionId, EventType.SESSION_FAILED,
                        Map.of("error", response.getContent() != null ? response.getContent() : "Unknown error"));
                hadError[0] = true;
                break;

            default:
                log.warn("Unknown LLMResponse type '{}' for session={}", response.getType(), sessionId);
                break;
        }
    }

    /** Handles events from the follow-up (final) LLM turn. DONE ends the session. */
    private void handleFinalTurn(String sessionId, LLMResponse response) {
        switch (response.getType()) {

            case LLMResponse.TOKEN:
                broadcast(sessionId, EventType.AGENT_TOKEN,
                        Map.of("token", response.getContent()));
                break;

            case LLMResponse.DONE:
                broadcast(sessionId, EventType.SESSION_COMPLETED, Map.of());
                break;

            case LLMResponse.ERROR:
                log.warn("LLM error (follow-up turn) for session={}: {}", sessionId, response.getContent());
                broadcast(sessionId, EventType.SESSION_FAILED,
                        Map.of("error", response.getContent() != null ? response.getContent() : "Unknown error"));
                break;

            default:
                log.warn("Unknown LLMResponse type '{}' for session={}", response.getType(), sessionId);
                break;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a follow-up request that appends the tool result as an assistant
     * + user exchange so the LLM can continue reasoning.
     */
    private LLMRequest appendToolResult(LLMRequest prev, String toolName, String toolResult) {
        java.util.List<com.loom.llm.LLMMessage> history =
                prev.getHistory() != null
                        ? new java.util.ArrayList<>(prev.getHistory())
                        : new java.util.ArrayList<>();

        // Original user prompt as user turn (if history was empty before)
        if (history.isEmpty()) {
            history.add(new com.loom.llm.LLMMessage("user", prev.getUserPrompt()));
        }
        // Assistant "called tool X" placeholder
        history.add(new com.loom.llm.LLMMessage("assistant",
                "[Called tool: " + toolName + "]"));
        // Tool result injected as a user turn
        history.add(new com.loom.llm.LLMMessage("user",
                "Tool result for " + toolName + ": " + toolResult));

        return LLMRequest.builder()
                .model(prev.getModel())
                .systemPrompt(prev.getSystemPrompt())
                .userPrompt(prev.getUserPrompt())
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
