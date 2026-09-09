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

import com.loom.domain.Skill;
import com.loom.storage.repository.SkillRepository;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.Optional;

public class SkillRoutes {

    private final SkillRepository skillRepository;

    public SkillRoutes(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public void register(JavalinDefaultRoutingApi router) {

        router.get("/skills", ctx -> ctx.json(skillRepository.findAll()));

        router.get("/skills/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Optional<Skill> found = skillRepository.findById(id);
            if (found.isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
            } else {
                ctx.json(found.get());
            }
        });

        router.post("/skills", ctx -> {
            Skill body = ctx.bodyAsClass(Skill.class);
            if (body.getName() == null || body.getName().isBlank()) {
                ctx.status(400).json(RouteHelper.error("name is required"));
                return;
            }
            long now = System.currentTimeMillis();
            Skill created = new Skill();
            created.setName(body.getName());
            created.setDescription(body.getDescription());
            created.setContent(body.getContent());
            created.setTags(body.getTags());
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            skillRepository.save(created);
            ctx.status(201).json(created);
        });

        router.put("/skills/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (skillRepository.findById(id).isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
                return;
            }
            Skill body = ctx.bodyAsClass(Skill.class);
            if (body.getName() == null || body.getName().isBlank()) {
                ctx.status(400).json(RouteHelper.error("name is required"));
                return;
            }
            body.setId(id);
            body.setUpdatedAt(System.currentTimeMillis());
            skillRepository.save(body);
            ctx.json(skillRepository.findById(id).get());
        });

        router.delete("/skills/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (skillRepository.findById(id).isEmpty()) {
                ctx.status(404).json(RouteHelper.notFound());
                return;
            }
            skillRepository.delete(id);
            ctx.status(204);
        });
    }
}
