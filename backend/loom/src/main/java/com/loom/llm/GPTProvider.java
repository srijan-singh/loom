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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Streams responses from OpenAI's Chat Completions API ({@code /v1/chat/completions}).
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code OPENAI_API_KEY} (required)</li>
 *   <li>{@code OPENAI_MODEL}   (optional, default {@code gpt-4o})</li>
 * </ul>
 *
 * <p>SSE event mapping:
 * <ul>
 *   <li>{@code delta.content} present and non-null → TOKEN</li>
 *   <li>{@code delta.tool_calls} present            → accumulate; emit TOOL_CALL on finish_reason</li>
 *   <li>{@code [DONE]} sentinel                     → DONE</li>
 *   <li>HTTP non-2xx or exception                   → ERROR</li>
 * </ul>
 */
@Slf4j
public class GPTProvider implements LLMGateway {

    private static final String API_URL       = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o";

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public GPTProvider() {
        this(new OkHttpClient(), new ObjectMapper());
    }

    /** Package-private for testing with a mock HTTP client. */
    GPTProvider(OkHttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper     = mapper;
        this.apiKey     = System.getenv("OPENAI_API_KEY");
        String envModel = System.getenv("OPENAI_MODEL");
        this.model      = (envModel != null && !envModel.isBlank()) ? envModel : DEFAULT_MODEL;
    }

    @Override
    public void send(LLMRequest request, Consumer<LLMResponse> tokenConsumer) {
        if (apiKey == null || apiKey.isBlank()) {
            tokenConsumer.accept(LLMResponse.error("OPENAI_API_KEY is not set"));
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
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body, JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "(empty)";
                log.warn("OpenAI HTTP error {}: {}", response.code(), sanitize(errBody));
                tokenConsumer.accept(LLMResponse.error("OpenAI HTTP " + response.code()));
                return;
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                tokenConsumer.accept(LLMResponse.error("Empty response body from OpenAI"));
                return;
            }

            parseStream(responseBody, tokenConsumer);
        } catch (Exception e) {
            log.error("OpenAI streaming error", e);
            tokenConsumer.accept(LLMResponse.error("Streaming error: " + e.getMessage()));
        }
    }

    // ── request building ─────────────────────────────────────────────────────

    private String buildRequestBody(LLMRequest request) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", request.getModel() != null ? request.getModel() : model);
        root.put("max_tokens", request.getMaxTokens());
        root.put("stream", true);

        ArrayNode messages = root.putArray("messages");

        // System prompt as a system message
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", request.getSystemPrompt());
        }

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
                t.put("type", "function");
                ObjectNode fn = t.putObject("function");
                fn.put("name", tool.getName());
                fn.put("description", tool.getDescription());
                fn.set("parameters", mapper.valueToTree(tool.getInputSchema()));
            }
        }

        return mapper.writeValueAsString(root);
    }

    // ── SSE parsing ──────────────────────────────────────────────────────────

    /**
     * OpenAI streams Server-Sent Events where each {@code data:} line is a
     * JSON chunk, terminated by the literal {@code data: [DONE]}.
     *
     * <p>Tool-call accumulation: OpenAI spreads a single tool call across
     * multiple chunks via {@code delta.tool_calls[].function.arguments} deltas.
     * We accumulate per index and emit TOOL_CALL events when the stream ends or
     * {@code finish_reason=tool_calls} is seen.
     */
    private void parseStream(ResponseBody body, Consumer<LLMResponse> consumer) throws Exception {
        // index → {name, accumulated-args}
        Map<Integer, ToolCallAccumulator> toolCalls = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();

                if ("[DONE]".equals(data)) {
                    flushToolCalls(toolCalls, consumer);
                    consumer.accept(LLMResponse.done());
                    return;
                }

                JsonNode chunk;
                try {
                    chunk = mapper.readTree(data);
                } catch (Exception e) {
                    log.debug("Skipping non-JSON SSE data: {}", data);
                    continue;
                }

                // Top-level error object (returned by some OpenAI error codes in stream)
                if (chunk.has("error")) {
                    String msg = chunk.path("error").path("message").asText("Unknown OpenAI error");
                    log.warn("OpenAI SSE error: {}", msg);
                    consumer.accept(LLMResponse.error(msg));
                    return;
                }

                JsonNode choices = chunk.path("choices");
                if (!choices.isArray() || choices.isEmpty()) continue;

                JsonNode choice = choices.get(0);
                JsonNode delta  = choice.path("delta");

                // TEXT token
                JsonNode contentNode = delta.path("content");
                if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                    String text = contentNode.asText("");
                    if (!text.isEmpty()) consumer.accept(LLMResponse.token(text));
                }

                // TOOL_CALL accumulation
                JsonNode toolCallsNode = delta.path("tool_calls");
                if (toolCallsNode.isArray()) {
                    for (JsonNode tc : toolCallsNode) {
                        int index = tc.path("index").asInt(0);
                        ToolCallAccumulator acc = toolCalls.computeIfAbsent(index, i -> new ToolCallAccumulator());
                        String nameFragment = tc.path("function").path("name").asText("");
                        if (!nameFragment.isEmpty()) acc.name.append(nameFragment);
                        String argsFragment = tc.path("function").path("arguments").asText("");
                        if (!argsFragment.isEmpty()) acc.args.append(argsFragment);
                    }
                }

                // finish_reason signals the tool call list is complete
                String finishReason = choice.path("finish_reason").asText("");
                if ("tool_calls".equals(finishReason)) {
                    flushToolCalls(toolCalls, consumer);
                    toolCalls.clear();
                }
            }
        }
        // Stream ended without [DONE] sentinel — flush any pending tool calls then emit DONE
        flushToolCalls(toolCalls, consumer);
        consumer.accept(LLMResponse.done());
    }

    private void flushToolCalls(Map<Integer, ToolCallAccumulator> toolCalls,
                                Consumer<LLMResponse> consumer) {
        if (toolCalls.isEmpty()) return;
        List<Integer> indices = new ArrayList<>(toolCalls.keySet());
        indices.sort(Integer::compareTo);
        for (int idx : indices) {
            ToolCallAccumulator acc = toolCalls.get(idx);
            Map<String, Object> input = parseToolInput(acc.args.toString());
            consumer.accept(LLMResponse.toolCall(acc.name.toString(), input));
        }
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

    private static String sanitize(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static class ToolCallAccumulator {
        final StringBuilder name = new StringBuilder();
        final StringBuilder args = new StringBuilder();
    }
}
