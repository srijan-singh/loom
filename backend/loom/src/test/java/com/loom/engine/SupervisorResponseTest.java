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
package com.loom.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SupervisorResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialiseDispatchWithWorkers() throws Exception {
        String json =
                "{\"done\":false,\"dispatchTo\":[\"worker-a\",\"worker-b\"],\"message\":\"go\"}";
        SupervisorResponse resp = mapper.readValue(json, SupervisorResponse.class);

        assertThat(resp.isDone()).isFalse();
        assertThat(resp.getDispatchTo()).containsExactly("worker-a", "worker-b");
        assertThat(resp.getMessage()).isEqualTo("go");
    }

    @Test
    void deserialiseFinishedResponse() throws Exception {
        String json = "{\"done\":true,\"dispatchTo\":[],\"message\":\"finished\"}";
        SupervisorResponse resp = mapper.readValue(json, SupervisorResponse.class);

        assertThat(resp.isDone()).isTrue();
        assertThat(resp.getDispatchTo()).isEmpty();
        assertThat(resp.getMessage()).isEqualTo("finished");
    }

    @Test
    void deserialiseEmptyObjectGracefully() throws Exception {
        String json = "{}";
        SupervisorResponse resp = mapper.readValue(json, SupervisorResponse.class);

        assertThat(resp.isDone()).isFalse();
        // dispatchTo will be null (Jackson default) or empty — either is acceptable
        if (resp.getDispatchTo() != null) {
            assertThat(resp.getDispatchTo()).isEmpty();
        } else {
            assertThat(resp.getDispatchTo()).isNull();
        }
        assertThat(resp.getMessage()).isNull();
    }
}
