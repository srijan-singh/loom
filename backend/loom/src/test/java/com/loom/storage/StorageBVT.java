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

package com.loom.storage;

import com.loom.domain.*;
import com.loom.storage.repository.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.loom.storage.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Build Verification Test — full end-to-end storage layer smoke test.
 *
 * <p>Uses a real SQLite file in a JVM temp directory. Fixture rows are
 * pre-populated from {@code testFixtures.sql} via {@link TestFixtures#load}.
 * Each test method covers exactly one repository and references fixed IDs
 * from {@link TestFixtures} — no shared mutable state between tests.
 */
@DisplayName("Storage BVT: SQLite persistence layer — full round-trip")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorageBVT {

    private static Path dbFile;
    private static DatabaseManager db;

    private static SkillRepository skillRepo;
    private static MCPConnectionRepository mcpRepo;
    private static AgentRepository agentRepo;
    private static WorkflowRepository workflowRepo;
    private static SessionRepository sessionRepo;
    private static WorkspaceRepository workspaceRepo;
    private static AgentExecutionRepository execRepo;
    private static WorkspaceKnowledgeRepository knowledgeRepo;

    @BeforeAll
    static void setUp() throws IOException {
        dbFile = Files.createTempFile("loom-bvt-", ".db");
        dbFile.toFile().deleteOnExit();
        db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);

        skillRepo     = new SkillRepository(db);
        mcpRepo       = new MCPConnectionRepository(db);
        agentRepo     = new AgentRepository(db);
        workflowRepo  = new WorkflowRepository(db);
        sessionRepo   = new SessionRepository(db);
        workspaceRepo = new WorkspaceRepository(db);
        execRepo      = new AgentExecutionRepository(db);
        knowledgeRepo = new WorkspaceKnowledgeRepository(db);
    }

    @AfterAll
    static void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    // ── 1. SKILL ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("SkillRepository: findById, tags round-trip, findByTag, comma-tag, delete")
    void skill() {
        Optional<Skill> found = skillRepo.findById(SKILL_ID);
        assertTrue(found.isPresent());
        assertEquals("Web Research", found.get().getName());
        assertEquals("Searches the web", found.get().getDescription());
        assertTrue(found.get().getTags().contains("search"));
        assertEquals(3, found.get().getTags().size());
        assertTrue(found.get().getTags().contains("web"));
        assertTrue(found.get().getTags().contains("research"));

        List<Skill> byTag = skillRepo.findByTag("web");
        assertEquals(1, byTag.size());
        assertEquals(SKILL_ID, byTag.get(0).getId());

        assertEquals(0, skillRepo.findByTag("nonexistent").size());

        // tag containing a comma must round-trip as one value, not be split
        Skill commaSkill = new Skill();
        commaSkill.setName("Comma Tag Skill");
        commaSkill.setTags(List.of("a,b", "c"));
        commaSkill.setCreatedAt(System.currentTimeMillis());
        commaSkill.setUpdatedAt(System.currentTimeMillis());
        skillRepo.save(commaSkill);

        Optional<Skill> foundComma = skillRepo.findById(commaSkill.getId());
        assertTrue(foundComma.isPresent());
        assertEquals(2, foundComma.get().getTags().size(), "tag containing comma must not be split");
        assertTrue(foundComma.get().getTags().contains("a,b"));
        assertEquals(1, skillRepo.findByTag("a,b").size(), "findByTag must match tag with comma exactly");
        assertEquals(0, skillRepo.findByTag("a").size(), "findByTag must not match partial comma-split");

        skillRepo.delete(commaSkill.getId());
        assertTrue(skillRepo.findById(commaSkill.getId()).isEmpty());
    }

    // ── 2. MCP CONNECTION ────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("MCPConnectionRepository: findById, config map round-trip")
    void mcpConnection() {
        Optional<MCPConnection> found = mcpRepo.findById(MCP_ID);
        assertTrue(found.isPresent());
        assertEquals("Brave Search", found.get().getName());
        assertEquals("stdio", found.get().getType());
        assertEquals(MCPStatus.CONNECTED, found.get().getStatus());
        assertEquals("npx", found.get().getConfig().get("command"));
    }

    // ── 3. AGENT DEFINITION ──────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("AgentRepository: findById, allowedMcpIds round-trip, findBySkillId")
    void agentDefinition() {
        Optional<AgentDefinition> found = agentRepo.findById(AGENT_ID);
        assertTrue(found.isPresent());
        assertEquals("Researcher", found.get().getName());
        assertEquals(SKILL_ID, found.get().getSkillId());
        assertEquals(1, found.get().getAllowedMcpIds().size());
        assertEquals(MCP_ID, found.get().getAllowedMcpIds().get(0));

        List<AgentDefinition> bySkill = agentRepo.findBySkillId(SKILL_ID);
        assertEquals(1, bySkill.size());
        assertEquals(AGENT_ID, bySkill.get(0).getId());
    }

    // ── 4. WORKFLOW DEFINITION ───────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("WorkflowRepository: findById, graph (nodes + edges) JSON round-trip, findByType")
    void workflowDefinition() {
        Optional<WorkflowDefinition> found = workflowRepo.findById(WORKFLOW_ID);
        assertTrue(found.isPresent());
        assertEquals("Research Pipeline",    found.get().getName());
        assertEquals(WorkflowType.CHAIN,     found.get().getType());
        assertEquals(WorkflowCreatedBy.USER, found.get().getCreatedBy());

        assertNotNull(found.get().getNodes());
        assertEquals(3, found.get().getNodes().size());
        assertEquals("node-start", found.get().getNodes().get(0).getId());
        assertEquals("node-worker", found.get().getNodes().get(1).getId());
        assertEquals("node-end", found.get().getNodes().get(2).getId());
        assertEquals(NodeType.WORKER, found.get().getNodes().get(1).getNodeType());
        assertEquals(AGENT_ID, found.get().getNodes().get(1).getAgentDefinitionId());
        assertEquals(200.0, found.get().getNodes().get(1).getPositionX(), 0.001);

        assertNotNull(found.get().getEdges());
        assertEquals(2, found.get().getEdges().size());
        assertEquals("edge-1", found.get().getEdges().get(0).getId());
        assertEquals(EdgeCondition.ALWAYS, found.get().getEdges().get(0).getCondition());
        assertEquals(EdgeCondition.ON_SUCCESS, found.get().getEdges().get(1).getCondition());

        List<WorkflowDefinition> byType = workflowRepo.findByType(WorkflowType.CHAIN);
        assertEquals(1, byType.size());
        assertEquals(WORKFLOW_ID, byType.get(0).getId());
    }

    // ── 5. WORKSPACE ─────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("WorkspaceRepository: findById")
    void workspace() {
        Optional<Workspace> found = workspaceRepo.findById(WORKSPACE_ID);
        assertTrue(found.isPresent());
        assertEquals("Research Project", found.get().getName());
        assertEquals("General research workspace", found.get().getDescription());
    }

    // ── 6. SESSION ───────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("SessionRepository: findById, findByWorkspaceId, findByStatus, save update, delete")
    void session() {
        // verify pre-populated completed session
        Optional<Session> found = sessionRepo.findById(SESSION_ID);
        assertTrue(found.isPresent());
        assertEquals(SessionStatus.COMPLETED, found.get().getStatus());
        assertNotNull(found.get().getCompletedAt());

        assertEquals(1, sessionRepo.findByWorkspaceId(WORKSPACE_ID).size());
        assertEquals(1, sessionRepo.findByStatus(SessionStatus.COMPLETED).size());

        // create + update a transient session
        Session session = new Session();
        session.setWorkspaceId(WORKSPACE_ID);
        session.setWorkflowDefinitionId(WORKFLOW_ID);
        session.setStatus(SessionStatus.RUNNING);
        session.setStartedAt(System.currentTimeMillis());
        sessionRepo.save(session);

        assertNull(sessionRepo.findById(session.getId()).get().getCompletedAt(),
                "completedAt must be null while running");

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepo.save(session);

        assertEquals(SessionStatus.COMPLETED,
                sessionRepo.findById(session.getId()).get().getStatus());

        sessionRepo.delete(session.getId());
        assertTrue(sessionRepo.findById(session.getId()).isEmpty());
    }

    // ── 7. AGENT EXECUTION ───────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("AgentExecutionRepository: findById, nullable completedAt, save update")
    void agentExecution() {
        // verify pre-populated completed execution
        Optional<AgentExecution> found = execRepo.findById(EXEC_ID);
        assertTrue(found.isPresent());
        assertEquals(AgentExecutionStatus.COMPLETED,  found.get().getStatus());
        assertEquals("Found 10 results about AI.", found.get().getOutput());
        assertEquals("{\"query\":\"latest AI news\"}", found.get().getInputContext());
        assertNotNull(found.get().getCompletedAt());

        // create a running execution and verify completedAt is null
        AgentExecution exec = new AgentExecution();
        exec.setSessionId(SESSION_ID);
        exec.setNodeId("node-worker");
        exec.setAgentDefinitionId(AGENT_ID);
        exec.setStatus(AgentExecutionStatus.RUNNING);
        exec.setStartedAt(System.currentTimeMillis());
        execRepo.save(exec);

        assertNull(execRepo.findById(exec.getId()).get().getCompletedAt(),
                "completedAt must be null while running");

        exec.setStatus(AgentExecutionStatus.COMPLETED);
        exec.setOutput("Done.");
        exec.setCompletedAt(System.currentTimeMillis());
        execRepo.save(exec);

        assertEquals(AgentExecutionStatus.COMPLETED,
                execRepo.findById(exec.getId()).get().getStatus());
    }

    // ── 8. WORKSPACE KNOWLEDGE ───────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("WorkspaceKnowledgeRepository: findById, tags round-trip")
    void workspaceKnowledge() {
        Optional<WorkspaceKnowledge> found = knowledgeRepo.findById(KNOWLEDGE_ID);
        assertTrue(found.isPresent());
        assertEquals("AI Trends 2025", found.get().getTitle());
        assertEquals(EXEC_ID, found.get().getSourceExecutionId());
        assertEquals(WORKSPACE_ID, found.get().getWorkspaceId());
        assertEquals(3, found.get().getTags().size());
        assertTrue(found.get().getTags().contains("ai"));
        assertTrue(found.get().getTags().contains("trends"));
        assertTrue(found.get().getTags().contains("research"));
    }

    // ── 9. findAll ────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("All repositories: findAll returns at least the seeded fixture rows")
    void findAll() {
        assertFalse(skillRepo.findAll().isEmpty(), "findAll skills");
        assertFalse(mcpRepo.findAll().isEmpty(), "findAll mcp connections");
        assertFalse(agentRepo.findAll().isEmpty(), "findAll agents");
        assertFalse(workflowRepo.findAll().isEmpty(), "findAll workflows");
        assertFalse(workspaceRepo.findAll().isEmpty(), "findAll workspaces");
        assertFalse(sessionRepo.findAll().isEmpty(), "findAll sessions");
        assertFalse(execRepo.findAll().isEmpty(), "findAll agent executions");
        assertFalse(knowledgeRepo.findAll().isEmpty(), "findAll workspace knowledge");
    }
}
