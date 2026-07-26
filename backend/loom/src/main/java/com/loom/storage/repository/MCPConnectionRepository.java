package com.loom.storage.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.domain.MCPConnection;
import com.loom.domain.MCPStatus;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.*;

public class MCPConnectionRepository {

    private final DatabaseManager db;
    private final ObjectMapper mapper;

    public MCPConnectionRepository(DatabaseManager db) {
        this.db = db;
        this.mapper = new ObjectMapper();
    }

    public void save(MCPConnection conn) {
        String sql = "INSERT INTO mcp_connections (id, name, type, config, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "name=excluded.name, type=excluded.type, config=excluded.config, status=excluded.status";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conn.getId());
            ps.setString(2, conn.getName());
            ps.setString(3, conn.getType());
            ps.setString(4, mapToJson(conn.getConfig()));
            ps.setString(5, conn.getStatus() != null ? conn.getStatus().name() : null);
            ps.setLong(6, conn.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save MCPConnection", e);
        }
    }

    public Optional<MCPConnection> findById(String id) {
        String sql = "SELECT * FROM mcp_connections WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find MCPConnection by id", e);
        }
        return Optional.empty();
    }

    public List<MCPConnection> findAll() {
        String sql = "SELECT * FROM mcp_connections";
        List<MCPConnection> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all MCPConnections", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM mcp_connections WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete MCPConnection", e);
        }
    }

    private MCPConnection map(ResultSet rs) throws SQLException {
        MCPConnection conn = new MCPConnection();
        conn.setId(rs.getString("id"));
        conn.setName(rs.getString("name"));
        conn.setType(rs.getString("type"));
        conn.setConfig(jsonToMap(rs.getString("config")));
        String status = rs.getString("status");
        if (status != null) conn.setStatus(MCPStatus.valueOf(status));
        conn.setCreatedAt(rs.getLong("created_at"));
        return conn;
    }

    private String mapToJson(Map<String, Object> config) {
        if (config == null) return "{}";
        try {
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize config", e);
        }
    }

    private Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize config", e);
        }
    }
}
