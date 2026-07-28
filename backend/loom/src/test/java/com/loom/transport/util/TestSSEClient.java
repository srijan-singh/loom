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

package com.loom.transport.util;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Minimal SSE test client backed by OkHttp.
 * Uses a background thread with a synchronous call so we can signal
 * "connected" the moment the 200 header arrives.
 */
public class TestSSEClient {

    private final int clientId;
    private final String url;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    /** Latches down to 0 as soon as the 200 response header is received. */
    private final CountDownLatch connectedLatch = new CountDownLatch(1);

    private volatile boolean connected = false;
    private volatile Call call;

    public boolean isConnected() { return connected; }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)  // infinite — SSE is long-lived
            .build();

    public TestSSEClient(int clientId, int port) {
        this.clientId = clientId;
        this.url = "http://localhost:" + port + "/events";
    }

    /**
     * Starts the SSE connection on a daemon thread and blocks until the
     * server sends back a 200 (or 3 s elapses).
     */
    public void connect() throws InterruptedException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build();

        call = HTTP.newCall(request);

        Thread thread = new Thread(() -> {
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Client " + clientId + " unexpected HTTP " + response.code());
                    return;
                }
                // Headers received — signal connected
                connected = true;
                connectedLatch.countDown();

                ResponseBody body = response.body();
                if (body == null) return;

                okio.BufferedSource source = body.source();
                while (!call.isCanceled()) {
                    String line = source.readUtf8Line();
                    if (line == null) break;
                    if (line.startsWith("data: ")) {
                        messageQueue.offer(line.substring(6));
                    }
                }
            } catch (IOException e) {
                if (!call.isCanceled()) {
                    System.err.println("Client " + clientId + " error: " + e.getMessage());
                }
            } finally {
                connected = false;
                connectedLatch.countDown(); // unblock connect() if we never got a 200
            }
        }, "sse-client-" + clientId);

        thread.setDaemon(true);
        thread.start();

        // Block until connected or timeout
        connectedLatch.await(3, TimeUnit.SECONDS);
    }

    public String waitForMessage(int timeoutSeconds) throws InterruptedException {
        return messageQueue.poll(timeoutSeconds, TimeUnit.SECONDS);
    }

    public void disconnect() {
        connected = false;
        if (call != null) call.cancel();
    }
}
