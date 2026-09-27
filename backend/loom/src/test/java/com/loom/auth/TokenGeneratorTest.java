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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

    // 32 random bytes with URL-safe Base64 and no padding → ceil(32 * 4/3) = 43 characters.
    private static final int EXPECTED_TOKEN_LENGTH = 43;

    // URL-safe Base64 alphabet: A-Z, a-z, 0-9, '-', '_'. No '+', '/', or '=' padding.
    private static final Pattern URL_SAFE_BASE64_NO_PADDING = Pattern.compile("^[A-Za-z0-9\\-_]+$");

    @Test
    void generatedToken_isNotNullOrBlank() {
        assertThat(TokenGenerator.generateToken()).isNotNull().isNotBlank();
    }

    @Test
    void generatedToken_hasExpectedLength() {
        // 32 bytes → 43 chars when using URL-safe Base64 without padding.
        // If this fails with 44, the encoder still has padding (withoutPadding() missing).
        assertThat(TokenGenerator.generateToken()).hasSize(EXPECTED_TOKEN_LENGTH);
    }

    @Test
    void generatedToken_usesUrlSafeAlphabetWithoutPadding() {
        // Run enough iterations to statistically encounter non-URL-safe characters
        // if the wrong encoder variant is used (standard Base64 emits '+' and '/';
        // URL-safe without withoutPadding() emits '=' at the end).
        for (int i = 0; i < 200; i++) {
            String token = TokenGenerator.generateToken();
            assertThat(token)
                    .as(
                            "Token at iteration %d must match URL-safe Base64 without padding: '%s'",
                            i, token)
                    .matches(URL_SAFE_BASE64_NO_PADDING);
        }
    }

    @Test
    void generatedToken_doesNotContainPlusOrSlash() {
        // '+' is decoded as a space by query-string parsers (application/x-www-form-urlencoded).
        // '/' is unsafe in URL paths. Both appear in standard (non-URL-safe) Base64.
        for (int i = 0; i < 200; i++) {
            String token = TokenGenerator.generateToken();
            assertThat(token)
                    .as("Token must not contain '+' (would be decoded as space in query params)")
                    .doesNotContain("+")
                    .as("Token must not contain '/' (unsafe in URL paths)")
                    .doesNotContain("/");
        }
    }

    @Test
    void generatedToken_doesNotContainPaddingEquals() {
        // '=' is the key-value separator in query strings; padding must be stripped
        // via withoutPadding() so the token survives round-tripping as a query parameter.
        for (int i = 0; i < 200; i++) {
            String token = TokenGenerator.generateToken();
            assertThat(token)
                    .as("Token must not contain '=' padding (key-value separator in query strings)")
                    .doesNotContain("=");
        }
    }

    @Test
    void generatedToken_isDecodableBack_to32Bytes() {
        // URL-safe Base64 without padding requires the URL decoder, not the standard one.
        String token = TokenGenerator.generateToken();
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32);
    }

    @Test
    void generatedToken_isUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(TokenGenerator.generateToken());
        }
        assertThat(tokens).hasSize(100);
    }
}
