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

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

@Tag("integration")
@DisplayName("SLM Integration BVT: Ollama + loom-engine.jar live smoke test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SLMIntegrationBVT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OLLAMA_BASE_URL = "http://localhost:11434/v1";
    private static final String MODEL = "qwen2.5-coder:1.5b";

    private static String BASE_URL;
    private static String TOKEN;
    private static HttpClient http;
    private static String createdConnectionId;

    @BeforeAll
    static void setup() {
        String baseUrl = System.getProperty("loom.integration.baseUrl", "");
        String token = System.getProperty("loom.integration.token", "");
        Assumptions.assumeTrue(
                !baseUrl.isBlank(),
                "loom.integration.baseUrl not set — skipping integration tests");
        Assumptions.assumeTrue(
                !token.isBlank(), "loom.integration.token not set — skipping integration tests");
        BASE_URL = baseUrl;
        TOKEN = token;
        http = HttpClient.newHttpClient();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .header("X-Loom-Token", TOKEN)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("X-Loom-Token", TOKEN)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .header("X-Loom-Token", TOKEN)
                        .method("PATCH", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /llm/connections with Ollama qwen2.5-coder:1.5b → 201, non-blank id")
    void registerOllamaConnection() throws Exception {
        String body =
                """
                {"name":"qwen-ci","baseUrl":"%s","apiKey":"ollama","model":"%s","default":false}
                """
                        .formatted(OLLAMA_BASE_URL, MODEL);
        HttpResponse<String> resp = post("/llm/connections", body);
        assertEquals(201, resp.statusCode());

        JsonNode json = MAPPER.readTree(resp.body());
        createdConnectionId = json.get("id").asText();
        assertNotNull(createdConnectionId, "id must be present in response");
        assertFalse(createdConnectionId.isBlank(), "id must be non-blank");
    }

    @Test
    @Order(2)
    @DisplayName("PATCH /llm/connections/{id}/default → 200, isDefault is true")
    void setOllamaConnectionAsDefault() throws Exception {
        HttpResponse<String> resp = patch("/llm/connections/" + createdConnectionId + "/default");
        assertEquals(200, resp.statusCode());

        JsonNode json = MAPPER.readTree(resp.body());
        assertTrue(
                json.get("isDefault").asBoolean(), "isDefault must be true after PATCH /default");
    }

    @Test
    @Order(3)
    @DisplayName("POST /llm/connections/test with Ollama endpoint → 200, ok is true")
    void testOllamaConnectivity() throws Exception {
        String body =
                """
                {"name":"qwen-ci","baseUrl":"%s","apiKey":"ollama","model":"%s"}
                """
                        .formatted(OLLAMA_BASE_URL, MODEL);
        HttpResponse<String> resp = post("/llm/connections/test", body);
        assertEquals(200, resp.statusCode());

        JsonNode json = MAPPER.readTree(resp.body());
        assertTrue(json.get("ok").asBoolean(), "ok must be true — Ollama must be reachable");
    }

    @Test
    @Order(4)
    @DisplayName("GET /llm/connections → contains qwen-ci with isDefault true")
    void listConnectionsShowsOllamaAsDefault() throws Exception {
        HttpResponse<String> resp = get("/llm/connections");
        assertEquals(200, resp.statusCode());

        JsonNode arr = MAPPER.readTree(resp.body());
        assertTrue(arr.isArray(), "response must be an array");

        boolean found = false;
        for (JsonNode node : arr) {
            if ("qwen-ci".equals(node.path("name").asText())) {
                found = true;
                assertTrue(
                        node.get("isDefault").asBoolean(),
                        "qwen-ci must be the default connection");
                break;
            }
        }
        assertTrue(found, "qwen-ci must appear in the connections list");
    }

    @Test
    @Order(5)
    @DisplayName("GET /events with valid token → 200, Content-Type is text/event-stream")
    void sseEndpointRespondsWithEventStream() throws Exception {
        // Use InputStream so send() returns as soon as the response headers arrive,
        // without waiting for the SSE stream to close (it never does).
        // Assert status and headers first, then close — closing before reading headers
        // can abort the connection before HttpClient buffers the response metadata.
        HttpResponse<InputStream> resp =
                http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/events?token=" + TOKEN))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofInputStream());

        int status = resp.statusCode();
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        resp.body().close(); // discard stream only after headers are captured

        assertEquals(200, status);
        assertTrue(
                contentType.contains("text/event-stream"),
                "Content-Type must be text/event-stream, got: " + contentType);
    }
}
