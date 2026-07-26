package com.loom.domain;

import java.util.UUID;

public class AgentExecution {

    private String id;
    private String sessionId;
    private String nodeId;
    private String agentDefinitionId;
    private AgentExecutionStatus status;
    private String inputContext;
    private String output;
    private String report;
    private long startedAt;
    private Long completedAt;

    public AgentExecution() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getAgentDefinitionId() { return agentDefinitionId; }
    public void setAgentDefinitionId(String agentDefinitionId) { this.agentDefinitionId = agentDefinitionId; }

    public AgentExecutionStatus getStatus() { return status; }
    public void setStatus(AgentExecutionStatus status) { this.status = status; }

    public String getInputContext() { return inputContext; }
    public void setInputContext(String inputContext) { this.inputContext = inputContext; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
}
