package com.loom.storage.repository;

import com.loom.domain.Workspace;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.*;

public class WorkspaceRepository {

    private final DatabaseManager db;

    public WorkspaceRepository(DatabaseManager db) {
        this.db = db;
    }

    public void save(Workspace workspace) {
        String sql = "INSERT INTO workspaces (id, name, description, created_at) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "name=excluded.name, description=excluded.description";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workspace.getId());
            ps.setString(2, workspace.getName());
            ps.setString(3, workspace.getDescription());
            ps.setLong(4, workspace.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save Workspace", e);
        }
    }

    public Optional<Workspace> findById(String id) {
        String sql = "SELECT * FROM workspaces WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find Workspace by id", e);
        }
        return Optional.empty();
    }

    public List<Workspace> findAll() {
        String sql = "SELECT * FROM workspaces";
        List<Workspace> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all Workspaces", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM workspaces WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete Workspace", e);
        }
    }

    /**
     * Returns all workspaces that are linked to the given workflow definition id
     * via the workspace_workflows join table.
     */
    public List<Workspace> findByWorkflowId(String workflowDefinitionId) {
        String sql = "SELECT w.* FROM workspaces w " +
                     "JOIN workspace_workflows ww ON w.id = ww.workspace_id " +
                     "WHERE ww.workflow_definition_id = ?";
        List<Workspace> result = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowDefinitionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find Workspaces by workflowId", e);
        }
        return result;
    }

    private Workspace map(ResultSet rs) throws SQLException {
        Workspace w = new Workspace();
        w.setId(rs.getString("id"));
        w.setName(rs.getString("name"));
        w.setDescription(rs.getString("description"));
        w.setCreatedAt(rs.getLong("created_at"));
        return w;
    }
}
