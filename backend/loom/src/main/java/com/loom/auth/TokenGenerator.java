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
package com.loom.auth;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates token for authorization via UI */
public class TokenGenerator {
    // SecureRandom is thread-safe and expensive to initialize; reuse it
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE_64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TokenGenerator() {}

    /** Generates raw SECURE_RANDOM bytes and encodes them into a URL-safe Base64 string */
    public static String generateToken() {
        // 32 bits of randomness gives 256 bits of entropy
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);

        // Encodes to a clean, URL safe string
        return BASE_64_ENCODER.encodeToString(token);
    }
}
