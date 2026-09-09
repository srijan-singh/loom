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
import com.loom.engine.AgentRuntime;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.llm.MockLLMProvider;
import com.loom.mcp.MCPClient;
import com.loom.storage.DatabaseManager;
import com.loom.storage.repository.*;
import com.loom.transport.util.TestSSEClient;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Build Verification Test — full end-to-end SSE smoke test.
 *
 * Verifies: server starts → SSE client connects → broadcast delivers a
 * well-formed WorkflowEvent JSON → server shuts down cleanly.
 */
class LocalServerBVT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static int port;
    private static LocalServer server;
    private static SSEManager sseManager;
    private static Path dbFile;

    @BeforeAll
    static void startServer() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        dbFile = Files.createTempFile("loom-bvt-server-", ".db");
        dbFile.toFile().deleteOnExit();

        DatabaseManager db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        SkillRepository              skillRepo     = new SkillRepository(db);
        MCPConnectionRepository      mcpRepo       = new MCPConnectionRepository(db);
        AgentRepository              agentRepo     = new AgentRepository(db);
        WorkflowRepository           workflowRepo  = new WorkflowRepository(db);
        WorkspaceRepository          workspaceRepo = new WorkspaceRepository(db);
        SessionRepository            sessionRepo   = new SessionRepository(db);
        AgentExecutionRepository     execRepo      = new AgentExecutionRepository(db);
        WorkspaceKnowledgeRepository knowledgeRepo = new WorkspaceKnowledgeRepository(db);

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
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (server != null) server.stop();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    @DisplayName("SSE end-to-end: client connects, broadcast delivers WorkflowEvent JSON")
    void sseEndToEnd() throws Exception {
        TestSSEClient client = new TestSSEClient(1, port);
        try {
            // 1 — connect
            client.connect();
            assertTrue(client.isConnected(), "SSE client must connect successfully");
            Thread.sleep(200); // let Javalin register the SseClient

            // 2 — broadcast a WorkflowEvent
            WorkflowEvent event = WorkflowEvent.builder()
                    .eventType(EventType.SESSION_STARTED)
                    .sessionId("bvt-session-1")
                    .data(MAPPER.valueToTree(Map.of("message", "hello")))
                    .build();
            sseManager.broadcast(event);

            // 3 — receive within 5 s
            String raw = client.waitForMessage(5);
            assertNotNull(raw, "SSE data line must be received within 5 s");

            // 4 — parse and assert the JSON payload
            JsonNode json = MAPPER.readTree(raw);
            assertEquals("SESSION_STARTED", json.path("eventType").asText());
            assertEquals("bvt-session-1",   json.path("sessionId").asText());
            assertEquals("hello",            json.path("data").path("message").asText());
            long ts = json.path("timestampMs").asLong();
            assertTrue(ts > 1_700_000_000_000L, "timestampMs must be a plausible epoch-ms value");

        } finally {
            client.disconnect();
        }
    }
}
