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

import com.loom.transport.routes.*;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalServer {

    private static final String EVENTS_ENDPOINT = "/events";

    private final SSEManager sseManager;
    private Javalin app;

    public LocalServer(SSEManager sseManager) {
        this.sseManager = sseManager;
    }

    public void start(int port) {
        AgentRoutes agentRoutes = new AgentRoutes();
        WorkflowRoutes workflowRoutes = new WorkflowRoutes();
        SkillRoutes skillRoutes = new SkillRoutes();
        MCPRoutes mcpRoutes = new MCPRoutes();
        SessionRoutes sessionRoutes = new SessionRoutes();
        WorkspaceRoutes workspaceRoutes = new WorkspaceRoutes();

        app = Javalin.create(config -> {
            config.routes.sse(EVENTS_ENDPOINT, sseManager::attach);
            agentRoutes.register(config.routes);
            workflowRoutes.register(config.routes);
            skillRoutes.register(config.routes);
            mcpRoutes.register(config.routes);
            sessionRoutes.register(config.routes);
            workspaceRoutes.register(config.routes);
        }).start(port);
        log.info("Started Loom engine on port {}", port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }
}
