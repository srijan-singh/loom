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

import com.loom.domain.MCPConnection;
import com.loom.domain.MCPStatus;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MCPConnectionRepository extends BaseRepository<MCPConnection> {

    // column names
    private static final String COL_NAME = "name";
    private static final String COL_TYPE = "type";
    private static final String COL_CONFIG = "config";
    private static final String COL_STATUS = "status";
    private static final String COL_CREATED_AT = "created_at";

    // queries
    private static final String TABLE = "mcp_connections";

    private static final String SAVE  = upsert(
            TABLE,
            COL_ID,
            COL_NAME,
            COL_TYPE,
            COL_CONFIG,
            COL_STATUS,
            COL_CREATED_AT
    );

    public MCPConnectionRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(MCPConnection conn) {
        db().update(SAVE, ps -> {
            ps.setString(1, conn.getId());
            ps.setString(2, conn.getName());
            ps.setString(3, conn.getType());
            ps.setString(4, toJsonMap(conn.getConfig()));
            ps.setString(5, conn.getStatus() != null ? conn.getStatus().name() : null);
            ps.setLong(6, conn.getCreatedAt());
        });
    }

    private MCPConnection map(ResultSet rs) throws SQLException {
        MCPConnection conn = new MCPConnection();
        conn.setId(rs.getString(COL_ID));
        conn.setName(rs.getString(COL_NAME));
        conn.setType(rs.getString(COL_TYPE));
        conn.setConfig(fromJsonMap(rs.getString(COL_CONFIG)));
        String status = rs.getString(COL_STATUS);
        if (status != null) conn.setStatus(MCPStatus.valueOf(status));
        conn.setCreatedAt(rs.getLong(COL_CREATED_AT));
        return conn;
    }
}
