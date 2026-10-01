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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.loom.llm.LLMConnection;
import com.loom.storage.DatabaseManager;

public class LLMConnectionRepository extends BaseRepository<LLMConnection> {

    // column names
    private static final String COL_NAME = "name";
    private static final String COL_BASE_URL = "base_url";
    private static final String COL_MODEL = "model";
    private static final String COL_API_KEY = "api_key";
    private static final String COL_IS_DEFAULT = "is_default";
    private static final String COL_CREATED_AT = "created_at";

    // queries
    private static final String TABLE = "llm_connections";

    private static final String SAVE =
            upsert(
                    TABLE,
                    COL_ID,
                    COL_NAME,
                    COL_BASE_URL,
                    COL_MODEL,
                    COL_API_KEY,
                    COL_IS_DEFAULT,
                    COL_CREATED_AT);

    private static final String FIND_DEFAULT =
            "SELECT * FROM " + TABLE + " WHERE " + COL_IS_DEFAULT + " = 1 LIMIT 1";

    /**
     * Single-statement atomic default swap: sets is_default = 1 on the given id, 0 on all others.
     * Avoids a two-statement window where every row temporarily has is_default = 0.
     */
    private static final String SET_DEFAULT_ATOMIC =
            "UPDATE "
                    + TABLE
                    + " SET "
                    + COL_IS_DEFAULT
                    + " = CASE WHEN "
                    + COL_ID
                    + " = ? THEN 1 ELSE 0 END";

    public LLMConnectionRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    /** Insert or replace. */
    public void save(LLMConnection conn) {
        db().update(
                        SAVE,
                        ps -> {
                            ps.setString(1, conn.getId());
                            ps.setString(2, conn.getName());
                            ps.setString(3, conn.getBaseUrl());
                            ps.setString(4, conn.getModel());
                            ps.setString(5, conn.getApiKey());
                            ps.setInt(6, conn.isDefault() ? 1 : 0);
                            ps.setLong(7, conn.getCreatedAt());
                        });
    }

    /** Returns the connection with is_default = 1, or empty. */
    public Optional<LLMConnection> findDefault() {
        return db().queryOne(FIND_DEFAULT, ps -> {}, this::map);
    }

    /** Atomically sets is_default = 1 on the given id and 0 on all other rows. */
    public void setDefault(String id) {
        db().update(SET_DEFAULT_ATOMIC, ps -> ps.setString(1, id));
    }

    private LLMConnection map(ResultSet rs) throws SQLException {
        LLMConnection conn = new LLMConnection();
        conn.setId(rs.getString(COL_ID));
        conn.setName(rs.getString(COL_NAME));
        conn.setBaseUrl(rs.getString(COL_BASE_URL));
        conn.setModel(rs.getString(COL_MODEL));
        conn.setApiKey(rs.getString(COL_API_KEY));
        conn.setDefault(rs.getInt(COL_IS_DEFAULT) == 1);
        conn.setCreatedAt(rs.getLong(COL_CREATED_AT));
        return conn;
    }
}
