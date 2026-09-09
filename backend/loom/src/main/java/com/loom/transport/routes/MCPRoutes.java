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

import com.loom.domain.MCPConnection;
import com.loom.domain.MCPStatus;
import com.loom.storage.repository.MCPConnectionRepository;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Optional;

public class MCPRoutes {

    private final MCPConnectionRepository mcpRepository;

    public MCPRoutes(MCPConnectionRepository mcpRepository) {
        this.mcpRepository = mcpRepository;
    }

    public void register(JavalinDefaultRoutingApi router) {

        router.get("/mcps", ctx -> ctx.json(mcpRepository.findAll()));

        router.post("/mcps", ctx -> {
            MCPConnection body = ctx.bodyAsClass(MCPConnection.class);
            if (body.getName() == null || body.getName().isBlank()) {
                ctx.status(400).json(RouteHelper.error("name is required"));
                return;
            }
            if (body.getType() == null || body.getType().isBlank()) {
                ctx.status(400).json(RouteHelper.error("type is required"));
                return;
            }
            MCPConnection created = new MCPConnection();
            created.setName(body.getName());
            created.setType(body.getType());
            created.setConfig(body.getConfig());
            created.setStatus(body.getStatus() != null ? body.getStatus() : MCPStatus.DISCONNECTED);
            created.setCreatedAt(System.currentTimeMillis());
            mcpRepository.save(created);
            ctx.status(201).json(created);
        });

        router.delete("/mcps/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Optional<MCPConnection> found = mcpRepository.findById(id);
            if (found.isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
                return;
            }
            mcpRepository.delete(id);
            ctx.status(204);
        });
    }
}
