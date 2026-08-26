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

import lombok.extern.slf4j.Slf4j;

/**
 * Selects the appropriate {@link LLMGateway} implementation at startup.
 *
 * <h3>Selection rules (evaluated in order)</h3>
 * <ol>
 *   <li>Only {@code ANTHROPIC_API_KEY} is set → {@link ClaudeProvider}.</li>
 *   <li>Only {@code OPENAI_API_KEY} is set    → {@link GPTProvider}.</li>
 *   <li>Both keys are set → read {@code LLM_PROVIDER} env var:
 *       <ul>
 *         <li>{@code "openai"}    → {@link GPTProvider}</li>
 *         <li>{@code "anthropic"} or absent → {@link ClaudeProvider} (Claude preferred)</li>
 *       </ul>
 *   </li>
 *   <li>Neither key is set → {@link MockLLMProvider}. This is intentionally
 *       only for local development; every {@code send()} call logs a WARNING
 *       so the absence of a real key is visible in production logs.</li>
 * </ol>
 */
@Slf4j
public class LLMProviderFactory {

    private LLMProviderFactory() {}

    public static LLMGateway create() {
        boolean hasAnthropic = isSet("ANTHROPIC_API_KEY");
        boolean hasOpenAI    = isSet("OPENAI_API_KEY");

        if (hasAnthropic && hasOpenAI) {
            String preference = System.getenv("LLM_PROVIDER");
            if ("openai".equalsIgnoreCase(preference)) {
                log.info("LLM provider: GPTProvider (both keys set, LLM_PROVIDER=openai)");
                return new GPTProvider();
            }
            log.info("LLM provider: ClaudeProvider (both keys set, defaulting to Claude; set LLM_PROVIDER=openai to override)");
            return new ClaudeProvider();
        }

        if (hasAnthropic) {
            log.info("LLM provider: ClaudeProvider");
            return new ClaudeProvider();
        }

        if (hasOpenAI) {
            log.info("LLM provider: GPTProvider");
            return new GPTProvider();
        }

        log.warn("No LLM API key found (ANTHROPIC_API_KEY / OPENAI_API_KEY). " +
                 "Using MockLLMProvider — suitable for local development only. " +
                 "Every request will produce a canned response.");
        return new MockLLMProvider();
    }

    private static boolean isSet(String envVar) {
        String val = System.getenv(envVar);
        return val != null && !val.isBlank();
    }
}
