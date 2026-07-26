package com.loom.storage.repository;

import com.loom.domain.Session;
import com.loom.domain.SessionStatus;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.*;

public class SessionRepository {

    private final DatabaseManager db;

    public SessionRepository(DatabaseManager db) {
        this.db = db;
    }

    public void save(Session session) {
        String sql = "INSERT INTO sessions (id, workspace_id, workflow_definition_id, status, started_at, completed_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "workspace_id=excluded.workspace_id, workflow_definition_id=excluded.workflow_definition_id, " +
                     "status=excluded.status, completed_at=excluded.completed_at";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.getId());
            ps.setString(2, session.getWorkspaceId());
            ps.setString(3, session.getWorkflowDefinitionId());
            ps.setString(4, session.getStatus() != null ? session.getStatus().name() : null);
            ps.setLong(5, session.getStartedAt());
            if (session.getCompletedAt() != null) ps.setLong(6, session.getCompletedAt());
            else ps.setNull(6, Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save Session", e);
        }
    }

    public Optional<Session> findById(String id) {
        String sql = "SELECT * FROM sessions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find Session by id", e);
        }
        return Optional.empty();
    }

    public List<Session> findAll() {
        String sql = "SELECT * FROM sessions";
        List<Session> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all Sessions", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM sessions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete Session", e);
        }
    }

    public List<Session> findByWorkspaceId(String workspaceId) {
        String sql = "SELECT * FROM sessions WHERE workspace_id = ?";
        List<Session> result = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find Sessions by workspaceId", e);
        }
        return result;
    }

    public List<Session> findByStatus(SessionStatus status) {
        String sql = "SELECT * FROM sessions WHERE status = ?";
        List<Session> result = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find Sessions by status", e);
        }
        return result;
    }

    private Session map(ResultSet rs) throws SQLException {
        Session s = new Session();
        s.setId(rs.getString("id"));
        s.setWorkspaceId(rs.getString("workspace_id"));
        s.setWorkflowDefinitionId(rs.getString("workflow_definition_id"));
        String status = rs.getString("status");
        if (status != null) s.setStatus(SessionStatus.valueOf(status));
        s.setStartedAt(rs.getLong("started_at"));
        long completedAt = rs.getLong("completed_at");
        if (!rs.wasNull()) s.setCompletedAt(completedAt);
        return s;
    }
}
