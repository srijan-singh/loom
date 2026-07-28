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

import com.loom.domain.AgentDefinition;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AgentRepository extends BaseRepository<AgentDefinition> {

    // column names
    private static final String COL_NAME = "name";
    private static final String COL_ROLE_DESCRIPTION = "role_description";
    private static final String COL_SKILL_ID = "skill_id";
    private static final String COL_ALLOWED_MCP_IDS = "allowed_mcp_ids";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_UPDATED_AT = "updated_at";

    // queries
    private static final String TABLE = "agent_definitions";

    private static final String FIND_BY_SKILL_ID = "SELECT * FROM agent_definitions WHERE skill_id = ?";

    private static final String SAVE  = upsert(
            TABLE,
            COL_ID,
            COL_NAME,
            COL_ROLE_DESCRIPTION,
            COL_SKILL_ID,
            COL_ALLOWED_MCP_IDS,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    public AgentRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(AgentDefinition agent) {
        db().update(SAVE, ps -> {
            ps.setString(1, agent.getId());
            ps.setString(2, agent.getName());
            ps.setString(3, agent.getRoleDescription());
            ps.setString(4, agent.getSkillId());
            ps.setString(5, toJsonList(agent.getAllowedMcpIds()));
            ps.setLong(6, agent.getCreatedAt());
            ps.setLong(7, agent.getUpdatedAt());
        });
    }

    public List<AgentDefinition> findBySkillId(String skillId) {
        return db().queryList(FIND_BY_SKILL_ID, ps -> ps.setString(1, skillId), this::map);
    }

    private AgentDefinition map(ResultSet rs) throws SQLException {
        AgentDefinition a = new AgentDefinition();
        a.setId(rs.getString(COL_ID));
        a.setName(rs.getString(COL_NAME));
        a.setRoleDescription(rs.getString(COL_ROLE_DESCRIPTION));
        a.setSkillId(rs.getString(COL_SKILL_ID));
        a.setAllowedMcpIds(fromJsonList(rs.getString(COL_ALLOWED_MCP_IDS)));
        a.setCreatedAt(rs.getLong(COL_CREATED_AT));
        a.setUpdatedAt(rs.getLong(COL_UPDATED_AT));
        return a;
    }

}
