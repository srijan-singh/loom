-- Skills table
CREATE TABLE IF NOT EXISTS skills (
    id          TEXT    PRIMARY KEY,
    name        TEXT    NOT NULL,
    description TEXT,
    content     TEXT,
    tags        TEXT,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);

-- MCP Connections table
CREATE TABLE IF NOT EXISTS mcp_connections (
    id         TEXT    PRIMARY KEY,
    name       TEXT    NOT NULL,
    type       TEXT    NOT NULL,
    config     TEXT,
    status     TEXT,
    created_at INTEGER NOT NULL
);

-- Agent Definitions table
CREATE TABLE IF NOT EXISTS agent_definitions (
    id               TEXT    PRIMARY KEY,
    name             TEXT    NOT NULL,
    role_description TEXT,
    skill_id         TEXT,
    allowed_mcp_ids  TEXT,
    created_at       INTEGER NOT NULL,
    updated_at       INTEGER NOT NULL,
    FOREIGN KEY (skill_id) REFERENCES skills(id)
);

-- Workflow Definitions table
CREATE TABLE IF NOT EXISTS workflow_definitions (
    id         TEXT    PRIMARY KEY,
    name       TEXT,
    type       TEXT,
    created_by TEXT,
    graph      TEXT,
    created_at INTEGER,
    updated_at INTEGER
);

-- Workspaces table
CREATE TABLE IF NOT EXISTS workspaces (
    id          TEXT    PRIMARY KEY,
    name        TEXT,
    description TEXT,
    created_at  INTEGER
);

-- Workspace Workflows join table
CREATE TABLE IF NOT EXISTS workspace_workflows (
    workspace_id            TEXT    REFERENCES workspaces(id),
    workflow_definition_id  TEXT    REFERENCES workflow_definitions(id)
);

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id                     TEXT    PRIMARY KEY,
    workspace_id           TEXT    REFERENCES workspaces(id),
    workflow_definition_id TEXT    REFERENCES workflow_definitions(id),
    status                 TEXT,
    started_at             INTEGER,
    completed_at           INTEGER
);

-- Agent Executions table
CREATE TABLE IF NOT EXISTS agent_executions (
    id                  TEXT    PRIMARY KEY,
    session_id          TEXT    REFERENCES sessions(id),
    node_id             TEXT,
    agent_definition_id TEXT    REFERENCES agent_definitions(id),
    status              TEXT,
    input_context       TEXT,
    output              TEXT,
    report              TEXT,
    started_at          INTEGER NOT NULL,
    completed_at        INTEGER
);

-- Workspace Knowledge table
CREATE TABLE IF NOT EXISTS workspace_knowledge (
    id                   TEXT    PRIMARY KEY,
    workspace_id         TEXT    REFERENCES workspaces(id),
    source_execution_id  TEXT    REFERENCES agent_executions(id),
    title                TEXT,
    content              TEXT,
    tags                 TEXT,
    created_at           INTEGER
);
