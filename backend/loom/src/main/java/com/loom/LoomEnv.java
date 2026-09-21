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
package com.loom;

/**
 * Canonical registry of every environment variable read by Loom at runtime.
 *
 * <p>Each constant carries its variable name and default value. Call sites use the no-arg typed
 * helpers — defaults never appear outside this file.
 *
 * <pre>{@code
 * int  port    = LoomEnv.LOOM_PORT.getInt();
 * long timeout = LoomEnv.LOOM_NODE_TIMEOUT_SECONDS.getLong();
 * String key   = LoomEnv.OPENAI_API_KEY.get();          // null when unset
 * String model = LoomEnv.OPENAI_MODEL.getOrDefault(myDefault);
 * }</pre>
 */
public enum LoomEnv {

    // ── Server ────────────────────────────────────────────────────────────────
    /** HTTP port the embedded server listens on. Default: {@code 7070}. */
    LOOM_PORT("LOOM_PORT", "7070"),

    // ── Storage ───────────────────────────────────────────────────────────────
    /** Filesystem path for the SQLite database file. Default: {@code ./loom.db}. */
    LOOM_DB_PATH("LOOM_DB_PATH", "./loom.db"),

    // ── Engine ────────────────────────────────────────────────────────────────
    /** Per-node execution timeout in seconds. Default: {@code 120}. */
    LOOM_NODE_TIMEOUT_SECONDS("LOOM_NODE_TIMEOUT_SECONDS", "120"),

    /**
     * Thread-pool size for parallel worker dispatch in SUPERVISOR workflows. Default: {@code 4}.
     */
    LOOM_WORKER_THREADS("LOOM_WORKER_THREADS", "4"),

    /** Maximum supervisor iterations before a session is marked PARTIAL. Default: {@code 10}. */
    LOOM_SUPERVISOR_MAX_ITER("LOOM_SUPERVISOR_MAX_ITER", "10"),

    // ── LLM ───────────────────────────────────────────────────────────────────
    /**
     * Selects the LLM provider when both API keys are present. Accepted values: {@code "openai"},
     * {@code "anthropic"} (default when absent).
     */
    LLM_PROVIDER("LLM_PROVIDER", null),

    /** API key for OpenAI. Required when using {@link com.loom.llm.GPTProvider}. */
    OPENAI_API_KEY("OPENAI_API_KEY", null),

    /**
     * OpenAI model identifier. No global default — {@link com.loom.llm.GPTProvider} supplies its
     * own provider-specific fallback via {@link #getOrDefault(String)}.
     */
    OPENAI_MODEL("OPENAI_MODEL", null),

    /** API key for Anthropic. Required when using {@link com.loom.llm.ClaudeProvider}. */
    ANTHROPIC_API_KEY("ANTHROPIC_API_KEY", null),

    /**
     * Anthropic model identifier. No global default — {@link com.loom.llm.ClaudeProvider} supplies
     * its own provider-specific fallback via {@link #getOrDefault(String)}.
     */
    ANTHROPIC_MODEL("ANTHROPIC_MODEL", null);

    // ── Implementation ────────────────────────────────────────────────────────

    private final String key;

    /** {@code null} for variables that have no global default (e.g. API keys, model names). */
    private final String defaultValue;

    LoomEnv(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    /** Returns the environment variable name (e.g. {@code "LOOM_PORT"}). */
    public String key() {
        return key;
    }

    /**
     * Returns the raw value from the environment, or {@code null} if unset or blank. For variables
     * with a default use {@link #getOrDefault(String)}, {@link #getInt()}, or {@link #getLong()}
     * instead.
     */
    public String get() {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : null;
    }

    /**
     * Returns the raw string value, falling back to {@code fallback} when unset or blank. Use this
     * when the caller owns the fallback (e.g. provider-specific model names).
     */
    public String getOrDefault(String fallback) {
        String val = get();
        return val != null ? val : fallback;
    }

    /**
     * Returns the value resolved against this constant's built-in default as a {@code String}.
     * Throws {@link IllegalStateException} if neither the env var nor a default is available.
     */
    public String getString() {
        String val = get();
        if (val != null) return val;
        if (defaultValue != null) return defaultValue;
        throw new IllegalStateException(key + " is not set and has no default");
    }

    /**
     * Parses the value (or built-in default) as an {@code int}. Non-positive values are rejected
     * and the default is used instead.
     *
     * @throws IllegalStateException if neither the env var nor a default is available
     */
    public int getInt() {
        String raw = get();
        if (raw != null) {
            try {
                int parsed = Integer.parseInt(raw.trim());
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        if (defaultValue != null) {
            return Integer.parseInt(defaultValue);
        }
        throw new IllegalStateException(key + " is not set and has no default");
    }

    /**
     * Parses the value (or built-in default) as a {@code long}.
     *
     * @throws IllegalStateException if neither the env var nor a default is available
     */
    public long getLong() {
        String raw = get();
        if (raw != null) {
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        if (defaultValue != null) {
            return Long.parseLong(defaultValue);
        }
        throw new IllegalStateException(key + " is not set and has no default");
    }

    /** Returns {@code true} when the variable is set to a non-blank value. */
    public boolean isSet() {
        return get() != null;
    }
}
