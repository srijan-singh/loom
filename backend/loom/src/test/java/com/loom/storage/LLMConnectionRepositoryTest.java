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

import com.loom.llm.LLMConnection;
import com.loom.storage.repository.LLMConnectionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LLMConnectionRepository: CRUD + default management")
class LLMConnectionRepositoryTest {

    private static Path dbFile;
    private static DatabaseManager db;
    private static LLMConnectionRepository repo;

    @BeforeAll
    static void setUp() throws IOException {
        dbFile = Files.createTempFile("loom-llmconn-", ".db");
        dbFile.toFile().deleteOnExit();
        db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);
        repo = new LLMConnectionRepository(db);
    }

    @AfterAll
    static void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    private static LLMConnection makeConnection(String name, boolean isDefault) {
        LLMConnection conn = new LLMConnection();
        conn.setName(name);
        conn.setBaseUrl("https://api.example.com");
        conn.setModel("gpt-4o");
        conn.setApiKey("sk-test");
        conn.setDefault(isDefault);
        conn.setCreatedAt(System.currentTimeMillis());
        return conn;
    }

    @Test
    @DisplayName("save + findById round-trip: all fields match, isDefault == false")
    void saveAndFindById() {
        LLMConnection conn = makeConnection("My Connection", false);

        repo.save(conn);

        Optional<LLMConnection> found = repo.findById(conn.getId());
        assertTrue(found.isPresent());
        assertEquals(conn.getId(), found.get().getId());
        assertEquals("My Connection", found.get().getName());
        assertEquals("https://api.example.com", found.get().getBaseUrl());
        assertEquals("gpt-4o", found.get().getModel());
        assertEquals("sk-test", found.get().getApiKey());
        assertFalse(found.get().isDefault());
        assertEquals(conn.getCreatedAt(), found.get().getCreatedAt());
    }

    @Test
    @DisplayName("findDefault returns the marked default connection")
    void findDefaultReturnsMarked() {
        LLMConnection c1 = makeConnection("Conn-A", false);
        LLMConnection c2 = makeConnection("Conn-B", false);
        repo.save(c1);
        repo.save(c2);

        repo.setDefault(c1.getId());

        Optional<LLMConnection> def = repo.findDefault();
        assertTrue(def.isPresent());
        assertEquals(c1.getId(), def.get().getId());
    }

    @Test
    @DisplayName("setDefault clears previous default and sets new one")
    void setDefaultClearsPreviousDefault() {
        LLMConnection c1 = makeConnection("Conn-C", false);
        LLMConnection c2 = makeConnection("Conn-D", false);
        repo.save(c1);
        repo.save(c2);

        repo.setDefault(c1.getId());
        repo.setDefault(c2.getId());

        Optional<LLMConnection> def = repo.findDefault();
        assertTrue(def.isPresent());
        assertEquals(c2.getId(), def.get().getId());

        assertFalse(repo.findById(c1.getId()).get().isDefault());
    }

    @Test
    @DisplayName("delete removes the record")
    void deleteRemovesRecord() {
        LLMConnection conn = makeConnection("To Be Deleted", false);
        repo.save(conn);

        repo.delete(conn.getId());

        assertTrue(repo.findById(conn.getId()).isEmpty());
    }

    @Test
    @DisplayName("findAll returns at least the saved connections")
    void findAllReturnsSavedConnections() {
        LLMConnection c1 = makeConnection("FindAll-X", false);
        LLMConnection c2 = makeConnection("FindAll-Y", false);
        repo.save(c1);
        repo.save(c2);

        List<LLMConnection> all = repo.findAll();
        assertTrue(all.size() >= 2);
        assertTrue(all.stream().anyMatch(c -> c.getId().equals(c1.getId())));
        assertTrue(all.stream().anyMatch(c -> c.getId().equals(c2.getId())));
    }
}
