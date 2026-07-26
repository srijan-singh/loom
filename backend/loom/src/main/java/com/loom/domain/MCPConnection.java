package com.loom.domain;

import java.util.Map;
import java.util.UUID;

public class MCPConnection {

    private String id;
    private String name;
    private String type;
    private Map<String, Object> config;
    private MCPStatus status;
    private long createdAt;

    public MCPConnection() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }

    public MCPStatus getStatus() { return status; }
    public void setStatus(MCPStatus status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
