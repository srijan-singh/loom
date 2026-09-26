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
package com.loom.engine.graph;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.loom.domain.WorkflowEdge;
import com.loom.domain.WorkflowNode;

/**
 * Extends {@link ExecutionPlan} with the four supervisor-topology fields needed by {@code
 * WorkflowEngine#runSupervisor}.
 */
@Getter
public class SupervisorExecutionPlan extends ExecutionPlan {

    private final WorkflowNode supervisorNode;
    private final List<WorkflowNode> workerNodes;

    /** supervisorId → list of worker nodes the supervisor can dispatch to. */
    private final Map<String, List<WorkflowNode>> dispatchEdges;

    /** workerId → the supervisor node that the worker reports back to. */
    private final Map<String, WorkflowNode> reportBackEdges;

    private final int maxIterations;

    public SupervisorExecutionPlan(
            List<WorkflowNode> orderedNodes,
            Map<String, WorkflowNode> nodeById,
            Map<String, List<WorkflowEdge>> edgesByFromNodeId,
            WorkflowNode supervisorNode,
            List<WorkflowNode> workerNodes,
            Map<String, List<WorkflowNode>> dispatchEdges,
            Map<String, WorkflowNode> reportBackEdges,
            int maxIterations) {
        super(orderedNodes, nodeById, edgesByFromNodeId);
        this.supervisorNode = supervisorNode;
        this.workerNodes = Collections.unmodifiableList(workerNodes);
        this.dispatchEdges = Collections.unmodifiableMap(dispatchEdges);
        this.reportBackEdges = Collections.unmodifiableMap(reportBackEdges);
        this.maxIterations = maxIterations;
    }
}
