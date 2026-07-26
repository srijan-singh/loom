package com.loom.domain;

public class WorkflowEdge {

    private String id;
    private String fromNodeId;
    private String toNodeId;
    private EdgeCondition condition;

    public WorkflowEdge() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromNodeId() { return fromNodeId; }
    public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }

    public String getToNodeId() { return toNodeId; }
    public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }

    public EdgeCondition getCondition() { return condition; }
    public void setCondition(EdgeCondition condition) { this.condition = condition; }
}
