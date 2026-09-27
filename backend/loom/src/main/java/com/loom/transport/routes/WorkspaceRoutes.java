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

import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Optional;

import com.loom.domain.Workspace;
import com.loom.storage.repository.WorkspaceRepository;

public class WorkspaceRoutes {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceRoutes(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public void register(JavalinDefaultRoutingApi router) {

        // GET /workspaces — list all
        router.get("/workspaces", ctx -> ctx.json(workspaceRepository.findAll()));

        // POST /workspaces — create and return 201
        router.post(
                "/workspaces",
                ctx -> {
                    Workspace body = ctx.bodyAsClass(Workspace.class);
                    if (body.getName() == null || body.getName().isBlank()) {
                        ctx.status(400).json(RouteHelper.error("name is required"));
                        return;
                    }
                    body.setCreatedAt(System.currentTimeMillis());
                    workspaceRepository.save(body);
                    ctx.status(201).json(body);
                });

        // GET /workspaces/{id} — find by id or 404
        router.get(
                "/workspaces/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    Optional<Workspace> found = workspaceRepository.findById(id);
                    if (found.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                    } else {
                        ctx.json(found.get());
                    }
                });
    }
}
