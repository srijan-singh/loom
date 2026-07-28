-- Test fixture data — fixed IDs so tests can reference them as constants.
-- Loaded once in @BeforeAll via TestFixtures.load(DatabaseManager).
-- JSON columns use the same serialization format as the repository layer
-- (Jackson, camelCase field names).

INSERT INTO skills (id, name, description, content, tags, created_at, updated_at) VALUES
  ('skill-web-research', 'Web Research', 'Searches the web',
   '# Web Research\nUse Tavily to search.',
   '["search","web","research"]',
   1700000000000, 1700000000000);

INSERT INTO mcp_connections (id, name, type, config, status, created_at) VALUES
  ('mcp-brave', 'Brave Search', 'stdio',
   '{"command":"npx","args":["-y","brave-search-mcp"]}',
   'CONNECTED',
   1700000000000);

INSERT INTO agent_definitions (id, name, role_description, skill_id, allowed_mcp_ids, created_at, updated_at) VALUES
  ('agent-researcher', 'Researcher', 'Performs web research tasks',
   'skill-web-research',
   '["mcp-brave"]',
   1700000000000, 1700000000000);

INSERT INTO workflow_definitions (id, name, type, created_by, graph, created_at, updated_at) VALUES
  ('wf-research-pipeline', 'Research Pipeline', 'CHAIN', 'USER',
   '{"nodes":[{"id":"node-start","label":"Start","nodeType":"START","agentDefinitionId":null,"positionX":0.0,"positionY":0.0},{"id":"node-worker","label":"Research","nodeType":"WORKER","agentDefinitionId":"agent-researcher","positionX":200.0,"positionY":0.0},{"id":"node-end","label":"End","nodeType":"END","agentDefinitionId":null,"positionX":400.0,"positionY":0.0}],"edges":[{"id":"edge-1","fromNodeId":"node-start","toNodeId":"node-worker","condition":"ALWAYS"},{"id":"edge-2","fromNodeId":"node-worker","toNodeId":"node-end","condition":"ON_SUCCESS"}]}',
   1700000000000, 1700000000000);

INSERT INTO workspaces (id, name, description, created_at) VALUES
  ('ws-research', 'Research Project', 'General research workspace', 1700000000000);

INSERT INTO workspace_workflows (workspace_id, workflow_definition_id) VALUES
  ('ws-research', 'wf-research-pipeline');

INSERT INTO sessions (id, workspace_id, workflow_definition_id, status, started_at, completed_at) VALUES
  ('session-completed', 'ws-research', 'wf-research-pipeline', 'COMPLETED', 1700000001000, 1700000002000);

INSERT INTO agent_executions (id, session_id, node_id, agent_definition_id, status, input_context, output, report, started_at, completed_at) VALUES
  ('exec-research', 'session-completed', 'node-worker', 'agent-researcher', 'COMPLETED',
   '{"query":"latest AI news"}',
   'Found 10 results about AI.',
   '# Summary\nAI is advancing rapidly.',
   1700000001000, 1700000002000);

INSERT INTO workspace_knowledge (id, workspace_id, source_execution_id, title, content, tags, created_at) VALUES
  ('knowledge-ai-trends', 'ws-research', 'exec-research',
   'AI Trends 2025', '# AI Trends\nLLMs are widely adopted.',
   '["ai","trends","research"]',
   1700000002000);
