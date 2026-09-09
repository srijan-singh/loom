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

import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.engine.AgentRuntime;
import com.loom.storage.repository.SessionRepository;
import io.javalin.router.JavalinDefaultRoutingApi;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SessionRoutes {

    private final SessionRepository sessionRepository;
    private final AgentRuntime agentRuntime;
    /** Guards against duplicate concurrent runs for the same session. */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = new ThreadPoolExecutor(
            4, 20, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "session-runner");
                t.setDaemon(true);
                return t;
            });

    public SessionRoutes(SessionRepository sessionRepository, AgentRuntime agentRuntime) {
        this.sessionRepository = sessionRepository;
        this.agentRuntime      = agentRuntime;
    }

    public void register(JavalinDefaultRoutingApi router) {

        router.get("/sessions", ctx -> ctx.json(sessionRepository.findAll()));

        router.get("/sessions/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Optional<Session> found = sessionRepository.findById(id);
            if (found.isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
            } else {
                ctx.json(found.get());
            }
        });

        router.post("/sessions", ctx -> {
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

        router.delete("/sessions/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Optional<Session> found = sessionRepository.findById(id);
            if (found.isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
                return;
            }
            synchronized (activeSessions) {
                if (found.get().getStatus() == SessionStatus.RUNNING || activeSessions.contains(id)) {
                    ctx.status(409).json(Map.of("status", "conflict", "sessionId", id));
                    return;
                }
                sessionRepository.delete(id);
            }
            ctx.status(204);
        });

        router.post("/sessions/{id}/run", ctx -> {
            String sessionId = ctx.pathParam("id");
            Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
                return;
            }
            Session session = sessionOpt.get();
            synchronized (activeSessions) {
                if (activeSessions.contains(sessionId)) {
                    ctx.status(409).json(Map.of("status", "conflict", "sessionId", sessionId));
                    return;
                }
                activeSessions.add(sessionId);
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

            try {
                executor.submit(() -> {
                    try {
                        agentRuntime.execute(sessionId, finalInput);
                    } catch (Exception e) {
                        log.error("Unexpected error running session {}", sessionId, e);
                    } finally {
                        activeSessions.remove(sessionId);
                    }
                });
            } catch (RejectedExecutionException e) {
                activeSessions.remove(sessionId);
                ctx.status(503).json(Map.of("status", "unavailable", "sessionId", sessionId));
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
