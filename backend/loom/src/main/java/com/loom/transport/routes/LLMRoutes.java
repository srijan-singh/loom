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
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.loom.llm.LLMConnection;
import com.loom.llm.LLMRequest;
import com.loom.llm.LLMResponse;
import com.loom.llm.OpenAICompatibleProvider;
import com.loom.llm.ProviderConfig;
import com.loom.storage.repository.LLMConnectionRepository;

/**
 * HTTP transport adapter for LLM connection CRUD and connection testing.
 *
 * <p>Route table:
 *
 * <ul>
 *   <li>POST /llm/connections — create a new connection (201)
 *   <li>GET /llm/connections — list all connections (200)
 *   <li>GET /llm/connections/{id} — get one connection (200/404)
 *   <li>DELETE /llm/connections/{id} — delete a connection (204/404)
 *   <li>PATCH /llm/connections/{id}/default — set as default (200/404)
 *   <li>POST /llm/connections/test — test connectivity (always 200)
 * </ul>
 *
 * <p>API key masking: GET responses never return the raw key — first 3 chars + {@code "****"}.
 */
@Slf4j
public class LLMRoutes {

    private final LLMConnectionRepository llmConnectionRepo;

    public LLMRoutes(LLMConnectionRepository llmConnectionRepo) {
        this.llmConnectionRepo = llmConnectionRepo;
    }

    public void register(JavalinDefaultRoutingApi router) {

        // POST /llm/connections — create
        router.post(
                "/llm/connections",
                ctx -> {
                    LLMConnection body = ctx.bodyAsClass(LLMConnection.class);
                    if (body.getName() == null || body.getName().isBlank()) {
                        ctx.status(400).json(RouteHelper.error("name is required"));
                        return;
                    }
                    if (body.getBaseUrl() == null || body.getBaseUrl().isBlank()) {
                        ctx.status(400).json(RouteHelper.error("baseUrl is required"));
                        return;
                    }
                    if (body.getModel() == null || body.getModel().isBlank()) {
                        ctx.status(400).json(RouteHelper.error("model is required"));
                        return;
                    }
                    if (body.getApiKey() == null || body.getApiKey().isBlank()) {
                        ctx.status(400).json(RouteHelper.error("apiKey is required"));
                        return;
                    }
                    LLMConnection created = new LLMConnection();
                    created.setName(body.getName());
                    created.setBaseUrl(body.getBaseUrl());
                    created.setModel(body.getModel());
                    created.setApiKey(body.getApiKey());
                    created.setDefault(false); // always persist false; setDefault clears others
                    created.setCreatedAt(System.currentTimeMillis());
                    llmConnectionRepo.save(created);
                    if (body.isDefault()) {
                        llmConnectionRepo.setDefault(created.getId());
                        created.setDefault(true);
                    }
                    ctx.status(201).json(LLMConnectionView.of(created));
                });

        // GET /llm/connections — list all
        router.get(
                "/llm/connections",
                ctx -> {
                    List<LLMConnectionView> views =
                            llmConnectionRepo.findAll().stream()
                                    .map(LLMConnectionView::of)
                                    .collect(Collectors.toList());
                    ctx.json(views);
                });

        // GET /llm/connections/{id} — get one
        router.get(
                "/llm/connections/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    Optional<LLMConnection> found = llmConnectionRepo.findById(id);
                    if (found.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                        return;
                    }
                    ctx.json(LLMConnectionView.of(found.get()));
                });

        // DELETE /llm/connections/{id}
        router.delete(
                "/llm/connections/{id}",
                ctx -> {
                    String id = ctx.pathParam("id");
                    if (llmConnectionRepo.findById(id).isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                        return;
                    }
                    llmConnectionRepo.delete(id);
                    ctx.status(204);
                });

        // PATCH /llm/connections/{id}/default — set as default
        router.patch(
                "/llm/connections/{id}/default",
                ctx -> {
                    String id = ctx.pathParam("id");
                    Optional<LLMConnection> found = llmConnectionRepo.findById(id);
                    if (found.isEmpty()) {
                        ctx.status(404).json(RouteHelper.notFound());
                        return;
                    }
                    llmConnectionRepo.setDefault(id);
                    // Re-fetch to return the updated state
                    ctx.json(LLMConnectionView.of(llmConnectionRepo.findById(id).get()));
                });

        // POST /llm/connections/test — test connectivity (always 200, errors in body)
        router.post(
                "/llm/connections/test",
                ctx -> {
                    LLMConnection body = ctx.bodyAsClass(LLMConnection.class);
                    String baseUrl = body.getBaseUrl();
                    String apiKey = body.getApiKey();
                    String model = body.getModel();

                    if (baseUrl == null
                            || baseUrl.isBlank()
                            || apiKey == null
                            || apiKey.isBlank()
                            || model == null
                            || model.isBlank()) {
                        ctx.json(
                                Map.of(
                                        "ok",
                                        false,
                                        "error",
                                        "baseUrl, apiKey and model are required"));
                        return;
                    }

                    OkHttpClient httpClient =
                            new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(15)).build();

                    // Step 1: probe /models endpoint
                    try {
                        Request probeRequest =
                                new Request.Builder()
                                        .url(baseUrl + "/models")
                                        .addHeader("Authorization", "Bearer " + apiKey)
                                        .get()
                                        .build();
                        try (Response probeResponse = httpClient.newCall(probeRequest).execute()) {
                            int code = probeResponse.code();
                            // Only a 200 means the /models endpoint confirmed reachability
                            if (code == 200) {
                                ctx.json(Map.of("ok", true));
                                return;
                            }
                            // 404/405/501 and other non-success: fall through to completion probe
                        }
                    } catch (Exception e) {
                        ctx.json(
                                Map.of(
                                        "ok",
                                        false,
                                        "error",
                                        e.getMessage() != null ? e.getMessage() : "unreachable"));
                        return;
                    }

                    // Step 2: fallback — attempt a minimal completion
                    ProviderConfig config = new ProviderConfig(baseUrl, apiKey, model);
                    OpenAICompatibleProvider provider = new OpenAICompatibleProvider(config);
                    LLMRequest probe =
                            LLMRequest.builder().userPrompt("Say hi").maxTokens(5).build();

                    final boolean[] ok = {false};
                    final String[] errorMsg = {null};
                    provider.send(
                            probe,
                            event -> {
                                if (LLMResponse.TOKEN.equals(event.getType())
                                        || LLMResponse.DONE.equals(event.getType())) {
                                    ok[0] = true;
                                } else if (LLMResponse.ERROR.equals(event.getType())) {
                                    errorMsg[0] = event.getContent();
                                }
                            });

                    if (ok[0]) {
                        ctx.json(Map.of("ok", true));
                    } else {
                        ctx.json(
                                Map.of(
                                        "ok",
                                        false,
                                        "error",
                                        errorMsg[0] != null ? errorMsg[0] : "connection failed"));
                    }
                });
    }

    // ── view projection ───────────────────────────────────────────────────────

    /**
     * Public projection of {@link LLMConnection} with the API key masked. Fields are intentionally
     * public and final — this is an immutable JSON view object.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    public static final class LLMConnectionView {
        public final String id;
        public final String name;
        public final String baseUrl;
        public final String model;
        public final String apiKey; // masked
        public final boolean isDefault;
        public final long createdAt;

        private LLMConnectionView(LLMConnection c) {
            this.id = c.getId();
            this.name = c.getName();
            this.baseUrl = c.getBaseUrl();
            this.model = c.getModel();
            this.apiKey = maskKey(c.getApiKey());
            this.isDefault = c.isDefault();
            this.createdAt = c.getCreatedAt();
        }

        public static LLMConnectionView of(LLMConnection c) {
            return new LLMConnectionView(c);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String maskKey(String key) {
        if (key == null || key.length() < 4) return "****";
        return key.substring(0, 3) + "****";
    }
}
