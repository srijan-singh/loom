package com.loom.storage.repository;

import com.loom.domain.Skill;
import com.loom.storage.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SkillRepository {

    private final DatabaseManager db;

    public SkillRepository(DatabaseManager db) {
        this.db = db;
    }

    public void save(Skill skill) {
        String sql = "INSERT INTO skills (id, name, description, content, tags, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "name=excluded.name, description=excluded.description, content=excluded.content, " +
                     "tags=excluded.tags, updated_at=excluded.updated_at";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skill.getId());
            ps.setString(2, skill.getName());
            ps.setString(3, skill.getDescription());
            ps.setString(4, skill.getContent());
            ps.setString(5, tagsToString(skill.getTags()));
            ps.setLong(6, skill.getCreatedAt());
            ps.setLong(7, skill.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save Skill", e);
        }
    }

    public Optional<Skill> findById(String id) {
        String sql = "SELECT * FROM skills WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find Skill by id", e);
        }
        return Optional.empty();
    }

    public List<Skill> findAll() {
        String sql = "SELECT * FROM skills";
        List<Skill> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all Skills", e);
        }
        return result;
    }

    public void delete(String id) {
        String sql = "DELETE FROM skills WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete Skill", e);
        }
    }

    public List<Skill> findByTag(String tag) {
        String sql = "SELECT * FROM skills WHERE (',' || tags || ',') LIKE ?";
        List<Skill> result = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%," + tag + ",%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find Skills by tag", e);
        }
        return result;
    }

    private Skill map(ResultSet rs) throws SQLException {
        Skill s = new Skill();
        s.setId(rs.getString("id"));
        s.setName(rs.getString("name"));
        s.setDescription(rs.getString("description"));
        s.setContent(rs.getString("content"));
        s.setTags(stringToTags(rs.getString("tags")));
        s.setCreatedAt(rs.getLong("created_at"));
        s.setUpdatedAt(rs.getLong("updated_at"));
        return s;
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
