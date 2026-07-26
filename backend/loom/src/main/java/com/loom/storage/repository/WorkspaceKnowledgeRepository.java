package com.loom.storage.repository;

import com.loom.domain.WorkspaceKnowledge;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.*;

public class WorkspaceKnowledgeRepository {

    private final DatabaseManager db;

    public WorkspaceKnowledgeRepository(DatabaseManager db) {
        this.db = db;
    }

    public void save(WorkspaceKnowledge knowledge) {
        String sql = "INSERT INTO workspace_knowledge (id, workspace_id, source_execution_id, title, content, tags, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "workspace_id=excluded.workspace_id, source_execution_id=excluded.source_execution_id, " +
                     "title=excluded.title, content=excluded.content, tags=excluded.tags";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, knowledge.getId());
            ps.setString(2, knowledge.getWorkspaceId());
            ps.setString(3, knowledge.getSourceExecutionId());
            ps.setString(4, knowledge.getTitle());
            ps.setString(5, knowledge.getContent());
            ps.setString(6, tagsToString(knowledge.getTags()));
            ps.setLong(7, knowledge.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save WorkspaceKnowledge", e);
        }
    }

    public Optional<WorkspaceKnowledge> findById(String id) {
        String sql = "SELECT * FROM workspace_knowledge WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find WorkspaceKnowledge by id", e);
        }
        return Optional.empty();
    }

    public List<WorkspaceKnowledge> findAll() {
        String sql = "SELECT * FROM workspace_knowledge";
        List<WorkspaceKnowledge> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all WorkspaceKnowledge", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM workspace_knowledge WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete WorkspaceKnowledge", e);
        }
    }

    private WorkspaceKnowledge map(ResultSet rs) throws SQLException {
        WorkspaceKnowledge k = new WorkspaceKnowledge();
        k.setId(rs.getString("id"));
        k.setWorkspaceId(rs.getString("workspace_id"));
        k.setSourceExecutionId(rs.getString("source_execution_id"));
        k.setTitle(rs.getString("title"));
        k.setContent(rs.getString("content"));
        k.setTags(stringToTags(rs.getString("tags")));
        k.setCreatedAt(rs.getLong("created_at"));
        return k;
    }

    private String tagsToString(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return String.join(",", tags);
    }

    private List<String> stringToTags(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }
}
