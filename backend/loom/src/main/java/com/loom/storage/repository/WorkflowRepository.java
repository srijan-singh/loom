package com.loom.storage.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.*;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.*;

public class WorkflowRepository {

    private final DatabaseManager db;
    private final ObjectMapper mapper;

    public WorkflowRepository(DatabaseManager db) {
        this.db = db;
        this.mapper = new ObjectMapper();
    }

    public void save(WorkflowDefinition wf) {
        String sql = "INSERT INTO workflow_definitions (id, name, type, created_by, graph, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "name=excluded.name, type=excluded.type, created_by=excluded.created_by, " +
                     "graph=excluded.graph, updated_at=excluded.updated_at";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, wf.getId());
            ps.setString(2, wf.getName());
            ps.setString(3, wf.getType() != null ? wf.getType().name() : null);
            ps.setString(4, wf.getCreatedBy() != null ? wf.getCreatedBy().name() : null);
            ps.setString(5, graphToJson(wf));
            ps.setLong(6, wf.getCreatedAt());
            ps.setLong(7, wf.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save WorkflowDefinition", e);
        }
    }

    public Optional<WorkflowDefinition> findById(String id) {
        String sql = "SELECT * FROM workflow_definitions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find WorkflowDefinition by id", e);
        }
        return Optional.empty();
    }

    public List<WorkflowDefinition> findAll() {
        String sql = "SELECT * FROM workflow_definitions";
        List<WorkflowDefinition> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all WorkflowDefinitions", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM workflow_definitions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete WorkflowDefinition", e);
        }
    }

    public List<WorkflowDefinition> findByType(WorkflowType type) {
        String sql = "SELECT * FROM workflow_definitions WHERE type = ?";
        List<WorkflowDefinition> result = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find WorkflowDefinitions by type", e);
        }
        return result;
    }

    private WorkflowDefinition map(ResultSet rs) throws SQLException {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setId(rs.getString("id"));
        wf.setName(rs.getString("name"));
        String type = rs.getString("type");
        if (type != null) wf.setType(WorkflowType.valueOf(type));
        String createdBy = rs.getString("created_by");
        if (createdBy != null) wf.setCreatedBy(WorkflowCreatedBy.valueOf(createdBy));
        wf.setCreatedAt(rs.getLong("created_at"));
        wf.setUpdatedAt(rs.getLong("updated_at"));
        applyGraph(wf, rs.getString("graph"));
        return wf;
    }

    private String graphToJson(WorkflowDefinition wf) {
        try {
            Map<String, Object> graph = new HashMap<>();
            graph.put("nodes", wf.getNodes() != null ? wf.getNodes() : Collections.emptyList());
            graph.put("edges", wf.getEdges() != null ? wf.getEdges() : Collections.emptyList());
            return mapper.writeValueAsString(graph);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize workflow graph", e);
        }
    }

    private void applyGraph(WorkflowDefinition wf, String json) {
        if (json == null || json.isBlank()) return;
        try {
            Map<?, ?> graph = mapper.readValue(json, Map.class);
            List<WorkflowNode> nodes = mapper.convertValue(
                graph.get("nodes"),
                mapper.getTypeFactory().constructCollectionType(List.class, WorkflowNode.class)
            );
            List<WorkflowEdge> edges = mapper.convertValue(
                graph.get("edges"),
                mapper.getTypeFactory().constructCollectionType(List.class, WorkflowEdge.class)
            );
            wf.setNodes(nodes);
            wf.setEdges(edges);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize workflow graph", e);
        }
    }
}
