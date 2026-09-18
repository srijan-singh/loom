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
package com.loom.transport.routes;

import com.loom.domain.AgentExecution;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.engine.WorkflowEngine;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.SessionRepository;
import io.javalin.router.JavalinDefaultRoutingApi;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers the HTTP routes that manage {@link com.loom.domain.Session} lifecycle:
 *
 * <ul>
 *   <li>{@code GET /sessions} — list all sessions
 *   <li>{@code GET /sessions/{id}} — get a session with its execution records
 *   <li>{@code POST /sessions} — create a new session
 *   <li>{@code DELETE /sessions/{id}} — delete a session (409 if active or RUNNING)
 *   <li>{@code POST /sessions/{id}/run} — start async execution; 202 on acceptance, 409 on
 *       duplicate or overload
 * </ul>
 */
@Slf4j
public class SessionRoutes {

    private final SessionRepository sessionRepository;
    private final WorkflowEngine workflowEngine;
    private final AgentExecutionRepository executionRepository;

    /**
     * Constructs a SessionRoutes with the required collaborators.
     *
     * @param sessionRepository repository for reading, creating, and deleting session records
     * @param workflowEngine engine used to submit and track async workflow runs
     * @param executionRepository repository for reading agent execution records
     */
    public SessionRoutes(
            SessionRepository sessionRepository,
            WorkflowEngine workflowEngine,
            AgentExecutionRepository executionRepository) {
        this.sessionRepository = sessionRepository;
        this.workflowEngine = workflowEngine;
        this.executionRepository = executionRepository;
    }

    /**
     * Registers all session-related routes on the given router.
     *
     * @param router the Javalin routing API to register routes on
     */
    public void register(JavalinDefaultRoutingApi router) {

        router.get("/sessions", ctx -> ctx.json(sessionRepository.findAll()));

        router.get(
                "/sessions/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    Optional<Session> found = sessionRepository.findById(id);
                    if (found.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                    } else {
                        List<AgentExecution> executions =
                                executionRepository.findAll().stream()
                                        .filter(e -> id.equals(e.getSessionId()))
                                        .collect(java.util.stream.Collectors.toList());
                        ctx.json(Map.of("session", found.get(), "executions", executions));
                    }
                });

        router.post(
                "/sessions",
                ctx -> {
                    SessionCreateRequest body = ctx.bodyAsClass(SessionCreateRequest.class);
                    if (body.workspaceId == null || body.workspaceId.isBlank()) {
                        ctx.status(400).json(RouteHelper.error("workspaceId is required"));
                        return;
                    }
                    if (body.workflowDefinitionId == null || body.workflowDefinitionId.isBlank()) {
                        ctx.status(400).json(RouteHelper.error("workflowDefinitionId is required"));
                        return;
                    }
                    Session session = new Session();
                    session.setWorkspaceId(body.workspaceId);
                    session.setWorkflowDefinitionId(body.workflowDefinitionId);
                    session.setStatus(SessionStatus.CREATED);
                    session.setStartedAt(System.currentTimeMillis());
                    sessionRepository.save(session);
                    ctx.status(201).json(session);
                });

        router.delete(
                "/sessions/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    Optional<Session> found = sessionRepository.findById(id);
                    if (found.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                        return;
                    }
                    if (found.get().getStatus() == SessionStatus.RUNNING
                            || workflowEngine.isActive(id)) {
                        ctx.status(409).json(Map.of("status", "conflict", "sessionId", id));
                        return;
                    }
                    sessionRepository.delete(id);
                    ctx.status(204);
                });

        router.post(
                "/sessions/{id}/run",
                ctx -> {
                    String sessionId = ctx.pathParam("id");
                    Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
                    if (sessionOpt.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                        return;
                    }

                    // Resolve input context from query param or body
                    String inputContext = ctx.queryParam("prompt");
                    if (inputContext == null || inputContext.isBlank()) {
                        String rawBody = ctx.body();
                        if (!rawBody.isBlank()) {
                            try {
                                SessionRunRequest req = ctx.bodyAsClass(SessionRunRequest.class);
                                inputContext = req.prompt != null ? req.prompt : "";
                            } catch (Exception ignored) {
                                inputContext = rawBody;
                            }
                        } else {
                            inputContext = "";
                        }
                    }
                    final String finalInput = inputContext;

                    boolean accepted = workflowEngine.runAsync(sessionId, finalInput);
                    if (!accepted) {
                        ctx.status(409).json(Map.of("status", "conflict", "sessionId", sessionId));
                        return;
                    }
                    ctx.status(202).json(Map.of("status", "started", "sessionId", sessionId));
                });
    }

    /** Simple request body for POST /sessions. */
    public static final class SessionCreateRequest {
        public String workspaceId;
        public String workflowDefinitionId;
    }

    /** Request body for POST /sessions/{id}/run. */
    public static final class SessionRunRequest {
        public String prompt;
    }
}
