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

import com.loom.domain.Workspace;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WorkspaceRepository extends BaseRepository<Workspace> {

    // column names
    private static final String COL_NAME = "name";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_CREATED_AT = "created_at";

    // queries
    private static final String TABLE = "workspaces";

    private static final String FIND_BY_WORKFLOW = String.join(" ",
            "SELECT w.* FROM workspaces w",
            " JOIN workspace_workflows ww ON w.id = ww.workspace_id",
            " WHERE ww.workflow_definition_id = ?"
            );

    private static final String SAVE = upsert(
            TABLE,
            COL_ID,
            COL_NAME,
            COL_DESCRIPTION,
            COL_CREATED_AT
    );

    public WorkspaceRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(Workspace workspace) {
        db().update(SAVE, ps -> {
            ps.setString(1, workspace.getId());
            ps.setString(2, workspace.getName());
            ps.setString(3, workspace.getDescription());
            ps.setLong(4, workspace.getCreatedAt());
        });
    }

    /** Returns all workspaces linked to the given workflow via the join table. */
    public List<Workspace> findByWorkflowId(String workflowDefinitionId) {
        return db().queryList(FIND_BY_WORKFLOW, ps -> ps.setString(1, workflowDefinitionId), this::map);
    }

    private Workspace map(ResultSet rs) throws SQLException {
        Workspace w = new Workspace();
        w.setId(rs.getString(COL_ID));
        w.setName(rs.getString(COL_NAME));
        w.setDescription(rs.getString(COL_DESCRIPTION));
        w.setCreatedAt(rs.getLong(COL_CREATED_AT));
        return w;
    }
}
