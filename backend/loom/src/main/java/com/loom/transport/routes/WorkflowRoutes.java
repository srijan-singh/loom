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

import com.loom.domain.WorkflowDefinition;
import com.loom.engine.GraphResolver;
import com.loom.engine.InvalidWorkflowException;
import com.loom.storage.repository.WorkflowRepository;

public class WorkflowRoutes {

    private final WorkflowRepository workflowRepository;
    private final GraphResolver graphResolver;

    public WorkflowRoutes(WorkflowRepository workflowRepository, GraphResolver graphResolver) {
        this.workflowRepository = workflowRepository;
        this.graphResolver = graphResolver;
    }

    public void register(JavalinDefaultRoutingApi router) {

        // GET /workflows — list all
        router.get("/workflows", ctx -> ctx.json(workflowRepository.findAll()));

        // POST /workflows — validate graph, save, return 201
        router.post(
                "/workflows",
                ctx -> {
                    WorkflowDefinition body = ctx.bodyAsClass(WorkflowDefinition.class);
                    if (body.getName() == null || body.getName().isBlank()) {
                        ctx.status(400).json(RouteHelper.error("name is required"));
                        return;
                    }
                    if (body.getType() == null) {
                        ctx.status(400).json(RouteHelper.error("type is required"));
                        return;
                    }
                    try {
                        graphResolver.resolve(body);
                    } catch (InvalidWorkflowException e) {
                        ctx.status(400).json(RouteHelper.error(e.getMessage()));
                        return;
                    }
                    long now = System.currentTimeMillis();
                    body.setCreatedAt(now);
                    body.setUpdatedAt(now);
                    workflowRepository.save(body);
                    ctx.status(201).json(body);
                });

        // GET /workflows/{id} — find by id or 404
        router.get(
                "/workflows/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    Optional<WorkflowDefinition> found = workflowRepository.findById(id);
                    if (found.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                    } else {
                        ctx.json(found.get());
                    }
                });

        // PUT /workflows/{id} — validate graph, merge, save, return 200
        router.put(
                "/workflows/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    Optional<WorkflowDefinition> existingOpt = workflowRepository.findById(id);
                    if (existingOpt.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                        return;
                    }
                    WorkflowDefinition existing = existingOpt.get();
                    WorkflowDefinition body = ctx.bodyAsClass(WorkflowDefinition.class);
                    // merge non-null fields
                    if (body.getName() != null && !body.getName().isBlank()) {
                        existing.setName(body.getName());
                    }
                    if (body.getType() != null) {
                        existing.setType(body.getType());
                    }
                    if (body.getNodes() != null) {
                        existing.setNodes(body.getNodes());
                    }
                    if (body.getEdges() != null) {
                        existing.setEdges(body.getEdges());
                    }
                    if (body.getCreatedBy() != null) {
                        existing.setCreatedBy(body.getCreatedBy());
                    }
                    existing.setUpdatedAt(System.currentTimeMillis());
                    try {
                        graphResolver.resolve(existing);
                    } catch (InvalidWorkflowException e) {
                        ctx.status(400).json(RouteHelper.error(e.getMessage()));
                        return;
                    }
                    workflowRepository.save(existing);
                    ctx.status(200).json(existing);
                });

        // DELETE /workflows/{id} — delete or 404
        router.delete(
                "/workflows/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    if (workflowRepository.findById(id).isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                        return;
                    }
                    workflowRepository.delete(id);
                    ctx.status(204);
                });
    }
}
