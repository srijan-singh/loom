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

import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Deterministic stand-in used when no real API key is configured. Streams a short canned reply
 * token-by-token so the SSE pipeline can be exercised end-to-end without any network access.
 */
@Slf4j
public class MockLLMProvider implements LLMGateway {

    private static final List<String> TOKENS =
            List.of("Hello", "!", " I", " am", " Loom", "'s", " mock", " LLM", ".");

    @Override
    public void send(LLMRequest request, Consumer<LLMResponse> tokenConsumer) {
        log.warn(
                "MockLLMProvider active — set ANTHROPIC_API_KEY or OPENAI_API_KEY for a real provider");
        for (String token : TOKENS) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            tokenConsumer.accept(LLMResponse.token(token));
        }
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        tokenConsumer.accept(LLMResponse.done());
    }
}
