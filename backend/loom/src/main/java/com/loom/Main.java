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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.engine.AgentRunner;
import com.loom.event.EventType;
import com.loom.event.WorkflowEvent;
import com.loom.llm.LLMGateway;
import com.loom.llm.LLMProviderFactory;
import com.loom.mcp.MCPClient;
import com.loom.transport.LocalServer;
import com.loom.transport.SSEManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class Main {
    public static void main(String[] args) {
        String portEnv = System.getenv("LOOM_PORT");
        int port = (portEnv != null && !portEnv.isBlank()) ? Integer.parseInt(portEnv) : 7070;

        SSEManager  sseManager  = new SSEManager();
        LLMGateway  llmGateway  = LLMProviderFactory.create();
        MCPClient   mcpClient   = new MCPClient();
        AgentRunner agentRunner = new AgentRunner(llmGateway, mcpClient, sseManager);

        LocalServer localServer = new LocalServer(sseManager, agentRunner);

        localServer.start(port);
        System.out.println("Loom engine listening on port " + port);

        ObjectMapper mapper = new ObjectMapper();
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                WorkflowEvent testflowEvent = WorkflowEvent.builder()
                        .eventType(EventType.SESSION_STARTED)
                        .sessionId("test-123")
                        .data(mapper.valueToTree(Map.of("message", "Hello World!")))
                        .build();

                sseManager.broadcast(testflowEvent);
                log.info("test event broadcasted");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}