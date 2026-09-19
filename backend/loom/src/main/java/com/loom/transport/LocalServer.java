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

import com.loom.engine.AgentRuntime;
import com.loom.engine.GraphResolver;
import com.loom.engine.WorkflowEngine;
import com.loom.storage.repository.*;
import com.loom.transport.routes.*;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalServer {

    private static final String EVENTS_ENDPOINT = "/events";

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
    private Javalin app;

    public LocalServer(
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
            GraphResolver graphResolver) {
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
    }

    public void start(int port) {
        AgentRoutes agentRoutes = new AgentRoutes(agentRepository);
        WorkflowRoutes workflowRoutes = new WorkflowRoutes(workflowRepository, graphResolver);
        SkillRoutes skillRoutes = new SkillRoutes(skillRepository);
        MCPRoutes mcpRoutes = new MCPRoutes(mcpRepository);
        SessionRoutes sessionRoutes =
                new SessionRoutes(sessionRepository, workflowEngine, executionRepository);
        WorkspaceRoutes workspaceRoutes = new WorkspaceRoutes();

        app =
                Javalin.create(
                                config -> {
                                    config.routes.sse(EVENTS_ENDPOINT, sseManager::attach);
                                    agentRoutes.register(config.routes);
                                    workflowRoutes.register(config.routes);
                                    skillRoutes.register(config.routes);
                                    mcpRoutes.register(config.routes);
                                    sessionRoutes.register(config.routes);
                                    workspaceRoutes.register(config.routes);
                                })
                        .start(port);
        log.info("Started Loom engine on port {}", port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }
}
