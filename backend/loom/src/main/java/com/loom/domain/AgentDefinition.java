package com.loom.domain;

import java.util.List;
import java.util.UUID;

public class AgentDefinition {

    private String id;
    private String name;
    private String roleDescription;
    private String skillId;
    private List<String> allowedMcpIds;
    private long createdAt;
    private long updatedAt;

    public AgentDefinition() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRoleDescription() { return roleDescription; }
    public void setRoleDescription(String roleDescription) { this.roleDescription = roleDescription; }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }

    public List<String> getAllowedMcpIds() { return allowedMcpIds; }
    public void setAllowedMcpIds(List<String> allowedMcpIds) { this.allowedMcpIds = allowedMcpIds; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
