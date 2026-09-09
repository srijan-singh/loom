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

import com.loom.domain.AgentDefinition;
import com.loom.storage.repository.AgentRepository;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Optional;

public class AgentRoutes {

    private final AgentRepository agentRepository;

    public AgentRoutes(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public void register(JavalinDefaultRoutingApi router) {

        router.get("/agents", ctx -> ctx.json(agentRepository.findAll()));

        router.get("/agents/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Optional<AgentDefinition> found = agentRepository.findById(id);
            if (found.isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
            } else {
                ctx.json(found.get());
            }
        });

        router.post("/agents", ctx -> {
            AgentDefinition body = ctx.bodyAsClass(AgentDefinition.class);
            if (body.getName() == null || body.getName().isBlank()) {
                ctx.status(400).json(RouteHelper.error("name is required"));
                return;
            }
            long now = System.currentTimeMillis();
            AgentDefinition created = new AgentDefinition();
            created.setName(body.getName());
            created.setRoleDescription(body.getRoleDescription());
            created.setSkillId(body.getSkillId());
            created.setAllowedMcpIds(body.getAllowedMcpIds());
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            agentRepository.save(created);
            ctx.status(201).json(created);
        });

        router.put("/agents/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Optional<AgentDefinition> existing = agentRepository.findById(id);
            if (existing.isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
                return;
            }
            AgentDefinition body = ctx.bodyAsClass(AgentDefinition.class);
            if (body.getName() == null || body.getName().isBlank()) {
                ctx.status(400).json(RouteHelper.error("name is required"));
                return;
            }
            body.setId(id);
            body.setCreatedAt(existing.get().getCreatedAt());
            body.setUpdatedAt(System.currentTimeMillis());
            agentRepository.save(body);
            ctx.json(agentRepository.findById(id).get());
        });

        router.delete("/agents/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (agentRepository.findById(id).isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
                return;
            }
            agentRepository.delete(id);
            ctx.status(204);
        });
    }
}
