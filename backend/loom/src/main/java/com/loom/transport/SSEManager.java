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

package com.loom.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.event.WorkflowEvent;
import io.javalin.http.sse.SseClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class SSEManager {

    private final List<SseClient> clients = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Called by Javalin when a new client connects to /events
     */
    public void attach(SseClient client) {
        clients.add(client);

        client.onClose(() -> {
            clients.remove(client);
            log.info("Client disconnected. {} clients remaining", getClientCount());
        });

        log.info("Client connected. {} total clients", getClientCount());

        // Keep the SSE connection open until the client disconnects or
        // close() is called explicitly. Without this, Javalin closes the
        // response as soon as the handler returns.
        client.keepAlive();
    }

    /**
     * Send a WorkflowEvent to ALL connected clients
     */
    public void broadcast(WorkflowEvent event) {
        // Serialize the event to JSON string
        String eventMessage;
        try {
            // convert event to JSON string
            eventMessage = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            return; // Skip broadcasting this event
        }

        // Track failed clients
        List<SseClient> failedClients = new ArrayList<>();

        // Send data to all client
        clients.forEach(client -> {
            try {
                client.sendEvent(eventMessage);
            } catch (Exception e) {
                log.warn("Failed to send event to client: {}", e.getMessage());
                failedClients.add(client);
            }
        });

        // Remove failed clients
        if (!failedClients.isEmpty()) {
            clients.removeAll(failedClients);
            log.info("Removed {} disconnected clients. {} remaining", failedClients.size(), getClientCount());
        }
    }

    /**
     * Get current connection count
     */
    public int getClientCount() {
        return clients.size();
    }
}
