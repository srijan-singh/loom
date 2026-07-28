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

import com.loom.domain.AgentExecution;
import com.loom.domain.AgentExecutionStatus;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class AgentExecutionRepository extends BaseRepository<AgentExecution> {

    // column names
    private static final String COL_SESSION_ID = "session_id";
    private static final String COL_NODE_ID = "node_id";
    private static final String COL_AGENT_DEFINITION_ID = "agent_definition_id";
    private static final String COL_STATUS = "status";
    private static final String COL_INPUT_CONTEXT = "input_context";
    private static final String COL_OUTPUT = "output";
    private static final String COL_REPORT = "report";
    private static final String COL_STARTED_AT = "started_at";
    private static final String COL_COMPLETED_AT = "completed_at";

    // queries
    private static final String TABLE = "agent_executions";

    private static final String SAVE  = upsert(
            TABLE,
            COL_ID,
            COL_SESSION_ID,
            COL_NODE_ID,
            COL_AGENT_DEFINITION_ID,
            COL_STATUS,
            COL_INPUT_CONTEXT,
            COL_OUTPUT,
            COL_REPORT,
            COL_STARTED_AT,
            COL_COMPLETED_AT
    );

    public AgentExecutionRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(AgentExecution exec) {
        db().update(SAVE, ps -> {
            ps.setString(1, exec.getId());
            ps.setString(2, exec.getSessionId());
            ps.setString(3, exec.getNodeId());
            ps.setString(4, exec.getAgentDefinitionId());
            ps.setString(5, exec.getStatus() != null ? exec.getStatus().name() : null);
            ps.setString(6, exec.getInputContext());
            ps.setString(7, exec.getOutput());
            ps.setString(8, exec.getReport());
            ps.setLong(9, exec.getStartedAt());
            if (exec.getCompletedAt() != null) ps.setLong(10, exec.getCompletedAt());
            else ps.setNull(10, Types.INTEGER);
        });
    }

    private AgentExecution map(ResultSet rs) throws SQLException {
        AgentExecution e = new AgentExecution();
        e.setId(rs.getString(COL_ID));
        e.setSessionId(rs.getString(COL_SESSION_ID));
        e.setNodeId(rs.getString(COL_NODE_ID));
        e.setAgentDefinitionId(rs.getString(COL_AGENT_DEFINITION_ID));
        String status = rs.getString(COL_STATUS);
        if (status != null) e.setStatus(AgentExecutionStatus.valueOf(status));
        e.setInputContext(rs.getString(COL_INPUT_CONTEXT));
        e.setOutput(rs.getString(COL_OUTPUT));
        e.setReport(rs.getString(COL_REPORT));
        e.setStartedAt(rs.getLong(COL_STARTED_AT));
        e.setCompletedAt(getLongOrNull(rs, COL_COMPLETED_AT));
        return e;
    }
}
