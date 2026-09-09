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

package com.loom.engine;

import com.loom.domain.AgentDefinition;
import com.loom.domain.Session;
import com.loom.domain.WorkspaceKnowledge;
import com.loom.llm.LLMRequest;
import com.loom.llm.MCPToolDefinition;
import com.loom.mcp.MCPClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles the {@link LLMRequest} for a single agent turn from its
 * definition, session, input context, and available workspace knowledge.
 *
 * <ul>
 *   <li>System prompt  = the agent's skill content markdown (may be null/empty)</li>
 *   <li>User prompt    = {@code roleDescription + "\n\n" + inputContext}</li>
 *   <li>Tools          = MCP tool definitions for each id in
 *                        {@code agentDefinition.allowedMcpIds}</li>
 * </ul>
 */
public class ContextBuilder {

    private final MCPClient mcpClient;

    public ContextBuilder(MCPClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Builds an {@link LLMRequest} ready to be sent to an LLM provider.
     *
     * @param agentDefinition the agent whose skill + role drive the prompts
     * @param session         the current session (used for workspace scoping)
     * @param skillContent    raw markdown content of the linked skill (may be null)
     * @param inputContext    the user-facing input for this execution node
     * @param knowledge       workspace knowledge snippets to append to the user prompt
     * @return fully-formed LLMRequest
     */
    public LLMRequest build(AgentDefinition agentDefinition,
                            Session session,
                            String skillContent,
                            String inputContext,
                            List<WorkspaceKnowledge> knowledge) {

        String systemPrompt = skillContent != null ? skillContent : "";

        StringBuilder userPrompt = new StringBuilder();
        if (agentDefinition.getRoleDescription() != null) {
            userPrompt.append(agentDefinition.getRoleDescription());
        }
        if (inputContext != null && !inputContext.isBlank()) {
            if (userPrompt.length() > 0) userPrompt.append("\n\n");
            userPrompt.append(inputContext);
        }
        if (knowledge != null && !knowledge.isEmpty()) {
            userPrompt.append("\n\n## Workspace Knowledge\n");
            for (WorkspaceKnowledge k : knowledge) {
                userPrompt.append("### ").append(k.getTitle()).append("\n");
                userPrompt.append(k.getContent()).append("\n\n");
            }
        }

        List<MCPToolDefinition> tools = null;
        if (agentDefinition.getAllowedMcpIds() != null && !agentDefinition.getAllowedMcpIds().isEmpty()) {
            tools = agentDefinition.getAllowedMcpIds().stream()
                    .flatMap(mcpId -> mcpClient.listTools(mcpId).stream())
                    .collect(Collectors.toList());
        }

        return LLMRequest.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt.toString())
                .tools(tools)
                .build();
    }
}
