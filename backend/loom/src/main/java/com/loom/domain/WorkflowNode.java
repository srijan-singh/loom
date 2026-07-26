package com.loom.domain;

public class WorkflowNode {

    private String id;
    private String label;
    private NodeType nodeType;
    private String agentDefinitionId;
    private double positionX;
    private double positionY;

    public WorkflowNode() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public NodeType getNodeType() { return nodeType; }
    public void setNodeType(NodeType nodeType) { this.nodeType = nodeType; }

    public String getAgentDefinitionId() { return agentDefinitionId; }
    public void setAgentDefinitionId(String agentDefinitionId) { this.agentDefinitionId = agentDefinitionId; }

    public double getPositionX() { return positionX; }
    public void setPositionX(double positionX) { this.positionX = positionX; }

    public double getPositionY() { return positionY; }
    public void setPositionY(double positionY) { this.positionY = positionY; }
}
