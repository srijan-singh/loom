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
package com.loom.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.loom.storage.DatabaseManager;
import com.loom.storage.TestFixtures;
import com.loom.storage.repository.LLMConnectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LLMProviderFactory: provider selection")
class LLMProviderFactoryTest {

    private Path dbFile;
    private LLMConnectionRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("loom-factory-", ".db");
        dbFile.toFile().deleteOnExit();
        DatabaseManager db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);
        repo = new LLMConnectionRepository(db);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    @Test
    @DisplayName("findDefault() returns a connection → create() returns OpenAICompatibleProvider")
    void withDefaultConnection_returnsOpenAICompatibleProvider() {
        LLMConnection conn = new LLMConnection();
        conn.setName("My LLM");
        conn.setBaseUrl("https://api.example.com");
        conn.setApiKey("sk-test");
        conn.setModel("gpt-4o");
        conn.setDefault(true);
        conn.setCreatedAt(System.currentTimeMillis());
        repo.save(conn);
        repo.setDefault(conn.getId());

        LLMGateway gateway = LLMProviderFactory.create(repo);

        assertInstanceOf(OpenAICompatibleProvider.class, gateway);
    }

    @Test
    @DisplayName("findDefault() returns empty → create() returns MockLLMProvider")
    void withNoDefaultConnection_returnsMockLLMProvider() {
        // repo is empty — no connections saved

        LLMGateway gateway = LLMProviderFactory.create(repo);

        assertInstanceOf(MockLLMProvider.class, gateway);
    }
}
