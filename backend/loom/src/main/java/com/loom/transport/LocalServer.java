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
package com.loom.transport;

import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import lombok.extern.slf4j.Slf4j;

import com.loom.engine.WorkflowEngine;
import com.loom.engine.agent.AgentRuntime;
import com.loom.engine.graph.GraphResolver;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.AgentRepository;
import com.loom.storage.repository.LLMConnectionRepository;
import com.loom.storage.repository.MCPConnectionRepository;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.SkillRepository;
import com.loom.storage.repository.WorkflowRepository;
import com.loom.storage.repository.WorkspaceRepository;
import com.loom.transport.routes.AgentRoutes;
import com.loom.transport.routes.LLMRoutes;
import com.loom.transport.routes.MCPRoutes;
import com.loom.transport.routes.SessionRoutes;
import com.loom.transport.routes.SkillRoutes;
import com.loom.transport.routes.WorkflowRoutes;
import com.loom.transport.routes.WorkspaceRoutes;

@Slf4j
public class LocalServer {

    private static final String EVENTS_ENDPOINT = "/events";

    /**
     * Header name sent on REST requests (e.g. {@code curl -H 'X-Loom-Token: <token>'
     * http://localhost:<port>/...}).
     */
    private static final String TOKEN_HEADER = "X-Loom-Token";

    /**
     * Query parameter name for SSE clients ({@code GET /events?token=<token>}).
     *
     * <p>Standard browser/platform EventSource implementations do not support custom request
     * headers, so token parameter fallback is supported for SSE streams.
     */
    private static final String TOKEN_PARAM = "token";

    private final String expectedToken;
    private final SSEManager sseManager;
    private final AgentRuntime agentRuntime;
    private final WorkflowEngine workflowEngine;
    private final AgentRepository agentRepository;
    private final SkillRepository skillRepository;
    private final MCPConnectionRepository mcpRepository;
    private final SessionRepository sessionRepository;
    private final AgentExecutionRepository executionRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkspaceRepository workspaceRepository;
    private final GraphResolver graphResolver;
    private final LLMConnectionRepository llmConnectionRepository;
    private Javalin app;

    public LocalServer(
            String expectedToken,
            SSEManager sseManager,
            AgentRuntime agentRuntime,
            WorkflowEngine workflowEngine,
            AgentRepository agentRepository,
            SkillRepository skillRepository,
            MCPConnectionRepository mcpRepository,
            SessionRepository sessionRepository,
            AgentExecutionRepository executionRepository,
            WorkflowRepository workflowRepository,
            WorkspaceRepository workspaceRepository,
            GraphResolver graphResolver,
            LLMConnectionRepository llmConnectionRepository) {
        this.expectedToken = expectedToken != null ? expectedToken.strip() : null;
        this.sseManager = sseManager;
        this.agentRuntime = agentRuntime;
        this.workflowEngine = workflowEngine;
        this.agentRepository = agentRepository;
        this.skillRepository = skillRepository;
        this.mcpRepository = mcpRepository;
        this.sessionRepository = sessionRepository;
        this.executionRepository = executionRepository;
        this.workflowRepository = workflowRepository;
        this.workspaceRepository = workspaceRepository;
        this.graphResolver = graphResolver;
        this.llmConnectionRepository = llmConnectionRepository;
    }

    public void start(int port) {
        AgentRoutes agentRoutes = new AgentRoutes(agentRepository);
        WorkflowRoutes workflowRoutes = new WorkflowRoutes(workflowRepository, graphResolver);
        SkillRoutes skillRoutes = new SkillRoutes(skillRepository);
        MCPRoutes mcpRoutes = new MCPRoutes(mcpRepository);
        SessionRoutes sessionRoutes =
                new SessionRoutes(sessionRepository, workflowEngine, executionRepository);
        WorkspaceRoutes workspaceRoutes = new WorkspaceRoutes(workspaceRepository);
        LLMRoutes llmRoutes = new LLMRoutes(llmConnectionRepository);

        app =
                Javalin.create(
                                config -> {
                                    config.routes.before(
                                            ctx -> {
                                                if (isInvalidToken(
                                                        ctx.header(TOKEN_HEADER),
                                                        ctx.queryParam(TOKEN_PARAM))) {
                                                    log.warn(
                                                            "Rejected request {} {} due to invalid token",
                                                            ctx.method(),
                                                            ctx.path());
                                                    throw new UnauthorizedResponse();
                                                }
                                            });
                                    // SSE connections are not intercepted by the before-filter
                                    // above; validate the token directly in the SseHandler
                                    // before handing off to SSEManager.
                                    config.routes.sse(
                                            EVENTS_ENDPOINT,
                                            client -> {
                                                if (isInvalidToken(
                                                        client.ctx().header(TOKEN_HEADER),
                                                        client.ctx().queryParam(TOKEN_PARAM))) {
                                                    log.warn(
                                                            "Rejected SSE connection due to invalid token");
                                                    client.ctx().status(401);
                                                    return;
                                                }
                                                sseManager.attach(client);
                                            });
                                    config.routes.get(
                                            "/health",
                                            ctx -> ctx.json(java.util.Map.of("status", "ok")));
                                    agentRoutes.register(config.routes);
                                    workflowRoutes.register(config.routes);
                                    skillRoutes.register(config.routes);
                                    mcpRoutes.register(config.routes);
                                    sessionRoutes.register(config.routes);
                                    workspaceRoutes.register(config.routes);
                                    llmRoutes.register(config.routes);
                                })
                        .start(port);
        log.info("Started Loom engine on port {}", port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    /** Returns true when neither the header nor the query-param matches the per-launch token. */
    private boolean isInvalidToken(String fromHeader, String fromQuery) {
        String provided = (fromHeader != null && !fromHeader.isBlank()) ? fromHeader : fromQuery;
        return provided == null || !expectedToken.equals(provided.strip());
    }
}
