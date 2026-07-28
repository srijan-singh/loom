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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Loads {@code src/test/resources/db/testFixtures.sql} into a
 * {@link DatabaseManager} instance.
 *
 * <p>All fixture rows use fixed string IDs (constants below) so every test
 * can reference known values without sharing mutable state.
 *
 * <p>Usage in {@code @BeforeAll}:
 * <pre>{@code
 *   db = new DatabaseManager(dbFile.toAbsolutePath().toString());
 *   TestFixtures.load(db);
 * }</pre>
 */
public final class TestFixtures {

    // ── fixed IDs ─────────────────────────────────────────────────────────────
    public static final String SKILL_ID = "skill-web-research";
    public static final String MCP_ID = "mcp-brave";
    public static final String AGENT_ID = "agent-researcher";
    public static final String WORKFLOW_ID = "wf-research-pipeline";
    public static final String WORKSPACE_ID = "ws-research";
    public static final String SESSION_ID = "session-completed";
    public static final String EXEC_ID = "exec-research";
    public static final String KNOWLEDGE_ID = "knowledge-ai-trends";

    private static final String FIXTURES_PATH = "/db/testFixtures.sql";

    private TestFixtures() {}

    /**
     * Reads {@code testFixtures.sql} from the test classpath and executes
     * every statement against {@code db}.
     */
    public static void load(DatabaseManager db) {
        String sql = readResource();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String s : sql.split(";")) {
                String trimmed = s.replaceAll("(?m)^--[^\n]*\\n?", "").trim();
                if (!trimmed.isEmpty()) {
                    stmt.executeUpdate(trimmed);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load test fixtures", e);
        }
    }

    private static String readResource() {
        try (InputStream is = TestFixtures.class.getResourceAsStream(FIXTURES_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Test fixture not found: " + FIXTURES_PATH);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read test fixtures", e);
        }
    }
}
