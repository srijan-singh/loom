package com.loom.storage.repository;

import com.loom.domain.AgentExecution;
import com.loom.domain.AgentExecutionStatus;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.*;

public class AgentExecutionRepository {

    private final DatabaseManager db;

    public AgentExecutionRepository(DatabaseManager db) {
        this.db = db;
    }

    public void save(AgentExecution exec) {
        String sql = "INSERT INTO agent_executions " +
                     "(id, session_id, node_id, agent_definition_id, status, input_context, output, report, started_at, completed_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "status=excluded.status, input_context=excluded.input_context, output=excluded.output, " +
                     "report=excluded.report, completed_at=excluded.completed_at";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
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
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save AgentExecution", e);
        }
    }

    public Optional<AgentExecution> findById(String id) {
        String sql = "SELECT * FROM agent_executions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find AgentExecution by id", e);
        }
        return Optional.empty();
    }

    public List<AgentExecution> findAll() {
        String sql = "SELECT * FROM agent_executions";
        List<AgentExecution> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all AgentExecutions", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM agent_executions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete AgentExecution", e);
        }
    }

    private AgentExecution map(ResultSet rs) throws SQLException {
        AgentExecution e = new AgentExecution();
        e.setId(rs.getString("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setNodeId(rs.getString("node_id"));
        e.setAgentDefinitionId(rs.getString("agent_definition_id"));
        String status = rs.getString("status");
        if (status != null) e.setStatus(AgentExecutionStatus.valueOf(status));
        e.setInputContext(rs.getString("input_context"));
        e.setOutput(rs.getString("output"));
        e.setReport(rs.getString("report"));
        e.setStartedAt(rs.getLong("started_at"));
        long completedAt = rs.getLong("completed_at");
        if (!rs.wasNull()) e.setCompletedAt(completedAt);
        return e;
    }
}
