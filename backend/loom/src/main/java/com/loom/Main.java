/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.loom;

import com.loom.engine.AgentRuntime;
import com.loom.llm.LLMGateway;
import com.loom.llm.LLMProviderFactory;
import com.loom.mcp.MCPClient;
import com.loom.storage.DatabaseManager;
import com.loom.storage.repository.*;
import com.loom.transport.LocalServer;
import com.loom.transport.SSEManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        String portEnv = System.getenv("LOOM_PORT");
        int port = (portEnv != null && !portEnv.isBlank()) ? Integer.parseInt(portEnv) : 7070;

        // Storage
        DatabaseManager db = new DatabaseManager();

        SkillRepository                skillRepo     = new SkillRepository(db);
        MCPConnectionRepository        mcpRepo       = new MCPConnectionRepository(db);
        AgentRepository                agentRepo     = new AgentRepository(db);
        WorkflowRepository             workflowRepo  = new WorkflowRepository(db);
        WorkspaceRepository            workspaceRepo = new WorkspaceRepository(db);
        SessionRepository              sessionRepo   = new SessionRepository(db);
        AgentExecutionRepository       execRepo      = new AgentExecutionRepository(db);
        WorkspaceKnowledgeRepository   knowledgeRepo = new WorkspaceKnowledgeRepository(db);

        // Engine
        SSEManager  sseManager  = new SSEManager();
        LLMGateway  llmGateway  = LLMProviderFactory.create();
        MCPClient   mcpClient   = new MCPClient();

        AgentRuntime agentRuntime = new AgentRuntime(
                llmGateway, mcpClient, sseManager,
                skillRepo, knowledgeRepo, execRepo,
                sessionRepo, workflowRepo, agentRepo);

        // Transport
        LocalServer localServer = new LocalServer(
                sseManager, agentRuntime,
                agentRepo, skillRepo, mcpRepo,
                sessionRepo, workflowRepo, workspaceRepo);

        localServer.start(port);
        System.out.println("Loom engine listening on port " + port);
    }
}
