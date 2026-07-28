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

import com.loom.domain.WorkspaceKnowledge;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WorkspaceKnowledgeRepository extends BaseRepository<WorkspaceKnowledge> {

    // column names
    private static final String COL_WORKSPACE_ID = "workspace_id";
    private static final String COL_SOURCE_EXECUTION_ID = "source_execution_id";
    private static final String COL_TITLE = "title";
    private static final String COL_CONTENT = "content";
    private static final String COL_TAGS = "tags";
    private static final String COL_CREATED_AT = "created_at";

    // queries
    private static final String TABLE = "workspace_knowledge";

    private static final String SAVE = upsert(
            TABLE,
            COL_ID,
            COL_WORKSPACE_ID,
            COL_SOURCE_EXECUTION_ID,
            COL_TITLE,
            COL_CONTENT,
            COL_TAGS,
            COL_CREATED_AT
    );

    public WorkspaceKnowledgeRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(WorkspaceKnowledge knowledge) {
        db().update(SAVE, ps -> {
            ps.setString(1, knowledge.getId());
            ps.setString(2, knowledge.getWorkspaceId());
            ps.setString(3, knowledge.getSourceExecutionId());
            ps.setString(4, knowledge.getTitle());
            ps.setString(5, knowledge.getContent());
            ps.setString(6, toJsonList(knowledge.getTags()));
            ps.setLong(7, knowledge.getCreatedAt());
        });
    }

    private WorkspaceKnowledge map(ResultSet rs) throws SQLException {
        WorkspaceKnowledge k = new WorkspaceKnowledge();
        k.setId(rs.getString(COL_ID));
        k.setWorkspaceId(rs.getString(COL_WORKSPACE_ID));
        k.setSourceExecutionId(rs.getString(COL_SOURCE_EXECUTION_ID));
        k.setTitle(rs.getString(COL_TITLE));
        k.setContent(rs.getString(COL_CONTENT));
        k.setTags(fromJsonList(rs.getString(COL_TAGS)));
        k.setCreatedAt(rs.getLong(COL_CREATED_AT));
        return k;
    }

}
