package com.loom.storage.repository;

import com.loom.domain.AgentDefinition;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.*;

public class AgentRepository {

    private final DatabaseManager db;

    public AgentRepository(DatabaseManager db) {
        this.db = db;
    }

    public void save(AgentDefinition agent) {
        String sql = "INSERT INTO agent_definitions (id, name, role_description, skill_id, allowed_mcp_ids, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "name=excluded.name, role_description=excluded.role_description, skill_id=excluded.skill_id, " +
                     "allowed_mcp_ids=excluded.allowed_mcp_ids, updated_at=excluded.updated_at";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agent.getId());
            ps.setString(2, agent.getName());
            ps.setString(3, agent.getRoleDescription());
            ps.setString(4, agent.getSkillId());
            ps.setString(5, listToString(agent.getAllowedMcpIds()));
            ps.setLong(6, agent.getCreatedAt());
            ps.setLong(7, agent.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save AgentDefinition", e);
        }
    }

    public Optional<AgentDefinition> findById(String id) {
        String sql = "SELECT * FROM agent_definitions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find AgentDefinition by id", e);
        }
        return Optional.empty();
    }

    public List<AgentDefinition> findAll() {
        String sql = "SELECT * FROM agent_definitions";
        List<AgentDefinition> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all AgentDefinitions", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM agent_definitions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete AgentDefinition", e);
        }
    }

    public List<AgentDefinition> findBySkillId(String skillId) {
        String sql = "SELECT * FROM agent_definitions WHERE skill_id = ?";
        List<AgentDefinition> result = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find AgentDefinitions by skillId", e);
        }
        return result;
    }

    private AgentDefinition map(ResultSet rs) throws SQLException {
        AgentDefinition a = new AgentDefinition();
        a.setId(rs.getString("id"));
        a.setName(rs.getString("name"));
        a.setRoleDescription(rs.getString("role_description"));
        a.setSkillId(rs.getString("skill_id"));
        a.setAllowedMcpIds(stringToList(rs.getString("allowed_mcp_ids")));
        a.setCreatedAt(rs.getLong("created_at"));
        a.setUpdatedAt(rs.getLong("updated_at"));
        return a;
    }

    private String listToString(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(",", list);
    }

    private List<String> stringToList(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }
}
