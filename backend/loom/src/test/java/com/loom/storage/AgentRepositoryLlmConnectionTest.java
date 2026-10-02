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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.loom.domain.AgentDefinition;
import com.loom.llm.LLMConnection;
import com.loom.storage.repository.AgentRepository;
import com.loom.storage.repository.LLMConnectionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AgentRepository: llmConnectionId round-trip")
class AgentRepositoryLlmConnectionTest {

    private static Path dbFile;
    private static DatabaseManager db;
    private static AgentRepository agentRepo;
    private static String llmConnectionId;

    @BeforeAll
    static void setUp() throws IOException {
        dbFile = Files.createTempFile("loom-agent-llm-", ".db");
        dbFile.toFile().deleteOnExit();
        db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);
        agentRepo = new AgentRepository(db);

        // Seed a real LLMConnection so the FK constraint on agent_definitions is satisfied
        LLMConnection conn = new LLMConnection();
        conn.setName("Test LLM");
        conn.setBaseUrl("https://api.example.com");
        conn.setModel("gpt-4o");
        conn.setApiKey("sk-test");
        conn.setCreatedAt(System.currentTimeMillis());
        new LLMConnectionRepository(db).save(conn);
        llmConnectionId = conn.getId();
    }

    @AfterAll
    static void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    @Test
    @DisplayName("save then findById: llmConnectionId null round-trips as null")
    void saveThenFindById_nullLlmConnectionId() {
        AgentDefinition agent = new AgentDefinition();
        agent.setName("Agent With No Connection");
        agent.setAllowedMcpIds(List.of());
        // llmConnectionId intentionally not set — remains null

        agentRepo.save(agent);

        Optional<AgentDefinition> found = agentRepo.findById(agent.getId());
        assertTrue(found.isPresent());
        assertNull(found.get().getLlmConnectionId());
    }

    @Test
    @DisplayName("save then findById: llmConnectionId round-trips correctly")
    void saveThenFindById_withLlmConnectionId() {
        AgentDefinition agent = new AgentDefinition();
        agent.setName("Agent With Connection");
        agent.setAllowedMcpIds(List.of());
        agent.setLlmConnectionId(llmConnectionId);

        agentRepo.save(agent);

        Optional<AgentDefinition> found = agentRepo.findById(agent.getId());
        assertTrue(found.isPresent());
        assertEquals(llmConnectionId, found.get().getLlmConnectionId());
    }
}
