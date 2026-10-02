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

import static org.junit.jupiter.api.Assertions.*;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OpenAICompatibleProvider: SSE streaming behaviour")
class OpenAICompatibleProviderTest {

    private MockWebServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final LLMRequest BASIC_REQUEST =
            LLMRequest.builder().userPrompt("Hello").maxTokens(100).build();

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Creates a provider wired to the MockWebServer. */
    private OpenAICompatibleProvider provider(String apiKey) {
        OkHttpClient client =
                new OkHttpClient.Builder()
                        .readTimeout(Duration.ZERO)
                        .callTimeout(Duration.ofSeconds(5))
                        .build();
        return new OpenAICompatibleProvider(
                server.url("").toString().replaceAll("/$", ""), apiKey, "gpt-4o", client, mapper);
    }

    private List<LLMResponse> collect(LLMRequest request, String apiKey) {
        List<LLMResponse> events = new ArrayList<>();
        provider(apiKey).send(request, events::add);
        return events;
    }

    /** Enqueues a 200 SSE response whose body is the given lines joined by newline. */
    private void enqueueSse(String... lines) {
        String body = String.join("\n", lines) + "\n";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "text/event-stream")
                        .setBody(body));
    }

    private String tokenChunk(String text) throws Exception {
        return mapper.writeValueAsString(
                mapper.readTree(
                        "{\"choices\":[{\"delta\":{\"content\":"
                                + mapper.writeValueAsString(text)
                                + "}}]}"));
    }

    private String toolCallChunk(int index, String name, String args) throws Exception {
        String escapedArgs = mapper.writeValueAsString(args);
        String json =
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":"
                        + index
                        + ",\"function\":{\"name\":"
                        + mapper.writeValueAsString(name)
                        + ",\"arguments\":"
                        + escapedArgs
                        + "}}]}}]}";
        return mapper.writeValueAsString(mapper.readTree(json));
    }

    private String finishChunk(String finishReason) throws Exception {
        return mapper.writeValueAsString(
                mapper.readTree(
                        "{\"choices\":[{\"delta\":{},\"finish_reason\":\""
                                + finishReason
                                + "\"}]}"));
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Token streaming: two TOKEN events then DONE")
    void tokenStreaming() throws Exception {
        enqueueSse("data: " + tokenChunk("Hello"), "data: " + tokenChunk(" world"), "data: [DONE]");

        List<LLMResponse> events = collect(BASIC_REQUEST, "sk-test");

        assertEquals(3, events.size());
        assertEquals(LLMResponse.TOKEN, events.get(0).getType());
        assertEquals("Hello", events.get(0).getContent());
        assertEquals(LLMResponse.TOKEN, events.get(1).getType());
        assertEquals(" world", events.get(1).getContent());
        assertEquals(LLMResponse.DONE, events.get(2).getType());
    }

    @Test
    @DisplayName("Tool call accumulation: name + args across two chunks yields TOOL_CALL then DONE")
    void toolCallAccumulation() throws Exception {
        enqueueSse(
                "data: " + toolCallChunk(0, "search", ""),
                "data: " + toolCallChunk(0, "", "{\"q\":\"ai\"}"),
                "data: " + finishChunk("tool_calls"),
                "data: [DONE]");

        List<LLMResponse> events = collect(BASIC_REQUEST, "sk-test");

        assertEquals(2, events.size());
        assertEquals(LLMResponse.TOOL_CALL, events.get(0).getType());
        assertEquals("search", events.get(0).getToolName());
        assertEquals("ai", events.get(0).getToolInput().get("q"));
        assertEquals(LLMResponse.DONE, events.get(1).getType());
    }

    @Test
    @DisplayName("Non-2xx HTTP response emits exactly one ERROR, no exception thrown")
    void non2xxEmitsError() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

        List<LLMResponse> events = collect(BASIC_REQUEST, "sk-test");

        assertEquals(1, events.size());
        assertEquals(LLMResponse.ERROR, events.get(0).getType());
    }

    @Test
    @DisplayName("Missing/blank apiKey emits ERROR immediately, no HTTP request made")
    void blankApiKeyEmitsErrorWithoutNetworkCall() throws Exception {
        List<LLMResponse> events = collect(BASIC_REQUEST, "");

        assertEquals(1, events.size());
        assertEquals(LLMResponse.ERROR, events.get(0).getType());
        // MockWebServer records every received request — none should have been made
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("[DONE] sentinel without prior content emits only DONE")
    void doneWithoutContent() {
        enqueueSse("data: [DONE]");

        List<LLMResponse> events = collect(BASIC_REQUEST, "sk-test");

        assertEquals(1, events.size());
        assertEquals(LLMResponse.DONE, events.get(0).getType());
    }

    @Test
    @DisplayName("Stream ends without [DONE] sentinel: tokens arrive and exactly one DONE emitted")
    void streamEndsWithoutDoneSentinel() throws Exception {
        // Body has two token chunks but NO trailing "data: [DONE]" line.
        // Some local/custom providers (llama.cpp, etc.) close the connection without it.
        String body =
                "data: " + tokenChunk("Hello") + "\n" + "data: " + tokenChunk(" world") + "\n";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "text/event-stream")
                        .setBody(body));

        List<LLMResponse> events = collect(BASIC_REQUEST, "sk-test");

        // Two TOKEN events followed by exactly one DONE — the post-loop path
        assertEquals(3, events.size());
        assertEquals(LLMResponse.TOKEN, events.get(0).getType());
        assertEquals("Hello", events.get(0).getContent());
        assertEquals(LLMResponse.TOKEN, events.get(1).getType());
        assertEquals(" world", events.get(1).getContent());
        assertEquals(LLMResponse.DONE, events.get(2).getType());
    }

    @Test
    @DisplayName("Tool calls with no [DONE]: flushed once via post-loop, not double-emitted")
    void toolCallWithoutDoneSentinelNotDoubleEmitted() throws Exception {
        // finish_reason=tool_calls flushes the batch; stream then ends without [DONE].
        // Post-loop must emit exactly one DONE and must NOT re-emit the already-flushed tool call.
        String body =
                "data: "
                        + toolCallChunk(0, "search", "")
                        + "\n"
                        + "data: "
                        + toolCallChunk(0, "", "{\"q\":\"ai\"}")
                        + "\n"
                        + "data: "
                        + finishChunk("tool_calls")
                        + "\n";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "text/event-stream")
                        .setBody(body));

        List<LLMResponse> events = collect(BASIC_REQUEST, "sk-test");

        // Exactly: one TOOL_CALL then one DONE — no duplicate TOOL_CALL, no duplicate DONE
        assertEquals(2, events.size());
        assertEquals(LLMResponse.TOOL_CALL, events.get(0).getType());
        assertEquals("search", events.get(0).getToolName());
        assertEquals(LLMResponse.DONE, events.get(1).getType());
    }
}
