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

package com.loom.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't serialize null fields
public class WorkflowEvent {

    /**
     * Type of event - maps to EventType enum
     */
    private EventType eventType;

    /**
     * Unique identifier for the workflow session
     */
    private String sessionId;

    /**
     * Optional: Specific workflow node this event relates to
     */
    @Nullable
    private String nodeId;

    /**
     * Optional: Specific agent execution this event relates to
     */
    @Nullable
    private String agentExecutionId;

    /**
     * Unix epoch milliseconds when this event occurred.
     * Serialized as "timestampMs" in JSON so the unit is unambiguous to consumers.
     */
    @JsonProperty("timestampMs")
    @Builder.Default
    private long timestampMs = System.currentTimeMillis();

    /**
     * Event-specific payload serialized as a JSON object.
     * Structure is event-type-specific; consumers should inspect {@code eventType}
     * before reading fields. Examples:
     * <pre>
     *   SESSION_STARTED : {"workflowName": "...", "userId": "..."}
     *   AGENT_TOKEN     : {"token": "Hello", "index": 0}
     *   NODE_FAILED     : {"error": "...", "retryable": true}
     * </pre>
     * Use {@code ObjectMapper.valueToTree(map)} to convert a plain Map when building.
     */
    @Nullable
    @Builder.Default
    private JsonNode data = NullNode.getInstance();
}
