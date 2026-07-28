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

import com.loom.domain.*;
import com.loom.storage.DatabaseManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowRepository extends BaseRepository<WorkflowDefinition> {

    // column names
    private static final String COL_NAME = "name";
    private static final String COL_TYPE = "type";
    private static final String COL_CREATED_BY = "created_by";
    private static final String COL_GRAPH = "graph";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_UPDATED_AT = "updated_at";

    // queries
    private static final String TABLE = "workflow_definitions";

    private static final String FIND_BY_TYPE = "SELECT * FROM workflow_definitions WHERE type = ?";

    private static final String SAVE = upsert(
            TABLE,
            COL_ID,
            COL_NAME,
            COL_TYPE,
            COL_CREATED_BY,
            COL_GRAPH,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    public WorkflowRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(WorkflowDefinition wf) {
        db().update(SAVE, ps -> {
            ps.setString(1, wf.getId());
            ps.setString(2, wf.getName());
            ps.setString(3, wf.getType() != null ? wf.getType().name() : null);
            ps.setString(4, wf.getCreatedBy() != null ? wf.getCreatedBy().name() : null);
            ps.setString(5, graphToJson(wf));
            ps.setLong(6, wf.getCreatedAt());
            ps.setLong(7, wf.getUpdatedAt());
        });
    }

    public List<WorkflowDefinition> findByType(WorkflowType type) {
        return db().queryList(FIND_BY_TYPE, ps -> ps.setString(1, type.name()), this::map);
    }

    private WorkflowDefinition map(java.sql.ResultSet rs) throws java.sql.SQLException {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setId(rs.getString(COL_ID));
        wf.setName(rs.getString(COL_NAME));
        String type = rs.getString(COL_TYPE);
        if (type != null) wf.setType(WorkflowType.valueOf(type));
        String createdBy = rs.getString(COL_CREATED_BY);
        if (createdBy != null) wf.setCreatedBy(WorkflowCreatedBy.valueOf(createdBy));
        wf.setCreatedAt(rs.getLong(COL_CREATED_AT));
        wf.setUpdatedAt(rs.getLong(COL_UPDATED_AT));
        applyGraph(wf, rs.getString(COL_GRAPH));
        return wf;
    }

    private String graphToJson(WorkflowDefinition wf) {
        try {
            Map<String, Object> graph = new HashMap<>();
            graph.put("nodes", wf.getNodes() != null ? wf.getNodes() : Collections.emptyList());
            graph.put("edges", wf.getEdges() != null ? wf.getEdges() : Collections.emptyList());
            return JSON.writeValueAsString(graph);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize workflow graph", e);
        }
    }

    private void applyGraph(WorkflowDefinition wf, String json) {
        if (json == null || json.isBlank()) return;
        try {
            Map<?, ?> graph = JSON.readValue(json, Map.class);
            Object nodes = graph.get("nodes");
            Object edges = graph.get("edges");
            wf.setNodes(JSON.convertValue(nodes != null ? nodes : Collections.emptyList(),
                    JSON.getTypeFactory().constructCollectionType(List.class, WorkflowNode.class)));
            wf.setEdges(JSON.convertValue(edges != null ? edges : Collections.emptyList(),
                    JSON.getTypeFactory().constructCollectionType(List.class, WorkflowEdge.class)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize workflow graph", e);
        }
    }
}
