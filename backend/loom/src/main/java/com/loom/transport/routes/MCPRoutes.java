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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MCPRoutes {

    private final MCPConnectionRepository mcpRepository;

    public MCPRoutes(MCPConnectionRepository mcpRepository) {
        this.mcpRepository = mcpRepository;
    }

    public void register(JavalinDefaultRoutingApi router) {

        router.get("/mcps", ctx -> {
            List<MCPConnectionView> views = mcpRepository.findAll().stream()
                    .map(MCPConnectionView::of)
                    .collect(Collectors.toList());
            ctx.json(views);
        });

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
            ctx.status(201).json(MCPConnectionView.of(created));
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

    /** Public projection of {@link MCPConnection} that omits the sensitive {@code config} field. */
    public static final class MCPConnectionView {
        public final String id;
        public final String name;
        public final String type;
        public final MCPStatus status;
        public final long createdAt;

        private MCPConnectionView(MCPConnection c) {
            this.id        = c.getId();
            this.name      = c.getName();
            this.type      = c.getType();
            this.status    = c.getStatus();
            this.createdAt = c.getCreatedAt();
        }

        public static MCPConnectionView of(MCPConnection c) {
            return new MCPConnectionView(c);
        }
    }
}
