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
package com.loom.engine.executor;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.NodeType;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowNode;
import com.loom.domain.WorkspaceKnowledge;
import com.loom.engine.StateManager;
import com.loom.engine.graph.SupervisorExecutionPlan;
import com.loom.engine.graph.SupervisorResponse;
import com.loom.engine.node.NodeResult;
import com.loom.engine.node.NodeRunner;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.WorkspaceKnowledgeRepository;
import com.loom.transport.SSEManager;

@Slf4j
public class SupervisorExecutor {

    private final StateManager stateManager;
    private final NodeRunner nodeRunner;
    private final ExecutorService workerExecutor;
    private final WorkspaceKnowledgeRepository knowledgeRepository;
    private final SessionRepository sessionRepository;
    private final SSEManager sseManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public SupervisorExecutor(
            StateManager stateManager,
            NodeRunner nodeRunner,
            ExecutorService workerExecutor,
            WorkspaceKnowledgeRepository knowledgeRepository,
            SessionRepository sessionRepository,
            SSEManager sseManager) {
        this.stateManager = stateManager;
        this.nodeRunner = nodeRunner;
        this.workerExecutor = workerExecutor;
        this.knowledgeRepository = knowledgeRepository;
        this.sessionRepository = sessionRepository;
        this.sseManager = sseManager;
    }

    public void run(
            Session session,
            SupervisorExecutionPlan plan,
            String inputContext,
            long timeoutSeconds) {

        String sessionId = session.getId();
        WorkflowNode supervisorNode = plan.getSupervisorNode();

        // Build the permitted worker ID set once - only these may be dispatched
        Set<String> permittedWorkerIds = new HashSet<>();
        plan.getWorkerNodes().forEach(workerNode -> permittedWorkerIds.add(workerNode.getId()));

        // Set session to Running
        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        // Initialize all nodes are PENDING (supervisor + worker)
        plan.getOrderedNodes()
                .forEach(
                        node ->
                                stateManager.setStatus(
                                        sessionId, node.getId(), AgentExecutionStatus.PENDING));

        broadcast(sessionId, EventType.SESSION_STARTED, Map.of());

        List<WorkspaceKnowledge> workerReports = Collections.EMPTY_LIST;
        int iteration = 0;
        boolean done = false;
        boolean parseFailed = false;
        boolean anyWorkersFailed = false;

        while (iteration < plan.getMaxIterations() && !done) {
            // --- SUPERVISOR TURN ---
            String supervisorCtx = buildSupervisorContext(inputContext, workerReports, iteration);
            NodeResult supResult =
                    nodeRunner.run(sessionId, supervisorNode, supervisorCtx, timeoutSeconds);

            if (!AgentExecutionStatus.COMPLETED.equals(supResult.status())) {
                parseFailed = true;
                break;
            }

            String superVisorOutput = supResult.output();
            Optional<SupervisorResponse> parsedOpt = parseSupervisorResponse(superVisorOutput);

            if (parsedOpt.isEmpty()) {
                log.warn("Failed to parse supervisor response for sessionId={}", sessionId);
                parseFailed = true;
                break;
            }

            SupervisorResponse supervisorResponse = parsedOpt.get();
            if (supervisorResponse.isDone()) {
                done = true;
                break;
            }

            // --- WORKER DISPATCH BATCH ---
            List<String> rawDispatch =
                    supervisorResponse.getDispatchTo() != null
                            ? supervisorResponse.getDispatchTo()
                            : List.of();

            List<String> toDispatch = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String id : rawDispatch) {
                // Deduplicate
                if (!seen.add(id)) {
                    continue;
                }
                WorkflowNode candidate = plan.getNode(id);
                if (candidate == null) {
                    log.warn("runSupervisor: dispatcher returned unknown nodeId={}, skipping", id);
                    continue;
                }
                if (!NodeType.WORKER.equals(candidate.getNodeType())
                        || !permittedWorkerIds.contains(id)) {
                    log.warn(
                            "runSupervisor: nodeId={} is not a permitted WORKER node, skipping",
                            id);
                    continue;
                }
                toDispatch.add(id);
            }

            // Submit each worker and wait for the batch to complete.
            boolean[] workerFailed = {false};
            List<Future<?>> outerFutures =
                    dispatchWorkerBatch(
                            sessionId,
                            toDispatch,
                            plan,
                            inputContext,
                            timeoutSeconds,
                            workerFailed);
            anyWorkersFailed |=
                    joinWorkerBatch(sessionId, outerFutures, toDispatch.size(), timeoutSeconds);

            if (workerFailed[0]) {
                anyWorkersFailed = true;
            }

            workerReports =
                    knowledgeRepository != null
                            ? knowledgeRepository.findBySessionId(sessionId)
                            : Collections.EMPTY_LIST;

            broadcast(
                    sessionId,
                    EventType.WORKFLOW_STATE_CHANGE,
                    Map.of("iteration", iteration, "dispatchedTo", toDispatch));

            iteration++;
        }

        // --- COMPUTE TERMINAL STATUS ---
        SessionStatus terminalStatus;
        if (done && !anyWorkersFailed && !parseFailed) {
            terminalStatus = SessionStatus.COMPLETED;
        } else if (parseFailed || iteration >= plan.getMaxIterations()) {
            terminalStatus = SessionStatus.PARTIAL;
        } else if (anyWorkersFailed) {
            terminalStatus = SessionStatus.PARTIAL;
        } else {
            terminalStatus = SessionStatus.COMPLETED;
        }

        session.setStatus(terminalStatus);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        EventType terminalEvent =
                (SessionStatus.COMPLETED.equals(terminalStatus))
                        ? EventType.SESSION_COMPLETED
                        : EventType.SESSION_FAILED;

        broadcast(sessionId, terminalEvent, Map.of());
    }

    private List<Future<?>> dispatchWorkerBatch(
            String sessionId,
            List<String> toDispatch,
            SupervisorExecutionPlan plan,
            String inputContext,
            long timeoutSeconds,
            boolean[] workerFailedRef) {
        List<Future<?>> outerFutures = new ArrayList<>();
        for (String nodeId : toDispatch) {
            WorkflowNode workerNode = plan.getNode(nodeId);
            Runnable task =
                    () -> {
                        NodeResult r =
                                nodeRunner.run(sessionId, workerNode, inputContext, timeoutSeconds);
                        if (r.status() != AgentExecutionStatus.COMPLETED) {
                            workerFailedRef[0] = true;
                        }
                    };
            try {
                outerFutures.add(workerExecutor.submit(task));
            } catch (RejectedExecutionException ree) {
                log.warn(
                        "runSupervisor: workerExecutor rejected node '{}' in session {}",
                        nodeId,
                        sessionId);
                stateManager.setStatus(sessionId, workerNode.getId(), AgentExecutionStatus.FAILED);
                broadcast(
                        sessionId,
                        EventType.NODE_FAILED,
                        Map.of("nodeId", workerNode.getId(), "reason", "rejected"));
                workerFailedRef[0] = true;
            }
        }
        return outerFutures;
    }

    private boolean joinWorkerBatch(
            String sessionId, List<Future<?>> outerFutures, int workerCount, long timeoutSeconds) {
        long batchDeadline =
                System.currentTimeMillis() + (workerCount + 1) * timeoutSeconds * 1000L;
        try {
            for (int i = 0; i < outerFutures.size(); i++) {
                long remaining = batchDeadline - System.currentTimeMillis();
                if (remaining > 0) {
                    outerFutures.get(i).get(remaining, TimeUnit.MILLISECONDS);
                } else {
                    log.warn("runSupervisor: worker batch timed out for session {}", sessionId);
                    for (int j = i; j < outerFutures.size(); j++) {
                        outerFutures.get(j).cancel(true);
                    }
                    return true;
                }
            }
        } catch (TimeoutException bte) {
            log.warn("runSupervisor: worker batch timed out for session {}", sessionId);
            for (Future<?> f : outerFutures) {
                f.cancel(true);
            }
            return true;
        } catch (Exception ignored) {
            // individual worker failures are already recorded inside NodeRunner
        }
        return false;
    }

    private Optional<SupervisorResponse> parseSupervisorResponse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return Optional.empty();
        }
        try {
            SupervisorResponse resp = mapper.readValue(rawOutput.trim(), SupervisorResponse.class);
            return Optional.of(resp);
        } catch (Exception ignored) {
            // not pure JSON — fall through to extraction
        }
        SupervisorResponse last = null;
        try (JsonParser jp = mapper.createParser(rawOutput)) {
            JsonToken token;
            while ((token = jp.nextToken()) != null) {
                if (JsonToken.START_OBJECT.equals(token)) {
                    try {
                        last = mapper.readValue(jp, SupervisorResponse.class);
                    } catch (Exception ignored) {
                        // not a valid SupervisorResponse at this position — keep scanning
                    }
                }
            }
        } catch (Exception ignored) {
            // malformed JSON stream — fall through
        }
        return last != null ? Optional.of(last) : Optional.empty();
    }

    private String buildSupervisorContext(
            String goal, List<WorkspaceKnowledge> reports, int iteration) {
        StringBuilder sb = new StringBuilder();
        sb.append("GOAL: ").append(goal).append("\n\n");
        sb.append("ITERATION: ").append(iteration).append("\n\n");
        if (reports.isEmpty()) {
            sb.append("No worker reports yet.\n");
        } else {
            sb.append("WORKER REPORTS:\n");
            for (WorkspaceKnowledge r : reports) {
                sb.append("- ")
                        .append(r.getTitle())
                        .append(": ")
                        .append(r.getContent())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Broadcasts a {@link WorkflowEvent} to all connected SSE clients. Exceptions are swallowed and
     * logged so that broadcast failures never interrupt session execution.
     */
    private void broadcast(String sessionId, EventType type, Map<String, Object> data) {
        try {
            WorkflowEvent event =
                    WorkflowEvent.builder()
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
