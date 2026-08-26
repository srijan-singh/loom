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

import java.util.Map;

/**
 * A single event emitted by a streaming LLM provider.
 *
 * <ul>
 *   <li>{@code TOKEN}    — incremental text delta; read {@link #content}</li>
 *   <li>{@code TOOL_CALL}— model wants to invoke a tool; read {@link #toolName} and {@link #toolInput}</li>
 *   <li>{@code DONE}     — stream ended cleanly</li>
 *   <li>{@code ERROR}    — provider reported an error; read {@link #content} for message</li>
 * </ul>
 */
@Data
@Builder
public class LLMResponse {

    public static final String TOKEN     = "TOKEN";
    public static final String TOOL_CALL = "TOOL_CALL";
    public static final String DONE      = "DONE";
    public static final String ERROR     = "ERROR";

    /** One of {@link #TOKEN}, {@link #TOOL_CALL}, {@link #DONE}, {@link #ERROR}. */
    private String type;

    /** Token text (type=TOKEN) or error message (type=ERROR). Null for other types. */
    @Nullable
    private String content;

    /** Tool name requested by the model (type=TOOL_CALL only). */
    @Nullable
    private String toolName;

    /** Arguments passed by the model to the tool (type=TOOL_CALL only). */
    @Nullable
    private Map<String, Object> toolInput;

    // ── convenience factories ────────────────────────────────────────────────

    public static LLMResponse token(String text)  { return LLMResponse.builder().type(TOKEN).content(text).build(); }
    public static LLMResponse done()              { return LLMResponse.builder().type(DONE).build(); }
    public static LLMResponse error(String msg)   { return LLMResponse.builder().type(ERROR).content(msg).build(); }
    public static LLMResponse toolCall(String name, Map<String, Object> input) {
        return LLMResponse.builder().type(TOOL_CALL).toolName(name).toolInput(input).build();
    }
}
