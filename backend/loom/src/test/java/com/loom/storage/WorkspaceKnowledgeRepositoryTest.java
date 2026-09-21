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

import static org.assertj.core.api.Assertions.assertThat;

import com.loom.domain.AgentExecution;
import com.loom.domain.AgentExecutionStatus;
import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.domain.WorkspaceKnowledge;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.WorkspaceKnowledgeRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkspaceKnowledgeRepositoryTest {

    private static Path dbFile;
    private static DatabaseManager db;
    private static WorkspaceKnowledgeRepository knowledgeRepo;
    private static AgentExecutionRepository execRepo;
    private static SessionRepository sessionRepo;

    @BeforeAll
    static void setUp() throws IOException {
        dbFile = Files.createTempFile("loom-wk-repo-test-", ".db");
        dbFile.toFile().deleteOnExit();
        db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);
        knowledgeRepo = new WorkspaceKnowledgeRepository(db);
        execRepo = new AgentExecutionRepository(db);
        sessionRepo = new SessionRepository(db);
    }

    @AfterAll
    static void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Insert a transient session referencing the fixture workspace + workflow so FK constraints are
     * satisfied.
     */
    private Session insertSession(String sessionId) {
        Session s = new Session();
        s.setId(sessionId);
        s.setWorkspaceId(TestFixtures.WORKSPACE_ID);
        s.setWorkflowDefinitionId(TestFixtures.WORKFLOW_ID);
        s.setStatus(SessionStatus.RUNNING);
        s.setStartedAt(System.currentTimeMillis());
        sessionRepo.save(s);
        return s;
    }

    private AgentExecution insertExec(String sessionId, long startedAt) {
        AgentExecution exec = new AgentExecution();
        exec.setSessionId(sessionId);
        exec.setNodeId("node-worker");
        exec.setStatus(AgentExecutionStatus.COMPLETED);
        exec.setStartedAt(startedAt);
        exec.setCompletedAt(startedAt + 100L);
        execRepo.save(exec);
        return exec;
    }

    private WorkspaceKnowledge insertKnowledge(String sourceExecutionId, long createdAt) {
        WorkspaceKnowledge wk = new WorkspaceKnowledge();
        wk.setWorkspaceId(TestFixtures.WORKSPACE_ID);
        wk.setSourceExecutionId(sourceExecutionId);
        wk.setTitle("Report at " + createdAt);
        wk.setContent("content-" + createdAt);
        wk.setCreatedAt(createdAt);
        knowledgeRepo.save(wk);
        return wk;
    }

    // ── test 1: rows for session A returned, ordered by created_at ───────────

    @Test
    void findBySessionIdReturnsBothRowsInCreatedAtOrder() {
        insertSession("session-wk-a");
        AgentExecution exec1 = insertExec("session-wk-a", 1000L);
        AgentExecution exec2 = insertExec("session-wk-a", 2000L);
        WorkspaceKnowledge wk1 = insertKnowledge(exec1.getId(), 1000L);
        WorkspaceKnowledge wk2 = insertKnowledge(exec2.getId(), 2000L);

        List<WorkspaceKnowledge> results = knowledgeRepo.findBySessionId("session-wk-a");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(wk1.getId());
        assertThat(results.get(1).getId()).isEqualTo(wk2.getId());
    }

    // ── test 2: session with no executions → empty list ──────────────────────

    @Test
    void findBySessionIdWithNoExecutionsReturnsEmpty() {
        List<WorkspaceKnowledge> results = knowledgeRepo.findBySessionId("session-no-execs");
        assertThat(results).isEmpty();
    }

    // ── test 3: rows from a different session not included ───────────────────

    @Test
    void findBySessionIdDoesNotReturnRowsFromDifferentSession() {
        // Insert independent rows for both session-wk-c and session-wk-d so this test
        // does not rely on state from any other test method.
        insertSession("session-wk-c");
        AgentExecution execC = insertExec("session-wk-c", 3000L);
        WorkspaceKnowledge wkC = insertKnowledge(execC.getId(), 3000L);

        insertSession("session-wk-d");
        AgentExecution execD = insertExec("session-wk-d", 4000L);
        WorkspaceKnowledge wkD = insertKnowledge(execD.getId(), 4000L);

        // session-wk-c must return only its own row
        List<WorkspaceKnowledge> resultsC = knowledgeRepo.findBySessionId("session-wk-c");
        assertThat(resultsC).hasSize(1);
        assertThat(resultsC.get(0).getId()).isEqualTo(wkC.getId());

        // session-wk-d must return only its own row, not session-wk-c's
        List<WorkspaceKnowledge> resultsD = knowledgeRepo.findBySessionId("session-wk-d");
        assertThat(resultsD).hasSize(1);
        assertThat(resultsD.get(0).getId()).isEqualTo(wkD.getId());

        // Explicit exclusion: wkD must not appear in session-wk-c results, and vice-versa
        assertThat(resultsC.stream().map(WorkspaceKnowledge::getId)).doesNotContain(wkD.getId());
        assertThat(resultsD.stream().map(WorkspaceKnowledge::getId)).doesNotContain(wkC.getId());
    }
}
