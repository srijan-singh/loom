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

import com.loom.domain.AgentExecution;
import com.loom.domain.WorkspaceKnowledge;
import com.loom.storage.repository.WorkspaceKnowledgeRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Persists the final output of an {@link AgentExecution} as a
 * {@link WorkspaceKnowledge} record in the workspace knowledge base.
 *
 * <p>Title format: {@code "<agentName> — <ISO timestamp>"}</p>
 */
public class ReportWriter {

    private static final DateTimeFormatter TITLE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final WorkspaceKnowledgeRepository knowledgeRepository;

    public ReportWriter(WorkspaceKnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    /**
     * Creates and persists a {@link WorkspaceKnowledge} entry from a completed
     * agent execution.
     *
     * @param execution   the finished {@link AgentExecution} (must have a session workspaceId)
     * @param agentName   display name of the agent (used in the title)
     * @param workspaceId the workspace that owns this knowledge
     * @param output      the full text output produced by the agent
     * @return the saved {@link WorkspaceKnowledge} record
     */
    public WorkspaceKnowledge write(AgentExecution execution,
                                   String agentName,
                                   String workspaceId,
                                   String output) {
        String title = agentName + " — " + TITLE_FMT.format(Instant.ofEpochMilli(
                execution.getCompletedAt() != null
                        ? execution.getCompletedAt()
                        : System.currentTimeMillis()));

        WorkspaceKnowledge knowledge = new WorkspaceKnowledge();
        knowledge.setWorkspaceId(workspaceId);
        knowledge.setSourceExecutionId(execution.getId());
        knowledge.setTitle(title);
        knowledge.setContent(output);
        knowledge.setCreatedAt(System.currentTimeMillis());

        knowledgeRepository.save(knowledge);
        return knowledge;
    }
}
