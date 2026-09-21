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

import java.util.List;

/**
 * Data carrier for the structured JSON response a supervisor agent emits each iteration.
 *
 * <p>Deserialised by {@code WorkflowEngine} via Jackson. Requires a no-arg constructor and standard
 * getters/setters.
 */
public class SupervisorResponse {

    private boolean done;
    private List<String> dispatchTo;
    private String message;

    /** No-arg constructor required for Jackson deserialisation. */
    public SupervisorResponse() {}

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public List<String> getDispatchTo() {
        return dispatchTo;
    }

    public void setDispatchTo(List<String> dispatchTo) {
        this.dispatchTo = dispatchTo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
