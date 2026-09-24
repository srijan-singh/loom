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

import lombok.extern.slf4j.Slf4j;

import com.loom.auth.TokenGenerator;
import com.loom.engine.AgentRuntime;
import com.loom.engine.GraphResolver;
import com.loom.engine.StateManager;
import com.loom.engine.WorkflowEngine;
import com.loom.llm.LLMGateway;
import com.loom.llm.LLMProviderFactory;
import com.loom.mcp.MCPClient;
import com.loom.storage.DatabaseManager;
import com.loom.storage.repository.AgentExecutionRepository;
import com.loom.storage.repository.AgentRepository;
import com.loom.storage.repository.MCPConnectionRepository;
import com.loom.storage.repository.SessionRepository;
import com.loom.storage.repository.SkillRepository;
import com.loom.storage.repository.WorkflowRepository;
import com.loom.storage.repository.WorkspaceKnowledgeRepository;
import com.loom.storage.repository.WorkspaceRepository;
import com.loom.transport.LocalServer;
import com.loom.transport.SSEManager;

@Slf4j
// Main is the composition root: it instantiates every repository by design.
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class Main {
    public static void main(String[] args) {
        int port = LoomEnv.LOOM_PORT.getInt();

        // Storage
        DatabaseManager db = new DatabaseManager();

        SkillRepository skillRepo = new SkillRepository(db);
        MCPConnectionRepository mcpRepo = new MCPConnectionRepository(db);
        AgentRepository agentRepo = new AgentRepository(db);
        WorkflowRepository workflowRepo = new WorkflowRepository(db);
        WorkspaceRepository workspaceRepo = new WorkspaceRepository(db);
        SessionRepository sessionRepo = new SessionRepository(db);
        AgentExecutionRepository execRepo = new AgentExecutionRepository(db);
        WorkspaceKnowledgeRepository knowledgeRepo = new WorkspaceKnowledgeRepository(db);

        // Engine
        SSEManager sseManager = new SSEManager();
        LLMGateway llmGateway = LLMProviderFactory.create();
        MCPClient mcpClient = new MCPClient();

        AgentRuntime agentRuntime =
                new AgentRuntime(
                        llmGateway,
                        mcpClient,
                        sseManager,
                        skillRepo,
                        knowledgeRepo,
                        execRepo,
                        sessionRepo,
                        workflowRepo,
                        agentRepo);

        GraphResolver graphResolver = new GraphResolver();
        StateManager stateManager = new StateManager(execRepo);
        WorkflowEngine workflowEngine =
                new WorkflowEngine(
                        agentRuntime,
                        stateManager,
                        graphResolver,
                        sessionRepo,
                        workflowRepo,
                        execRepo,
                        sseManager,
                        knowledgeRepo);

        // Auth - one token per launch, handed to Flutter via stdout
        String token = TokenGenerator.generateToken();

        // Transport
        LocalServer localServer =
                new LocalServer(
                        token,
                        sseManager,
                        agentRuntime,
                        workflowEngine,
                        agentRepo,
                        skillRepo,
                        mcpRepo,
                        sessionRepo,
                        execRepo,
                        workflowRepo,
                        workspaceRepo,
                        graphResolver);

        localServer.start(port);
        // Announce both port and token on stdout so the Flutter shell can read them.
        // CAUTION: These two lines are part of the process-launch protocol - do not re-order them.
        System.out.println("LOOM_PORT=" + port);
        System.out.println("LOOM_TOKEN=" + token);
        System.out.flush();
    }
}
