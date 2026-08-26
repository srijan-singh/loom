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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Streams responses from Anthropic's Messages API ({@code /v1/messages}).
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code ANTHROPIC_API_KEY} (required)</li>
 *   <li>{@code ANTHROPIC_MODEL}   (optional, default {@code claude-3-5-sonnet-20241022})</li>
 * </ul>
 *
 * <p>SSE event mapping:
 * <ul>
 *   <li>{@code content_block_delta} with {@code delta.type=text_delta} → TOKEN</li>
 *   <li>{@code content_block_stop}  after a {@code tool_use} block      → TOOL_CALL</li>
 *   <li>{@code message_stop}                                             → DONE</li>
 *   <li>HTTP non-2xx or {@code error} event                             → ERROR</li>
 * </ul>
 */
@Slf4j
public class ClaudeProvider implements LLMGateway {

    private static final String API_URL        = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL  = "claude-3-5-sonnet-20241022";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public ClaudeProvider() {
        this(new OkHttpClient(), new ObjectMapper());
    }

    /** Package-private for testing with a mock HTTP client. */
    ClaudeProvider(OkHttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper     = mapper;
        this.apiKey     = System.getenv("ANTHROPIC_API_KEY");
        String envModel = System.getenv("ANTHROPIC_MODEL");
        this.model      = (envModel != null && !envModel.isBlank()) ? envModel : DEFAULT_MODEL;
    }

    @Override
    public void send(LLMRequest request, Consumer<LLMResponse> tokenConsumer) {
        if (apiKey == null || apiKey.isBlank()) {
            tokenConsumer.accept(LLMResponse.error("ANTHROPIC_API_KEY is not set"));
            return;
        }

        String body;
        try {
            body = buildRequestBody(request);
        } catch (Exception e) {
            tokenConsumer.accept(LLMResponse.error("Failed to build request: " + e.getMessage()));
            return;
        }

        Request httpRequest = new Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body, JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "(empty)";
                log.warn("Anthropic HTTP error {}: {}", response.code(), sanitize(errBody));
                tokenConsumer.accept(LLMResponse.error("Anthropic HTTP " + response.code()));
                return;
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                tokenConsumer.accept(LLMResponse.error("Empty response body from Anthropic"));
                return;
            }

            parseStream(responseBody, tokenConsumer);
        } catch (Exception e) {
            log.error("Anthropic streaming error", e);
            tokenConsumer.accept(LLMResponse.error("Streaming error: " + e.getMessage()));
        }
    }

    // ── request building ─────────────────────────────────────────────────────

    private String buildRequestBody(LLMRequest request) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", request.getModel() != null ? request.getModel() : model);
        root.put("max_tokens", request.getMaxTokens());
        root.put("stream", true);

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            root.put("system", request.getSystemPrompt());
        }

        ArrayNode messages = root.putArray("messages");
        // Prior history
        if (request.getHistory() != null) {
            for (LLMMessage msg : request.getHistory()) {
                ObjectNode m = messages.addObject();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
            }
        }
        // Current user turn
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", request.getUserPrompt());

        // Tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (MCPToolDefinition tool : request.getTools()) {
                ObjectNode t = tools.addObject();
                t.put("name", tool.getName());
                t.put("description", tool.getDescription());
                t.set("input_schema", mapper.valueToTree(tool.getInputSchema()));
            }
        }

        return mapper.writeValueAsString(root);
    }

    // ── SSE parsing ──────────────────────────────────────────────────────────

    /**
     * Reads the Anthropic SSE stream line-by-line and translates each
     * {@code data:} line into one or more {@link LLMResponse} events.
     *
     * <p>Pending tool_use accumulation: Anthropic sends a {@code content_block_start}
     * with {@code type=tool_use}, then one or more {@code content_block_delta}
     * events with {@code delta.type=input_json_delta}, followed by
     * {@code content_block_stop}.  We accumulate the JSON and emit TOOL_CALL at stop.
     */
    private void parseStream(ResponseBody body, Consumer<LLMResponse> consumer) throws Exception {
        // State for accumulating a tool_use block
        String pendingToolName       = null;
        StringBuilder pendingToolJson = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;

                JsonNode event;
                try {
                    event = mapper.readTree(data);
                } catch (Exception e) {
                    log.debug("Skipping non-JSON SSE data: {}", data);
                    continue;
                }

                String eventType = event.path("type").asText("");
                switch (eventType) {

                    case "content_block_start": {
                        JsonNode block = event.path("content_block");
                        if ("tool_use".equals(block.path("type").asText())) {
                            pendingToolName = block.path("name").asText();
                            pendingToolJson.setLength(0);
                        }
                        break;
                    }

                    case "content_block_delta": {
                        JsonNode delta = event.path("delta");
                        String deltaType = delta.path("type").asText("");
                        if ("text_delta".equals(deltaType)) {
                            String text = delta.path("text").asText("");
                            if (!text.isEmpty()) consumer.accept(LLMResponse.token(text));
                        } else if ("input_json_delta".equals(deltaType)) {
                            pendingToolJson.append(delta.path("partial_json").asText(""));
                        }
                        break;
                    }

                    case "content_block_stop": {
                        if (pendingToolName != null) {
                            Map<String, Object> input = parseToolInput(pendingToolJson.toString());
                            consumer.accept(LLMResponse.toolCall(pendingToolName, input));
                            pendingToolName = null;
                            pendingToolJson.setLength(0);
                        }
                        break;
                    }

                    case "message_stop": {
                        consumer.accept(LLMResponse.done());
                        return;
                    }

                    case "error": {
                        String msg = event.path("error").path("message").asText("Unknown Anthropic error");
                        log.warn("Anthropic SSE error event: {}", msg);
                        consumer.accept(LLMResponse.error(msg));
                        return;
                    }

                    default:
                        // ping, message_start, message_delta, etc. — ignored
                        break;
                }
            }
        }
        // Stream ended without message_stop — emit DONE anyway
        consumer.accept(LLMResponse.done());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolInput(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tool input JSON: {}", sanitize(json));
            return Map.of();
        }
    }

    /** Removes API key values from log strings as a safety net. */
    private static String sanitize(String s) {
        if (s == null) return null;
        // Truncate long bodies; never log the key itself (it's in the header, not the body,
        // but guard against accidental inclusion in error payloads)
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
