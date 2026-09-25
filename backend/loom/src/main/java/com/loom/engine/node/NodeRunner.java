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
package com.loom.engine.node;

import static com.loom.domain.AgentExecutionStatus.COMPLETED;
import static com.loom.domain.AgentExecutionStatus.FAILED;
import static com.loom.domain.AgentExecutionStatus.QUEUED;
import static com.loom.domain.AgentExecutionStatus.RUNNING;
import static com.loom.domain.AgentExecutionStatus.TIMED_OUT;
import static com.loom.event.EventType.NODE_QUEUED;
import static com.loom.event.EventType.NODE_RUNNING;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.WorkflowNode;
import com.loom.engine.StateManager;
import com.loom.engine.agent.AgentRuntime;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.transport.SSEManager;

@AllArgsConstructor
@Slf4j
public class NodeRunner {
    private final AgentRuntime agentRuntime;
    private final StateManager stateManager;
    private final ExecutorService executorService;
    private final SSEManager sseManager;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * TODO
     *
     * @param sessionId
     * @param node
     * @param ctx
     * @param timeoutSeconds
     * @return
     */
    public NodeResult run(String sessionId, WorkflowNode node, String ctx, long timeoutSeconds) {
        stateManager.setStatus(sessionId, node.getId(), QUEUED);
        broadcast(sessionId, NODE_QUEUED, Map.of("nodeId", node.getId()));
        // prepare task
        Callable<String> nodeTask =
                () -> {
                    stateManager.setStatus(sessionId, node.getId(), RUNNING);
                    broadcast(sessionId, NODE_RUNNING, Map.of("nodeId", node.getId()));
                    return agentRuntime.executeNode(sessionId, node, ctx);
                };
        // run task
        Future<String> future = executorService.submit(nodeTask);
        try {
            String output = future.get(timeoutSeconds, TimeUnit.SECONDS);
            stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.COMPLETED);
            broadcast(sessionId, EventType.NODE_COMPLETED, Map.of("nodeId", node.getId()));
            return new NodeResult(COMPLETED, output);
        } catch (TimeoutException te) {
            future.cancel(true);
            stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.TIMED_OUT);
            broadcast(
                    sessionId,
                    EventType.NODE_FAILED,
                    Map.of("nodeId", node.getId(), "reason", "timeout"));
            return new NodeResult(TIMED_OUT, null);
        } catch (Exception e) {
            stateManager.setStatus(sessionId, node.getId(), AgentExecutionStatus.FAILED);
            Throwable target = e.getCause() != null ? e.getCause() : e;
            String reason =
                    target.getMessage() != null
                            ? target.getMessage()
                            : target.getClass().getSimpleName();
            broadcast(
                    sessionId,
                    EventType.NODE_FAILED,
                    Map.of("nodeId", node.getId(), "reason", reason));
            return new NodeResult(FAILED, target.getMessage());
        }
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
