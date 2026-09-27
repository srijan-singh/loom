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
package com.loom.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.loom.domain.WorkflowCreatedBy;
import com.loom.domain.WorkflowDefinition;
import com.loom.domain.WorkflowType;
import com.loom.storage.repository.WorkflowRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkflowRepositoryTest {

    private static Path dbFile;
    private static DatabaseManager db;
    private static WorkflowRepository workflowRepo;

    @BeforeAll
    static void setUp() throws IOException {
        dbFile = Files.createTempFile("loom-wf-repo-test-", ".db");
        dbFile.toFile().deleteOnExit();
        db = new DatabaseManager(dbFile.toAbsolutePath().toString());
        TestFixtures.load(db);
        workflowRepo = new WorkflowRepository(db);
    }

    @AfterAll
    static void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    @Test
    void saveAndFindByIdPreservesMetadata() {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setName("Metadata Test Workflow");
        wf.setType(WorkflowType.CHAIN);
        wf.setCreatedBy(WorkflowCreatedBy.USER);
        wf.setCreatedAt(1000L);
        wf.setUpdatedAt(2000L);
        wf.setMetadata(Map.of("category", "testing", "version", 2, "enabled", true));

        workflowRepo.save(wf);

        Optional<WorkflowDefinition> retrieved = workflowRepo.findById(wf.getId());
        assertThat(retrieved).isPresent();
        WorkflowDefinition loaded = retrieved.get();
        assertThat(loaded.getId()).isEqualTo(wf.getId());
        assertThat(loaded.getName()).isEqualTo("Metadata Test Workflow");
        assertThat(loaded.getMetadata()).isNotNull();
        assertThat(loaded.getMetadata().get("category")).isEqualTo("testing");
        assertThat(loaded.getMetadata().get("version")).isEqualTo(2);
        assertThat(loaded.getMetadata().get("enabled")).isEqualTo(true);
    }

    @Test
    void saveWithNullMetadataDeserializesToEmptyMap() {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setName("Null Metadata Workflow");
        wf.setType(WorkflowType.CHAIN);
        wf.setCreatedBy(WorkflowCreatedBy.USER);
        wf.setMetadata(null);

        workflowRepo.save(wf);

        Optional<WorkflowDefinition> retrieved = workflowRepo.findById(wf.getId());
        assertThat(retrieved).isPresent();
        WorkflowDefinition loaded = retrieved.get();
        assertThat(loaded.getMetadata()).isNotNull().isEmpty();
    }
}
