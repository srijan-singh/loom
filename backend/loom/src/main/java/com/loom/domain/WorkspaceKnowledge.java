package com.loom.domain;

import java.util.List;
import java.util.UUID;

public class WorkspaceKnowledge {

    private String id;
    private String workspaceId;
    private String sourceExecutionId;
    private String title;
    private String content;
    private List<String> tags;
    private long createdAt;

    public WorkspaceKnowledge() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getSourceExecutionId() { return sourceExecutionId; }
    public void setSourceExecutionId(String sourceExecutionId) { this.sourceExecutionId = sourceExecutionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
