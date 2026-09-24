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

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class StubMCPClient implements MCPClient {
    @Override
    public List<MCPToolDefinition> listTools(String mcpConnectionId) {
        log.debug("MCPClient.listTools (stub): mcpConnectionId='{}'", mcpConnectionId);
        return Collections.emptyList();
    }

    @Override
    public String execute(String toolName, Map<String, Object> toolInput) {
        log.info("MCPClient.execute (stub): tool='{}' input={}", toolName, toolInput);
        return "Tool '" + toolName + "' executed (stub result).";
    }
}
