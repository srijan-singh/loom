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

import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class SessionRepository extends BaseRepository<Session> {

    // column names
    private static final String COL_WORKSPACE_ID = "workspace_id";
    private static final String COL_WORKFLOW_DEFINITION_ID = "workflow_definition_id";
    private static final String COL_STATUS = "status";
    private static final String COL_STARTED_AT = "started_at";
    private static final String COL_COMPLETED_AT = "completed_at";

    // queries
    private static final String TABLE = "sessions";

    private static final String FIND_BY_WORKSPACE_ID = "SELECT * FROM sessions WHERE workspace_id = ?";

    private static final String FIND_BY_STATUS = "SELECT * FROM sessions WHERE status = ?";

    private static final String SAVE = upsert(
            TABLE,
            COL_ID,
            COL_WORKSPACE_ID,
            COL_WORKFLOW_DEFINITION_ID,
            COL_STATUS,
            COL_STARTED_AT,
            COL_COMPLETED_AT
    );

    public SessionRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(Session session) {
        db().update(SAVE, ps -> {
            ps.setString(1, session.getId());
            ps.setString(2, session.getWorkspaceId());
            ps.setString(3, session.getWorkflowDefinitionId());
            ps.setString(4, session.getStatus() != null ? session.getStatus().name() : null);
            ps.setLong(5, session.getStartedAt());
            if (session.getCompletedAt() != null) ps.setLong(6, session.getCompletedAt());
            else ps.setNull(6, Types.BIGINT);
        });
    }

    public List<Session> findByWorkspaceId(String workspaceId) {
        return db().queryList(FIND_BY_WORKSPACE_ID, ps -> ps.setString(1, workspaceId), this::map);
    }

    public List<Session> findByStatus(SessionStatus status) {
        return db().queryList(FIND_BY_STATUS, ps -> ps.setString(1, status.name()), this::map);
    }

    private Session map(ResultSet rs) throws SQLException {
        Session s = new Session();
        s.setId(rs.getString(COL_ID));
        s.setWorkspaceId(rs.getString(COL_WORKSPACE_ID));
        s.setWorkflowDefinitionId(rs.getString(COL_WORKFLOW_DEFINITION_ID));
        String status = rs.getString(COL_STATUS);
        if (status != null) s.setStatus(SessionStatus.valueOf(status));
        s.setStartedAt(rs.getLong(COL_STARTED_AT));
        s.setCompletedAt(getLongOrNull(rs, COL_COMPLETED_AT));
        return s;
    }
}
