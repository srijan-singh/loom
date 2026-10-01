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

import java.util.Optional;

import com.loom.storage.repository.LLMConnectionRepository;

/**
 * Selects the appropriate {@link LLMGateway} implementation at startup.
 *
 * <p>Loads the default {@link LLMConnection} from the database. If one exists, an {@link
 * OpenAICompatibleProvider} is returned configured with that connection's credentials. If no
 * default connection is configured, {@link MockLLMProvider} is returned as a fallback suitable for
 * local development only.
 */
@Slf4j
public class LLMProviderFactory {

    private LLMProviderFactory() {}

    public static LLMGateway create(LLMConnectionRepository repo) {
        Optional<LLMConnection> defaultConn = repo.findDefault();
        if (defaultConn.isPresent()) {
            LLMConnection conn = defaultConn.get();
            ProviderConfig config =
                    new ProviderConfig(conn.getBaseUrl(), conn.getApiKey(), conn.getModel());
            log.info(
                    "LLM provider: OpenAICompatibleProvider (default connection: {})",
                    conn.getName());
            return new OpenAICompatibleProvider(config);
        }
        log.warn(
                "No default LLM connection found in database. Using MockLLMProvider — "
                        + "suitable for local development only. Configure a default connection via "
                        + "POST /llm/connections and PATCH /llm/connections/{id}/default.");
        return new MockLLMProvider();
    }
}
