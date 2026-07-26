package com.loom.domain;

import java.util.List;
import java.util.UUID;

public class WorkflowDefinition {

    private String id;
    private String name;
    private WorkflowType type;
    private WorkflowCreatedBy createdBy;
    private List<WorkflowNode> nodes;
    private List<WorkflowEdge> edges;
    private long createdAt;
    private long updatedAt;

    public WorkflowDefinition() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public WorkflowType getType() { return type; }
    public void setType(WorkflowType type) { this.type = type; }

    public WorkflowCreatedBy getCreatedBy() { return createdBy; }
    public void setCreatedBy(WorkflowCreatedBy createdBy) { this.createdBy = createdBy; }

    public List<WorkflowNode> getNodes() { return nodes; }
    public void setNodes(List<WorkflowNode> nodes) { this.nodes = nodes; }

    public List<WorkflowEdge> getEdges() { return edges; }
    public void setEdges(List<WorkflowEdge> edges) { this.edges = edges; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
