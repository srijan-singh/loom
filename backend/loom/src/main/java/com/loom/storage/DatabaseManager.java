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

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class DatabaseManager {

    // ── SAM interfaces ────────────────────────────────────────────────────────

    /** Binds parameters onto a {@link PreparedStatement}. */
    @FunctionalInterface
    public interface ParamBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    /** Maps a single {@link ResultSet} row to a domain object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    // ── construction ──────────────────────────────────────────────────────────

    private static final String SCHEMA_PATH = "/db/schema/initialSchema.sql";
    private final String jdbcUrl;

    public DatabaseManager() {
        String path = System.getenv("LOOM_DB_PATH");
        if (path == null || path.isBlank()) {
            path = "./loom.db";
        }
        this.jdbcUrl = "jdbc:sqlite:" + path;
        initSchema();
    }

    /** Package-private constructor for tests — accepts an explicit file path. */
    DatabaseManager(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    // ── query helpers ─────────────────────────────────────────────────────────

    /**
     * Executes an INSERT / UPDATE / DELETE statement.
     *
     * @param sql    parameterised SQL string
     * @param binder lambda that binds {@code ?} parameters onto the statement
     */
    public void update(String sql, ParamBinder binder) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("update failed: " + sql, e);
        }
    }

    /**
     * Executes a SELECT that returns at most one row.
     *
     * @param sql    parameterised SQL string
     * @param binder lambda that binds {@code ?} parameters
     * @param mapper lambda that converts a {@link ResultSet} row to {@code T}
     * @return the mapped value, or {@link Optional#empty()} if no row matched
     */
    public <T> Optional<T> queryOne(String sql, ParamBinder binder, RowMapper<T> mapper) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapper.map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("queryOne failed: " + sql, e);
        }
        return Optional.empty();
    }

    /**
     * Executes a SELECT that returns zero or more rows.
     *
     * @param sql    parameterised SQL string
     * @param binder lambda that binds {@code ?} parameters
     * @param mapper lambda that converts each {@link ResultSet} row to {@code T}
     * @return list of mapped values (never {@code null})
     */
    public <T> List<T> queryList(String sql, ParamBinder binder, RowMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapper.map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("queryList failed: " + sql, e);
        }
        return result;
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            String schemaSQL = loadSchemaFromResource();

            // Split by semicolon and execute each statement.
            // Strip leading comment lines from each chunk before checking emptiness —
            // a chunk that starts with "-- comment\nCREATE TABLE..." is valid SQL.
            String[] statements = schemaSQL.split(";");
            for (String sql : statements) {
                String trimmed = sql.trim();
                // Remove leading comment lines so the DDL beneath them is not skipped
                trimmed = trimmed.replaceAll("(?m)^--[^\n]*\\n?", "").trim();
                if (!trimmed.isEmpty()) {
                    stmt.executeUpdate(trimmed);
                }
            }

            log.info("Database schema initialized successfully");

        } catch (SQLException e) {
            log.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database initialization failed", e);
        } catch (IOException e) {
            log.error("Failed to load schema file from resources", e);
            throw new RuntimeException("Schema file not found", e);
        }
    }

    private String loadSchemaFromResource() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_PATH)) {
            if (is == null) {
                throw new IOException("Schema resource not found: " + SCHEMA_PATH);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
