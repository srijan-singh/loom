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

package com.loom.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Describes a single MCP tool that can be offered to the LLM.
 * The {@code inputSchema} follows JSON Schema (type + properties + required).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolDefinition {
    /** Unique tool name, e.g. "read_file" */
    private String name;
    /** Human-readable description shown to the model. */
    private String description;
    /**
     * JSON Schema object describing the tool's parameters.
     * Example: {@code {"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}
     */
    private Map<String, Object> inputSchema;
}
