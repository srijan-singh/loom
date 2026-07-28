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

package com.loom.storage.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides the three queries every repository shares:
 * {@link #findById}, {@link #findAll}, and {@link #delete}.
 *
 * <p>Also exposes a shared {@link ObjectMapper} and JSON helpers
 * ({@link #toJsonList} / {@link #fromJsonList} / {@link #toJsonMap} / {@link #fromJsonMap})
 * for repositories that serialize list or map columns.
 *
 * <p>Subclasses call {@code super(db, table)} then immediately call
 * {@code setMapper(this::map)} in their constructor, once their own
 * {@code map()} method is in scope.
 *
 * @param <T> the domain entity type
 */
abstract class BaseRepository<T> {

    /** Shared column name for the primary key used by {@link #findById} and {@link #delete}. */
    protected static final String COL_ID = "id";

    /** Shared Jackson mapper — thread-safe after construction, reused across all subclasses. */
    protected static final ObjectMapper JSON = new ObjectMapper();

    private final DatabaseManager db;
    private final String findByIdSql;
    private final String findAllSql;
    private final String deleteSql;
    private DatabaseManager.RowMapper<T> mapper;

    protected BaseRepository(DatabaseManager db, String table) {
        this.db = db;
        this.findByIdSql = "SELECT * FROM " + table + " WHERE " + COL_ID + " = ?";
        this.findAllSql = "SELECT * FROM " + table;
        this.deleteSql = "DELETE FROM " + table + " WHERE " + COL_ID + " = ?";
    }

    /**
     * Must be called as the last line of every subclass constructor:
     * <pre>{@code setMapper(this::map);}</pre>
     */
    protected final void setMapper(DatabaseManager.RowMapper<T> mapper) {
        this.mapper = mapper;
    }

    public Optional<T> findById(String id) {
        return db.queryOne(findByIdSql, ps -> ps.setString(1, id), mapper);
    }

    public List<T> findAll() {
        return db.queryList(findAllSql, ps -> {}, mapper);
    }

    public void delete(String id) {
        db.update(deleteSql, ps -> ps.setString(1, id));
    }

    /** Exposes {@link DatabaseManager} to subclasses for {@code save()} and custom queries. */
    protected DatabaseManager db() {
        return db;
    }

    /**
     * Serializes a {@code List<String>} to a JSON array string.
     * Returns {@code "[]"} for null or empty input.
     */
    protected static String toJsonList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try { return JSON.writeValueAsString(list); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize JSON list", e); }
    }

    /**
     * Deserializes a JSON array string to a {@code List<String>}.
     * Returns an empty list for null or blank input.
     */
    protected static List<String> fromJsonList(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        try { return JSON.readValue(raw, new TypeReference<List<String>>() {}); }
        catch (Exception e) { throw new RuntimeException("Failed to deserialize JSON list", e); }
    }

    /**
     * Serializes a {@code Map<String, Object>} to a JSON object string.
     * Returns {@code "{}"} for null input.
     */
    protected static String toJsonMap(Map<String, Object> map) {
        if (map == null) return "{}";
        try { return JSON.writeValueAsString(map); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize JSON map", e); }
    }

    /**
     * Deserializes a JSON object string to a {@code Map<String, Object>}.
     * Returns an empty map for null or blank input.
     */
    protected static Map<String, Object> fromJsonMap(String raw) {
        if (raw == null || raw.isBlank()) return new HashMap<>();
        try { return JSON.readValue(raw, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { throw new RuntimeException("Failed to deserialize JSON map", e); }
    }

    /**
     * Builds a SQLite upsert statement for the given table and columns.
     *
     * <p>The first column is always {@code id} and is excluded from the
     * {@code ON CONFLICT … DO UPDATE SET} clause (it never changes on conflict).
     * All remaining columns are included in the update.
     *
     * <p>Example — {@code upsert("skills", "id","name","tags","created_at","updated_at")}
     * produces:
     * <pre>{@code
     * INSERT INTO skills (id, name, tags, created_at, updated_at)
     * VALUES (?, ?, ?, ?, ?)
     * ON CONFLICT(id) DO UPDATE SET
     * name=excluded.name, tags=excluded.tags, updated_at=excluded.updated_at
     * }</pre>
     *
     * @param table the table name
     * @param cols  column names in bind order; first column must be {@code "id"}
     */
    protected static String upsert(String table, String... cols) {
        if (cols == null || cols.length == 0) {
            throw new IllegalArgumentException("upsert: cols must not be empty");
        }
        if (!"id".equals(cols[0])) {
            throw new IllegalArgumentException("upsert: first column must be \"id\", got: " + cols[0]);
        }
        String colList    = String.join(", ", cols);
        String placeholders = "?, ".repeat(cols.length - 1) + "?";

        StringBuilder updateCols = new StringBuilder();
        for (int i = 1; i < cols.length; i++) {          // skip cols[0] == "id"
            if (i > 1) updateCols.append(", ");
            updateCols.append(cols[i]).append("=excluded.").append(cols[i]);
        }

        return "INSERT INTO " + table + " (" + colList + ") " +
               "VALUES (" + placeholders + ") " +
               "ON CONFLICT(id) DO UPDATE SET " + updateCols;
    }

    /**
     * Reads a nullable {@code LONG} column from the result set.
     *
     * <p>SQLite stores nullable integers as {@code NULL}; {@link ResultSet#getLong}
     * returns {@code 0} for {@code NULL} and sets the {@code wasNull} flag.
     * This helper checks that flag and returns {@code null} instead of {@code 0}.
     *
     * @param rs  the current result set row
     * @param col column name
     * @return the long value, or {@code null} if the column was SQL {@code NULL}
     */
    protected static Long getLongOrNull(ResultSet rs, String col) throws SQLException {
        long value = rs.getLong(col);
        return rs.wasNull() ? null : value;
    }
}
