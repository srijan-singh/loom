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

import io.javalin.Javalin;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.storage.DatabaseManager;
import com.loom.storage.TestFixtures;
import com.loom.storage.repository.LLMConnectionRepository;
import com.loom.transport.routes.LLMRoutes;
import org.junit.jupiter.api.*;

@DisplayName("LLMRoutes BVT: CRUD + test endpoint")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LLMRoutesBVT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static int port;
    private static Javalin app;
    private static Path dbFile;
    private static HttpClient http;

    // id of a connection created in the POST test — shared across ordered tests
    private static String createdId;

    @BeforeAll
    static void startServer() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        dbFile = Files.createTempFile("loom-llm-routes-bvt-", ".db");
        dbFile.toFile().deleteOnExit();

        DatabaseManager db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);
        LLMConnectionRepository repo = new LLMConnectionRepository(db);
        LLMRoutes llmRoutes = new LLMRoutes(repo);

        app = Javalin.create(config -> llmRoutes.register(config.routes)).start(port);
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (app != null) app.stop();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /llm/connections → 201, apiKey field is masked")
    void postCreatesConnectionWithMaskedKey() throws Exception {
        String body =
                """
                {"name":"Test LLM","baseUrl":"https://api.example.com","model":"gpt-4o","apiKey":"sk-secretkey123","default":false}
                """;
        HttpResponse<String> resp = post("/llm/connections", body);
        assertEquals(201, resp.statusCode());

        JsonNode json = MAPPER.readTree(resp.body());
        createdId = json.get("id").asText();
        assertNotNull(createdId);
        assertFalse(createdId.isBlank());

        String maskedKey = json.get("apiKey").asText();
        assertNotEquals("sk-secretkey123", maskedKey, "apiKey must be masked");
        assertTrue(maskedKey.endsWith("****"), "masked key must end with ****");
        assertTrue(maskedKey.startsWith("sk-"), "masked key must retain first 3 chars");
    }

    @Test
    @Order(2)
    @DisplayName("GET /llm/connections/{id} → 200, single connection with masked apiKey")
    void getByIdReturnsMaskedConnection() throws Exception {
        HttpResponse<String> resp = get("/llm/connections/" + createdId);
        assertEquals(200, resp.statusCode());

        JsonNode json = MAPPER.readTree(resp.body());
        assertEquals(createdId, json.get("id").asText());
        String maskedKey = json.get("apiKey").asText();
        assertNotEquals("sk-secretkey123", maskedKey, "apiKey must be masked in GET by id");
        assertTrue(maskedKey.endsWith("****"));
    }

    @Test
    @Order(3)
    @DisplayName(
            "GET /llm/connections → 200 array containing the created connection with masked key")
    void listContainsCreatedConnection() throws Exception {
        HttpResponse<String> resp = get("/llm/connections");
        assertEquals(200, resp.statusCode());

        JsonNode arr = MAPPER.readTree(resp.body());
        assertTrue(arr.isArray());

        boolean found = false;
        for (JsonNode node : arr) {
            if (createdId.equals(node.get("id").asText())) {
                found = true;
                String maskedKey = node.get("apiKey").asText();
                assertNotEquals("sk-secretkey123", maskedKey);
                assertTrue(maskedKey.endsWith("****"));
                break;
            }
        }
        assertTrue(found, "created connection must appear in findAll");
    }

    @Test
    @Order(4)
    @DisplayName("PATCH /llm/connections/{id}/default → 200, isDefault is true")
    void setDefaultReturnsUpdatedView() throws Exception {
        HttpResponse<String> resp = patch("/llm/connections/" + createdId + "/default");
        assertEquals(200, resp.statusCode());

        JsonNode json = MAPPER.readTree(resp.body());
        assertTrue(
                json.get("isDefault").asBoolean(), "isDefault must be true after PATCH /default");
    }

    @Test
    @Order(5)
    @DisplayName("DELETE /llm/connections/{id} → 204; subsequent GET returns 404")
    void deleteRemovesConnection() throws Exception {
        HttpResponse<String> del = delete("/llm/connections/" + createdId);
        assertEquals(204, del.statusCode());

        HttpResponse<String> get = get("/llm/connections/" + createdId);
        assertEquals(404, get.statusCode());
    }

    @Test
    @Order(6)
    @DisplayName(
            "POST /llm/connections/test with unreachable baseUrl → 200 {ok: false, error: non-blank}")
    void testEndpointUnreachableBaseUrl() throws Exception {
        String body =
                """
                {"name":"test","baseUrl":"http://localhost:1","apiKey":"sk-test","model":"gpt-4o"}
                """;
        HttpResponse<String> resp = post("/llm/connections/test", body);
        assertEquals(200, resp.statusCode());

        JsonNode json = MAPPER.readTree(resp.body());
        assertFalse(json.get("ok").asBoolean(), "ok must be false for unreachable endpoint");
        String error = json.path("error").asText("");
        assertFalse(error.isBlank(), "error message must be non-blank");
    }
}
