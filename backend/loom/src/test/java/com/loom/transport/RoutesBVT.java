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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.SessionStatus;
import com.loom.engine.AgentRuntime;
import com.loom.llm.MockLLMProvider;
import com.loom.mcp.MCPClient;
import com.loom.storage.DatabaseManager;
import com.loom.storage.TestFixtures;
import com.loom.storage.repository.*;
import com.loom.transport.util.TestSSEClient;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end BVT covering:
 * <ul>
 *   <li>CRUD routes for Skills, Agents, MCPs, Sessions</li>
 *   <li>Full execution loop via POST /sessions/{id}/run using MockLLMProvider</li>
 *   <li>SSE event ordering: SESSION_STARTED → AGENT_TOKEN(s) → AGENT_REPORT_WRITTEN → SESSION_COMPLETED</li>
 * </ul>
 */
@DisplayName("Routes + AgentRuntime BVT")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoutesBVT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static int port;
    private static LocalServer server;
    private static SSEManager sseManager;
    private static Path dbFile;
    private static HttpClient http;

    // repositories exposed for direct verification
    private static AgentExecutionRepository execRepo;
    private static WorkspaceKnowledgeRepository knowledgeRepo;
    private static SessionRepository sessionRepo;

    @BeforeAll
    static void startServer() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        dbFile = Files.createTempFile("loom-routes-bvt-", ".db");
        dbFile.toFile().deleteOnExit();

        DatabaseManager db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);

        SkillRepository              skillRepo     = new SkillRepository(db);
        MCPConnectionRepository      mcpRepo       = new MCPConnectionRepository(db);
        AgentRepository              agentRepo     = new AgentRepository(db);
        WorkflowRepository           workflowRepo  = new WorkflowRepository(db);
        WorkspaceRepository          workspaceRepo = new WorkspaceRepository(db);
        sessionRepo   = new SessionRepository(db);
        execRepo      = new AgentExecutionRepository(db);
        knowledgeRepo = new WorkspaceKnowledgeRepository(db);

        sseManager = new SSEManager();
        MCPClient mcpClient = new MCPClient();
        AgentRuntime agentRuntime = new AgentRuntime(
                new MockLLMProvider(), mcpClient, sseManager,
                skillRepo, knowledgeRepo, execRepo,
                sessionRepo, workflowRepo, agentRepo);

        server = new LocalServer(sseManager, agentRuntime,
                agentRepo, skillRepo, mcpRepo,
                sessionRepo, workflowRepo, workspaceRepo);
        server.start(port);

        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (server != null) server.stop();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ── 1. Skill CRUD ──────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /skills returns list including fixture skill")
    void skillGetAll() throws Exception {
        HttpResponse<String> res = get("/skills");
        assertEquals(200, res.statusCode());
        JsonNode arr = MAPPER.readTree(res.body());
        assertTrue(arr.isArray() && arr.size() >= 1);
    }

    @Test
    @Order(2)
    @DisplayName("POST /skills creates skill; GET /skills/{id} returns it; DELETE removes it")
    void skillCrud() throws Exception {
        // create
        HttpResponse<String> create = post("/skills",
                "{\"name\":\"Test Skill\",\"description\":\"desc\",\"content\":\"# Test\"}");
        assertEquals(201, create.statusCode());
        JsonNode created = MAPPER.readTree(create.body());
        String id = created.path("id").asText();
        assertFalse(id.isBlank(), "id must be present");
        assertEquals("Test Skill", created.path("name").asText());

        // GET by id
        HttpResponse<String> found = get("/skills/" + id);
        assertEquals(200, found.statusCode());
        assertEquals("Test Skill", MAPPER.readTree(found.body()).path("name").asText());

        // PUT update
        HttpResponse<String> updated = put("/skills/" + id,
                "{\"name\":\"Updated Skill\",\"description\":\"new desc\"}");
        assertEquals(200, updated.statusCode());
        assertEquals("Updated Skill", MAPPER.readTree(updated.body()).path("name").asText());

        // DELETE
        HttpResponse<String> del = delete("/skills/" + id);
        assertEquals(204, del.statusCode());

        // 404 after delete
        assertEquals(404, get("/skills/" + id).statusCode());
    }

    @Test
    @Order(3)
    @DisplayName("POST /skills with missing name returns 400")
    void skillCreateValidation() throws Exception {
        HttpResponse<String> res = post("/skills", "{\"description\":\"no name\"}");
        assertEquals(400, res.statusCode());
        assertEquals("name is required", MAPPER.readTree(res.body()).path("error").asText());
    }

    // ── 2. Agent CRUD ──────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /agents returns list including fixture agent")
    void agentGetAll() throws Exception {
        HttpResponse<String> res = get("/agents");
        assertEquals(200, res.statusCode());
        JsonNode arr = MAPPER.readTree(res.body());
        assertTrue(arr.isArray() && arr.size() >= 1);
    }

    @Test
    @Order(5)
    @DisplayName("POST /agents creates agent; GET /agents/{id} returns it; DELETE removes it")
    void agentCrud() throws Exception {
        // create
        HttpResponse<String> create = post("/agents",
                "{\"name\":\"My Agent\",\"roleDescription\":\"A helpful agent\"}");
        assertEquals(201, create.statusCode());
        JsonNode created = MAPPER.readTree(create.body());
        String id = created.path("id").asText();
        assertFalse(id.isBlank());
        assertEquals("My Agent", created.path("name").asText());

        // GET by id
        assertEquals(200, get("/agents/" + id).statusCode());

        // PUT update
        HttpResponse<String> updated = put("/agents/" + id,
                "{\"name\":\"Updated Agent\",\"roleDescription\":\"Updated role\"}");
        assertEquals(200, updated.statusCode());
        assertEquals("Updated Agent", MAPPER.readTree(updated.body()).path("name").asText());

        // DELETE
        assertEquals(204, delete("/agents/" + id).statusCode());

        // 404 after delete
        assertEquals(404, get("/agents/" + id).statusCode());
    }

    @Test
    @Order(6)
    @DisplayName("GET /agents/{id} with unknown id returns 404")
    void agentNotFound() throws Exception {
        HttpResponse<String> res = get("/agents/does-not-exist");
        assertEquals(404, res.statusCode());
        assertEquals("not_found", MAPPER.readTree(res.body()).path("error").asText());
    }

    // ── 3. MCP routes ──────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("GET /mcps returns list; POST /mcps creates; DELETE removes")
    void mcpCrud() throws Exception {
        HttpResponse<String> list = get("/mcps");
        assertEquals(200, list.statusCode());

        HttpResponse<String> create = post("/mcps",
                "{\"name\":\"Test MCP\",\"type\":\"stdio\"}");
        assertEquals(201, create.statusCode());
        String id = MAPPER.readTree(create.body()).path("id").asText();
        assertFalse(id.isBlank());

        assertEquals(204, delete("/mcps/" + id).statusCode());
        assertEquals(404, delete("/mcps/" + id).statusCode());
    }

    // ── 4. Session create + run (full execution loop) ──────────────────────────

    @Test
    @Order(8)
    @DisplayName("POST /sessions creates session with CREATED status")
    void sessionCreate() throws Exception {
        HttpResponse<String> res = post("/sessions",
                "{\"workspaceId\":\"ws-research\",\"workflowDefinitionId\":\"wf-research-pipeline\"}");
        assertEquals(201, res.statusCode());
        JsonNode body = MAPPER.readTree(res.body());
        assertFalse(body.path("id").asText().isBlank());
        assertEquals("CREATED", body.path("status").asText());
    }

    @Test
    @Order(9)
    @DisplayName("POST /sessions/{id}/run executes and produces COMPLETED session, execution row, and knowledge row")
    void sessionRun() throws Exception {
        // 1. Subscribe SSE before triggering run
        TestSSEClient sse = new TestSSEClient(20, port);
        sse.connect();
        assertTrue(sse.isConnected());
        Thread.sleep(200);

        // 2. Create session pointing at the fixture workflow
        HttpResponse<String> createRes = post("/sessions",
                "{\"workspaceId\":\"ws-research\",\"workflowDefinitionId\":\"wf-research-pipeline\"}");
        assertEquals(201, createRes.statusCode());
        String sessionId = MAPPER.readTree(createRes.body()).path("id").asText();

        // 3. Run
        HttpResponse<String> runRes = post("/sessions/" + sessionId + "/run",
                "\"Research the latest AI trends\"");
        assertEquals(202, runRes.statusCode());

        // 4. Wait for SESSION_COMPLETED event (up to 10 s)
        List<String> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        boolean completed = false;
        while (System.currentTimeMillis() < deadline) {
            String msg = sse.waitForMessage(1);
            if (msg == null) break;
            events.add(msg);
            JsonNode node = MAPPER.readTree(msg);
            if ("SESSION_COMPLETED".equals(node.path("eventType").asText())) {
                completed = true;
                break;
            }
        }
        sse.disconnect();

        assertTrue(completed, "SESSION_COMPLETED event must be received. Events seen: " + events);

        // 5. Collect event types seen
        List<String> eventTypes = new ArrayList<>();
        for (String e : events) {
            eventTypes.add(MAPPER.readTree(e).path("eventType").asText());
        }
        assertTrue(eventTypes.contains("SESSION_STARTED"),        "SESSION_STARTED missing");
        assertTrue(eventTypes.contains("AGENT_TOKEN"),            "AGENT_TOKEN missing");
        assertTrue(eventTypes.contains("AGENT_REPORT_WRITTEN"),   "AGENT_REPORT_WRITTEN missing");
        assertTrue(eventTypes.contains("SESSION_COMPLETED"),      "SESSION_COMPLETED missing");

        // Ordering: SESSION_STARTED first
        assertEquals("SESSION_STARTED", eventTypes.get(0),
                "SESSION_STARTED must be the first event");

        // 6. Verify session status in DB
        Thread.sleep(200); // let async thread finish writing
        var session = sessionRepo.findById(sessionId);
        assertTrue(session.isPresent());
        assertEquals(SessionStatus.COMPLETED, session.get().getStatus(),
                "Session must be COMPLETED in DB");

        // 7. Verify at least one agent_execution row with COMPLETED status
        boolean hasCompletedExec = execRepo.findAll().stream()
                .anyMatch(e -> sessionId.equals(e.getSessionId())
                        && com.loom.domain.AgentExecutionStatus.COMPLETED == e.getStatus());
        assertTrue(hasCompletedExec, "An agent_execution row with COMPLETED status must exist");

        // 8. Verify workspace_knowledge row was created
        boolean hasKnowledge = knowledgeRepo.findAll().stream()
                .anyMatch(k -> "ws-research".equals(k.getWorkspaceId())
                        && k.getContent() != null && !k.getContent().isBlank());
        assertTrue(hasKnowledge, "A workspace_knowledge row must exist for the session's workspace");
    }

    @Test
    @Order(10)
    @DisplayName("POST /sessions with missing workspaceId returns 400")
    void sessionCreateValidation() throws Exception {
        HttpResponse<String> res = post("/sessions",
                "{\"workflowDefinitionId\":\"wf-research-pipeline\"}");
        assertEquals(400, res.statusCode());
        assertEquals("workspaceId is required", MAPPER.readTree(res.body()).path("error").asText());
    }
}
