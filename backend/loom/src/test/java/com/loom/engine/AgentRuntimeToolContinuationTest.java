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
package com.loom.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.loom.domain.AgentDefinition;
import com.loom.domain.AgentExecution;
import com.loom.domain.NodeType;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkflowNode;
import com.loom.engine.agent.AgentRuntime;
import com.loom.llm.LLMConnection;
import com.loom.llm.LLMGateway;
import com.loom.llm.LLMMessage;
import com.loom.llm.LLMRequest;
import com.loom.llm.LLMResponse;
import com.loom.mcp.MCPClient;
import com.loom.storage.DatabaseManager;
import com.loom.storage.TestFixtures;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.AgentRepository;
import com.loom.storage.repository.LLMConnectionRepository;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.SkillRepository;
import com.loom.storage.repository.WorkspaceKnowledgeRepository;
import com.loom.transport.SSEManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression test for the tool-execution continuation path in AgentRuntime.
 *
 * <p>Verifies that after a TOOL_CALL response the follow-up LLMRequest retains the original
 * userPrompt unchanged, and that its history contains the assistant tool-call message immediately
 * followed by the tool-result message.
 */
@ExtendWith(MockitoExtension.class)
class AgentRuntimeToolContinuationTest {

    @Mock private LLMGateway llmGateway;
    @Mock private MCPClient mcpClient;
    @Mock private SSEManager sseManager;
    @Mock private SkillRepository skillRepository;
    @Mock private WorkspaceKnowledgeRepository knowledgeRepository;
    @Mock private AgentExecutionRepository executionRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private com.loom.storage.repository.WorkflowRepository workflowRepository;
    @Mock private AgentRepository agentRepository;

    // Real SQLite-backed repo — shared across tests that need it
    private Path dbFile;
    private LLMConnectionRepository llmConnectionRepository;

    private AgentRuntime runtime;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("loom-agentrt-", ".db");
        dbFile.toFile().deleteOnExit();
        DatabaseManager db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);
        llmConnectionRepository = new LLMConnectionRepository(db);

        runtime =
                new AgentRuntime(
                        llmGateway,
                        mcpClient,
                        sseManager,
                        skillRepository,
                        knowledgeRepository,
                        executionRepository,
                        sessionRepository,
                        workflowRepository,
                        agentRepository,
                        llmConnectionRepository);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    @Test
    void toolCallContinuation_followUpRequestPreservesUserPromptAndAppendsHistory() {
        // ── arrange ──────────────────────────────────────────────────────────

        Session session = new Session();
        session.setId("sess-1");
        session.setWorkspaceId("ws-1");
        session.setStatus(SessionStatus.RUNNING);
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setName("Test Agent");
        agent.setRoleDescription("You are a test agent.");
        agent.setAllowedMcpIds(Collections.emptyList());
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));

        when(knowledgeRepository.findByWorkspaceId(anyString()))
                .thenReturn(Collections.emptyList());
        when(executionRepository.findBySessionIdAndNodeId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        doAnswer(
                        inv -> {
                            ((AgentExecution) inv.getArgument(0)).setId("exec-1");
                            return null;
                        })
                .when(executionRepository)
                .save(any(AgentExecution.class));

        // First LLM turn: emit TOOL_CALL then DONE
        doAnswer(
                        inv -> {
                            java.util.function.Consumer<LLMResponse> consumer = inv.getArgument(1);
                            consumer.accept(LLMResponse.toolCall("search", Collections.emptyMap()));
                            consumer.accept(LLMResponse.done());
                            return null;
                        })
                .when(llmGateway)
                .send(any(LLMRequest.class), any());

        when(mcpClient.execute(anyString(), any())).thenReturn("42 results");

        // ── act ───────────────────────────────────────────────────────────────

        WorkflowNode node = new WorkflowNode();
        node.setId("node-1");
        node.setNodeType(NodeType.WORKER);
        node.setAgentDefinitionId("agent-1");

        runtime.executeNode("sess-1", node, "find me something");

        // ── assert ────────────────────────────────────────────────────────────

        // Capture both llmGateway.send() calls; the second is the follow-up turn
        ArgumentCaptor<LLMRequest> reqCaptor = ArgumentCaptor.forClass(LLMRequest.class);
        org.mockito.Mockito.verify(llmGateway, org.mockito.Mockito.times(2))
                .send(reqCaptor.capture(), any());

        LLMRequest followUp = reqCaptor.getAllValues().get(1);

        // userPrompt must be the original (role + input), never replaced
        assertThat(followUp.getUserPrompt())
                .isEqualTo("You are a test agent.\n\nfind me something");

        // history must end with: assistant tool-call message, then user tool-result message
        List<LLMMessage> history = followUp.getHistory();
        assertThat(history).isNotNull().hasSizeGreaterThanOrEqualTo(2);

        LLMMessage toolCallMsg = history.get(history.size() - 2);
        LLMMessage toolResultMsg = history.get(history.size() - 1);

        assertThat(toolCallMsg.getRole()).isEqualTo("assistant");
        assertThat(toolCallMsg.getContent()).isEqualTo("[Called tool: search]");

        assertThat(toolResultMsg.getRole()).isEqualTo("user");
        assertThat(toolResultMsg.getContent()).isEqualTo("Tool result for search: 42 results");
    }

    @Test
    void resolveGateway_fallsBackToStartupWiredGateway_whenRepoReturnsEmpty() {
        // llmConnectionRepository is empty (no connections saved) → findDefault() returns empty
        // → resolveGateway falls back to the startup-wired llmGateway mock

        Session session = new Session();
        session.setId("sess-fb");
        session.setWorkspaceId("ws-fb");
        session.setStatus(SessionStatus.RUNNING);
        when(sessionRepository.findById("sess-fb")).thenReturn(Optional.of(session));

        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-fb");
        agent.setName("Fallback Agent");
        agent.setRoleDescription("Fallback.");
        agent.setAllowedMcpIds(Collections.emptyList());
        // llmConnectionId is null → triggers findDefault()
        when(agentRepository.findById("agent-fb")).thenReturn(Optional.of(agent));

        when(knowledgeRepository.findByWorkspaceId(anyString()))
                .thenReturn(Collections.emptyList());
        when(executionRepository.findBySessionIdAndNodeId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        doAnswer(
                        inv -> {
                            ((AgentExecution) inv.getArgument(0)).setId("exec-fb");
                            return null;
                        })
                .when(executionRepository)
                .save(any(AgentExecution.class));

        doAnswer(
                        inv -> {
                            java.util.function.Consumer<LLMResponse> consumer = inv.getArgument(1);
                            consumer.accept(LLMResponse.token("ok"));
                            consumer.accept(LLMResponse.done());
                            return null;
                        })
                .when(llmGateway)
                .send(any(LLMRequest.class), any());

        WorkflowNode node = new WorkflowNode();
        node.setId("node-fb");
        node.setNodeType(NodeType.WORKER);
        node.setAgentDefinitionId("agent-fb");

        // Act
        runtime.executeNode("sess-fb", node, "hello");

        // Assert: the startup-wired llmGateway mock was called (not a real provider)
        verify(llmGateway).send(any(LLMRequest.class), any());
    }

    @Test
    void resolveGateway_usesSpecificConnection_whenLlmConnectionIdIsSet() throws Exception {
        // Seed a real LLMConnection pointing at a MockWebServer so the resolved
        // OpenAICompatibleProvider has a reachable endpoint for this execution.
        MockWebServer mockLlmServer = new MockWebServer();
        mockLlmServer.start();
        try {
            // Enqueue one SSE response that the resolved provider will consume
            String sseBody =
                    "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n" + "data: [DONE]\n";
            mockLlmServer.enqueue(
                    new MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "text/event-stream")
                            .setBody(sseBody));

            // Save connection into the real SQLite repo
            LLMConnection conn = new LLMConnection();
            conn.setName("Specific LLM");
            conn.setBaseUrl(mockLlmServer.url("").toString().replaceAll("/$", ""));
            conn.setModel("test-model");
            conn.setApiKey("sk-specific");
            conn.setCreatedAt(System.currentTimeMillis());
            llmConnectionRepository.save(conn);
            String specificConnId = conn.getId();

            // Wire session
            Session session = new Session();
            session.setId("sess-specific");
            session.setWorkspaceId("ws-specific");
            session.setStatus(SessionStatus.RUNNING);
            when(sessionRepository.findById("sess-specific")).thenReturn(Optional.of(session));

            // Agent carries the specific connection id — not null
            AgentDefinition agent = new AgentDefinition();
            agent.setId("agent-specific");
            agent.setName("Specific Agent");
            agent.setRoleDescription("Agent using a specific connection.");
            agent.setAllowedMcpIds(Collections.emptyList());
            agent.setLlmConnectionId(specificConnId);
            when(agentRepository.findById("agent-specific")).thenReturn(Optional.of(agent));

            when(knowledgeRepository.findByWorkspaceId(anyString()))
                    .thenReturn(Collections.emptyList());
            when(executionRepository.findBySessionIdAndNodeId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            doAnswer(
                            inv -> {
                                ((AgentExecution) inv.getArgument(0)).setId("exec-specific");
                                return null;
                            })
                    .when(executionRepository)
                    .save(any(AgentExecution.class));

            WorkflowNode node = new WorkflowNode();
            node.setId("node-specific");
            node.setNodeType(NodeType.WORKER);
            node.setAgentDefinitionId("agent-specific");

            // Act
            runtime.executeNode("sess-specific", node, "ping");

            // Assert: the startup-wired mock gateway was NOT called — the resolved provider was
            // used
            verify(llmGateway, never()).send(any(LLMRequest.class), any());

            // The MockWebServer received exactly one request (from the resolved provider)
            assertThat(mockLlmServer.getRequestCount()).isEqualTo(1);
        } finally {
            mockLlmServer.shutdown();
        }
    }
}
