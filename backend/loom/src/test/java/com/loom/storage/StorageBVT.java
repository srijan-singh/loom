package com.loom.storage;

import com.loom.domain.*;
import com.loom.storage.repository.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Build Verification Test — full end-to-end storage layer smoke test.
 *
 * <p>Uses a real SQLite file in a JVM temp directory, exercises every repository's
 * {@code save → findById} round-trip, and validates the three complex serialization
 * paths: tag lists (JSON array), allowedMcpIds (JSON array), and
 * WorkflowDefinition graph (JSON nodes + edges). No mocks; no in-memory fakes.
 *
 * <p>A single test method is used intentionally — one coherent behaviour story
 * is easier to reason about and extend than many isolated unit tests.
 */
@DisplayName("Storage BVT: SQLite persistence layer — full round-trip")
class StorageBVT {

    private static Path dbFile;
    private static DatabaseManager db;

    // repositories under test
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
        // DatabaseManager receives the path directly via its package-private constructor.
        db = new DatabaseManager(dbFile.toAbsolutePath().toString());

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

    @Test
    @DisplayName("All repositories: save → findById → findAll → extra queries → delete")
    void fullStorageRoundTrip() {

        // ── 1. SKILL ────────────────────────────────────────────────────────────
        Skill skill = new Skill();
        skill.setName("Web Research");
        skill.setDescription("Searches the web");
        skill.setContent("# Web Research\nUse Tavily to search.");
        skill.setTags(List.of("search", "web", "research"));
        skill.setCreatedAt(System.currentTimeMillis());
        skill.setUpdatedAt(System.currentTimeMillis());
        skillRepo.save(skill);

        Optional<Skill> foundSkill = skillRepo.findById(skill.getId());
        assertTrue(foundSkill.isPresent(), "Skill must be findable by id");
        assertEquals("Web Research",         foundSkill.get().getName());
        assertEquals("Searches the web",     foundSkill.get().getDescription());
        assertEquals(3,                      foundSkill.get().getTags().size());
        assertTrue(foundSkill.get().getTags().contains("search"));
        assertTrue(foundSkill.get().getTags().contains("research"));

        List<Skill> byTag = skillRepo.findByTag("web");
        assertEquals(1, byTag.size(), "findByTag(\"web\") must return the saved skill");
        assertEquals(skill.getId(), byTag.get(0).getId());

        assertEquals(0, skillRepo.findByTag("nonexistent").size());

        // Tag containing a comma must round-trip as a single value, not split into two
        Skill commaTagSkill = new Skill();
        commaTagSkill.setName("Comma Tag Skill");
        commaTagSkill.setTags(List.of("a,b", "c"));
        commaTagSkill.setCreatedAt(System.currentTimeMillis());
        commaTagSkill.setUpdatedAt(System.currentTimeMillis());
        skillRepo.save(commaTagSkill);

        Optional<Skill> foundCommaSkill = skillRepo.findById(commaTagSkill.getId());
        assertTrue(foundCommaSkill.isPresent());
        assertEquals(2, foundCommaSkill.get().getTags().size(), "tag containing comma must not be split");
        assertTrue(foundCommaSkill.get().getTags().contains("a,b"));
        assertTrue(foundCommaSkill.get().getTags().contains("c"));
        assertEquals(1, skillRepo.findByTag("a,b").size(), "findByTag must match tag containing comma exactly");
        assertEquals(0, skillRepo.findByTag("a").size(),   "findByTag must not match partial comma-split");
        skillRepo.delete(commaTagSkill.getId());

        // ── 2. MCP CONNECTION ────────────────────────────────────────────────────
        MCPConnection mcp = new MCPConnection();
        mcp.setName("Brave Search");
        mcp.setType("stdio");
        mcp.setConfig(java.util.Map.of("command", "npx", "args", List.of("-y", "brave-search-mcp")));
        mcp.setStatus(MCPStatus.CONNECTED);
        mcp.setCreatedAt(System.currentTimeMillis());
        mcpRepo.save(mcp);

        Optional<MCPConnection> foundMcp = mcpRepo.findById(mcp.getId());
        assertTrue(foundMcp.isPresent(), "MCPConnection must be findable by id");
        assertEquals("Brave Search",      foundMcp.get().getName());
        assertEquals(MCPStatus.CONNECTED, foundMcp.get().getStatus());
        assertEquals("npx",               foundMcp.get().getConfig().get("command"));

        // ── 3. AGENT DEFINITION ──────────────────────────────────────────────────
        AgentDefinition agent = new AgentDefinition();
        agent.setName("Researcher");
        agent.setRoleDescription("Performs web research tasks");
        agent.setSkillId(skill.getId());
        agent.setAllowedMcpIds(List.of(mcp.getId()));
        agent.setCreatedAt(System.currentTimeMillis());
        agent.setUpdatedAt(System.currentTimeMillis());
        agentRepo.save(agent);

        Optional<AgentDefinition> foundAgent = agentRepo.findById(agent.getId());
        assertTrue(foundAgent.isPresent(), "AgentDefinition must be findable by id");
        assertEquals("Researcher",           foundAgent.get().getName());
        assertEquals(skill.getId(),          foundAgent.get().getSkillId());
        assertEquals(1,                      foundAgent.get().getAllowedMcpIds().size());
        assertEquals(mcp.getId(),            foundAgent.get().getAllowedMcpIds().get(0));

        List<AgentDefinition> bySkill = agentRepo.findBySkillId(skill.getId());
        assertEquals(1, bySkill.size(), "findBySkillId must return the agent");

        // ── 4. WORKFLOW DEFINITION (graph round-trip) ────────────────────────────
        WorkflowNode start = new WorkflowNode();
        start.setId("node-start");
        start.setLabel("Start");
        start.setNodeType(NodeType.START);
        start.setPositionX(0);
        start.setPositionY(0);

        WorkflowNode worker = new WorkflowNode();
        worker.setId("node-worker");
        worker.setLabel("Research");
        worker.setNodeType(NodeType.WORKER);
        worker.setAgentDefinitionId(agent.getId());
        worker.setPositionX(200);
        worker.setPositionY(0);

        WorkflowNode end = new WorkflowNode();
        end.setId("node-end");
        end.setLabel("End");
        end.setNodeType(NodeType.END);
        end.setPositionX(400);
        end.setPositionY(0);

        WorkflowEdge edge1 = new WorkflowEdge();
        edge1.setId("edge-1");
        edge1.setFromNodeId("node-start");
        edge1.setToNodeId("node-worker");
        edge1.setCondition(EdgeCondition.ALWAYS);

        WorkflowEdge edge2 = new WorkflowEdge();
        edge2.setId("edge-2");
        edge2.setFromNodeId("node-worker");
        edge2.setToNodeId("node-end");
        edge2.setCondition(EdgeCondition.ON_SUCCESS);

        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setName("Research Pipeline");
        wf.setType(WorkflowType.CHAIN);
        wf.setCreatedBy(WorkflowCreatedBy.USER);
        wf.setNodes(List.of(start, worker, end));
        wf.setEdges(List.of(edge1, edge2));
        wf.setCreatedAt(System.currentTimeMillis());
        wf.setUpdatedAt(System.currentTimeMillis());
        workflowRepo.save(wf);

        Optional<WorkflowDefinition> foundWf = workflowRepo.findById(wf.getId());
        assertTrue(foundWf.isPresent(), "WorkflowDefinition must be findable by id");
        assertEquals("Research Pipeline",    foundWf.get().getName());
        assertEquals(WorkflowType.CHAIN,     foundWf.get().getType());
        assertEquals(WorkflowCreatedBy.USER, foundWf.get().getCreatedBy());

        // 3 nodes and 2 edges survive JSON round-trip without data loss
        assertNotNull(foundWf.get().getNodes());
        assertEquals(3, foundWf.get().getNodes().size(), "3 nodes must survive graph serialization");
        assertEquals("node-start",  foundWf.get().getNodes().get(0).getId());
        assertEquals("node-worker", foundWf.get().getNodes().get(1).getId());
        assertEquals("node-end",    foundWf.get().getNodes().get(2).getId());
        assertEquals(NodeType.WORKER, foundWf.get().getNodes().get(1).getNodeType());
        assertEquals(agent.getId(),   foundWf.get().getNodes().get(1).getAgentDefinitionId());
        assertEquals(200.0,           foundWf.get().getNodes().get(1).getPositionX(), 0.001);

        assertNotNull(foundWf.get().getEdges());
        assertEquals(2, foundWf.get().getEdges().size(), "2 edges must survive graph serialization");
        assertEquals("edge-1",            foundWf.get().getEdges().get(0).getId());
        assertEquals(EdgeCondition.ALWAYS,     foundWf.get().getEdges().get(0).getCondition());
        assertEquals(EdgeCondition.ON_SUCCESS, foundWf.get().getEdges().get(1).getCondition());

        List<WorkflowDefinition> byType = workflowRepo.findByType(WorkflowType.CHAIN);
        assertEquals(1, byType.size(), "findByType(CHAIN) must return the workflow");

        // ── 5. WORKSPACE ─────────────────────────────────────────────────────────
        Workspace workspace = new Workspace();
        workspace.setName("Research Project");
        workspace.setDescription("General research workspace");
        workspace.setCreatedAt(System.currentTimeMillis());
        workspaceRepo.save(workspace);

        Optional<Workspace> foundWs = workspaceRepo.findById(workspace.getId());
        assertTrue(foundWs.isPresent(), "Workspace must be findable by id");
        assertEquals("Research Project", foundWs.get().getName());

        // ── 6. SESSION ───────────────────────────────────────────────────────────
        Session session = new Session();
        session.setWorkspaceId(workspace.getId());
        session.setWorkflowDefinitionId(wf.getId());
        session.setStatus(SessionStatus.RUNNING);
        session.setStartedAt(System.currentTimeMillis());
        sessionRepo.save(session);

        Optional<Session> foundSession = sessionRepo.findById(session.getId());
        assertTrue(foundSession.isPresent(), "Session must be findable by id");
        assertEquals(SessionStatus.RUNNING, foundSession.get().getStatus());
        assertNull(foundSession.get().getCompletedAt(), "completedAt must be null when not set");

        List<Session> byWorkspace = sessionRepo.findByWorkspaceId(workspace.getId());
        assertEquals(1, byWorkspace.size(), "findByWorkspaceId must return the session");

        List<Session> byStatus = sessionRepo.findByStatus(SessionStatus.RUNNING);
        assertEquals(1, byStatus.size(), "findByStatus(RUNNING) must return the session");

        // complete the session
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(System.currentTimeMillis());
        sessionRepo.save(session);

        Optional<Session> completedSession = sessionRepo.findById(session.getId());
        assertTrue(completedSession.isPresent());
        assertEquals(SessionStatus.COMPLETED, completedSession.get().getStatus());
        assertNotNull(completedSession.get().getCompletedAt(), "completedAt must be set after completion");

        // ── 7. AGENT EXECUTION ───────────────────────────────────────────────────
        AgentExecution exec = new AgentExecution();
        exec.setSessionId(session.getId());
        exec.setNodeId("node-worker");
        exec.setAgentDefinitionId(agent.getId());
        exec.setStatus(AgentExecutionStatus.RUNNING);
        exec.setInputContext("{\"query\":\"latest AI news\"}");
        exec.setStartedAt(System.currentTimeMillis());
        execRepo.save(exec);

        Optional<AgentExecution> foundExec = execRepo.findById(exec.getId());
        assertTrue(foundExec.isPresent(), "AgentExecution must be findable by id");
        assertEquals(AgentExecutionStatus.RUNNING,       foundExec.get().getStatus());
        assertEquals("{\"query\":\"latest AI news\"}",   foundExec.get().getInputContext());
        assertNull(foundExec.get().getCompletedAt(),     "completedAt must be null while running");

        // complete the execution
        exec.setStatus(AgentExecutionStatus.COMPLETED);
        exec.setOutput("Found 10 results about AI.");
        exec.setReport("# Summary\nAI is advancing rapidly.");
        exec.setCompletedAt(System.currentTimeMillis());
        execRepo.save(exec);

        Optional<AgentExecution> completedExec = execRepo.findById(exec.getId());
        assertTrue(completedExec.isPresent());
        assertEquals(AgentExecutionStatus.COMPLETED, completedExec.get().getStatus());
        assertEquals("Found 10 results about AI.",   completedExec.get().getOutput());
        assertNotNull(completedExec.get().getCompletedAt());

        // ── 8. WORKSPACE KNOWLEDGE ───────────────────────────────────────────────
        WorkspaceKnowledge knowledge = new WorkspaceKnowledge();
        knowledge.setWorkspaceId(workspace.getId());
        knowledge.setSourceExecutionId(exec.getId());
        knowledge.setTitle("AI Trends 2025");
        knowledge.setContent("# AI Trends\nLLMs are widely adopted.");
        knowledge.setTags(List.of("ai", "trends", "research"));
        knowledge.setCreatedAt(System.currentTimeMillis());
        knowledgeRepo.save(knowledge);

        Optional<WorkspaceKnowledge> foundKnowledge = knowledgeRepo.findById(knowledge.getId());
        assertTrue(foundKnowledge.isPresent(), "WorkspaceKnowledge must be findable by id");
        assertEquals("AI Trends 2025",               foundKnowledge.get().getTitle());
        assertEquals(exec.getId(),                   foundKnowledge.get().getSourceExecutionId());
        assertEquals(3,                              foundKnowledge.get().getTags().size());
        assertTrue(foundKnowledge.get().getTags().contains("ai"));
        assertTrue(foundKnowledge.get().getTags().contains("trends"));

        // ── 9. findAll ────────────────────────────────────────────────────────────
        assertEquals(1, skillRepo.findAll().size(),     "findAll skills");
        assertEquals(1, mcpRepo.findAll().size(),       "findAll mcp connections");
        assertEquals(1, agentRepo.findAll().size(),     "findAll agents");
        assertEquals(1, workflowRepo.findAll().size(),  "findAll workflows");
        assertEquals(1, workspaceRepo.findAll().size(), "findAll workspaces");
        assertEquals(1, sessionRepo.findAll().size(),   "findAll sessions");
        assertEquals(1, execRepo.findAll().size(),      "findAll agent executions");
        assertEquals(1, knowledgeRepo.findAll().size(), "findAll workspace knowledge");

        // ── 10. DELETE ────────────────────────────────────────────────────────────
        skillRepo.delete(skill.getId());
        assertTrue(skillRepo.findById(skill.getId()).isEmpty(), "Skill must be gone after delete");
        assertEquals(0, skillRepo.findAll().size());

        sessionRepo.delete(session.getId());
        assertTrue(sessionRepo.findById(session.getId()).isEmpty(), "Session must be gone after delete");
    }
}
