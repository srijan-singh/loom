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

import java.util.function.Consumer;

/**
 * Transport-agnostic contract for streaming LLM providers.
 *
 * <h3>Capability contract</h3>
 * <ul>
 *   <li><b>Streaming</b> — tokens arrive incrementally. Every
 *       {@link LLMResponse#TOKEN} event carries a non-null, non-empty
 *       {@code content} string.</li>
 *   <li><b>Tool calls</b> — when the model requests a tool, one
 *       {@link LLMResponse#TOOL_CALL} event is emitted per call, with
 *       {@code toolName} and {@code toolInput} fully populated before the
 *       event is delivered to the consumer.</li>
 *   <li><b>Lifecycle</b> — the stream always ends with <em>exactly one</em>
 *       terminal event:
 *       <ul>
 *         <li>{@link LLMResponse#DONE}  — clean end-of-stream</li>
 *         <li>{@link LLMResponse#ERROR} — provider or transport failure;
 *             {@code content} holds a human-readable message with no API
 *             key material</li>
 *       </ul>
 *       No events are emitted after the terminal event.</li>
 *   <li><b>Ordering</b> — TOKEN events arrive in model-generation order.
 *       TOOL_CALL events for the same turn appear after all TOKEN events for
 *       that turn (if any) and before the terminal event.</li>
 *   <li><b>Blocking</b> — the call blocks the calling thread until the
 *       stream is fully consumed. Callers that need async behaviour must
 *       dispatch to their own executor (see {@code AgentRunner}).</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * Implementations must never throw; all failures are delivered as an
 * {@link LLMResponse#ERROR} event. In particular:
 * <ul>
 *   <li>A missing or blank API key emits ERROR immediately without making
 *       any network call.</li>
 *   <li>A non-2xx HTTP response emits ERROR with the HTTP status code; the
 *       raw response body is logged at WARN but never forwarded to the
 *       consumer.</li>
 * </ul>
 */
public interface LLMGateway {
    void send(LLMRequest request, Consumer<LLMResponse> tokenConsumer);
}
