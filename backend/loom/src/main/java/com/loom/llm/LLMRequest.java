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

import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Everything the LLM needs to produce a response: model selection, prompts,
 * conversation history, and the set of tools the agent may invoke.
 */
@Data
@Builder
public class LLMRequest {

    /** Model identifier, e.g. "claude-3-5-sonnet-20241022" or "gpt-4o". */
    private String model;

    /** System-level instructions for the assistant. */
    private String systemPrompt;

    /** The current user turn. */
    private String userPrompt;

    /**
     * Prior conversation turns, oldest-first.
     * Null or empty means a single-turn interaction.
     */
    @Nullable
    private List<LLMMessage> history;

    /**
     * MCP tools available for the model to call.
     * Null or empty means no tools are offered.
     */
    @Nullable
    private List<MCPToolDefinition> tools;

    /** Maximum completion tokens. Defaults to 4096 if not set. */
    @Builder.Default
    private int maxTokens = 4096;
}
