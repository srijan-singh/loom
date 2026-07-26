package com.loom.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final String jdbcUrl;

    public DatabaseManager() {
        String path = System.getenv("LOOM_DB_PATH");
        if (path == null || path.isBlank()) {
            path = "./loom.db";
        }
        this.jdbcUrl = "jdbc:sqlite:" + path;
        initSchema();
    }

    /** Package-private constructor for tests — accepts an explicit file path. */
    DatabaseManager(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS skills (" +
                "  id TEXT PRIMARY KEY, name TEXT, description TEXT," +
                "  content TEXT, tags TEXT, created_at INTEGER, updated_at INTEGER" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS mcp_connections (" +
                "  id TEXT PRIMARY KEY, name TEXT, type TEXT," +
                "  config TEXT, status TEXT, created_at INTEGER" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS agent_definitions (" +
                "  id TEXT PRIMARY KEY, name TEXT, role_description TEXT," +
                "  skill_id TEXT, allowed_mcp_ids TEXT, created_at INTEGER, updated_at INTEGER" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS workflow_definitions (" +
                "  id TEXT PRIMARY KEY, name TEXT, type TEXT, created_by TEXT," +
                "  graph TEXT, created_at INTEGER, updated_at INTEGER" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS workspaces (" +
                "  id TEXT PRIMARY KEY, name TEXT, description TEXT, created_at INTEGER" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS workspace_workflows (" +
                "  workspace_id TEXT, workflow_definition_id TEXT" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS sessions (" +
                "  id TEXT PRIMARY KEY, workspace_id TEXT, workflow_definition_id TEXT," +
                "  status TEXT, started_at INTEGER, completed_at INTEGER" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS agent_executions (" +
                "  id TEXT PRIMARY KEY, session_id TEXT, node_id TEXT," +
                "  agent_definition_id TEXT, status TEXT, input_context TEXT," +
                "  output TEXT, report TEXT, started_at INTEGER, completed_at INTEGER" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS workspace_knowledge (" +
                "  id TEXT PRIMARY KEY, workspace_id TEXT, source_execution_id TEXT," +
                "  title TEXT, content TEXT, tags TEXT, created_at INTEGER" +
                ")"
            );
            log.info("Database schema initialized");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }
}
