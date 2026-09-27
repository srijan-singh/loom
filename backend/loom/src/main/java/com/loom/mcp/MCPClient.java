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
package com.loom.mcp;

import java.util.List;
import java.util.Map;

/**
 * Stub MCP client.
 *
 * <p>Real tool execution is out of scope for this ticket. This class is the single place where
 * future MCP tool dispatch will live; callers already use it so the agentic loop compiles and runs
 * end-to-end.
 */
public interface MCPClient {

    /**
     * Returns the list of tools available on the given MCP connection. Stub implementation — real
     * discovery comes in a later issue.
     *
     * @param mcpConnectionId the MCP connection id
     * @return empty list (no tools available in stub mode)
     */
    List<MCPToolDefinition> listTools(String mcpConnectionId);

    /**
     * Executes a tool call and returns a plain-text result.
     *
     * @param toolName the tool identifier requested by the LLM
     * @param toolInput the arguments the LLM passed
     * @return a stub result string
     */
    String execute(String toolName, Map<String, Object> toolInput);
}
