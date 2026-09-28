/// Mirrors the SSE event types emitted by the Java engine.
enum WorkflowEventType {
  sessionStarted,
  sessionCompleted,
  sessionFailed,
  nodeWaiting,
  nodeQueued,
  nodeRunning,
  nodeCompleted,
  nodeFailed,
  agentToken,
  agentToolCall,
  agentToolResult,
  agentReportWritten,
  workflowStateChange,
  unknown,
}

WorkflowEventType _typeFromString(String? s) {
  switch (s) {
    case 'SESSION_STARTED':
      return WorkflowEventType.sessionStarted;
    case 'SESSION_COMPLETED':
      return WorkflowEventType.sessionCompleted;
    case 'SESSION_FAILED':
      return WorkflowEventType.sessionFailed;
    case 'NODE_WAITING':
      return WorkflowEventType.nodeWaiting;
    case 'NODE_QUEUED':
      return WorkflowEventType.nodeQueued;
    case 'NODE_RUNNING':
      return WorkflowEventType.nodeRunning;
    case 'NODE_COMPLETED':
      return WorkflowEventType.nodeCompleted;
    case 'NODE_FAILED':
      return WorkflowEventType.nodeFailed;
    case 'AGENT_TOKEN':
      return WorkflowEventType.agentToken;
    case 'AGENT_TOOL_CALL':
      return WorkflowEventType.agentToolCall;
    case 'AGENT_TOOL_RESULT':
      return WorkflowEventType.agentToolResult;
    case 'AGENT_REPORT_WRITTEN':
      return WorkflowEventType.agentReportWritten;
    case 'WORKFLOW_STATE_CHANGE':
      return WorkflowEventType.workflowStateChange;
    default:
      return WorkflowEventType.unknown;
  }
}

class WorkflowEvent {
  final WorkflowEventType type;
  final String? sessionId;
  final String? nodeId;
  final String? agentExecutionId;
  final int timestamp;
  final Map<String, dynamic>? data;

  const WorkflowEvent({
    required this.type,
    this.sessionId,
    this.nodeId,
    this.agentExecutionId,
    required this.timestamp,
    this.data,
  });

  factory WorkflowEvent.fromJson(Map<String, dynamic> json) => WorkflowEvent(
        type: _typeFromString(json['eventType'] as String?),
        sessionId: json['sessionId'] as String?,
        nodeId: json['nodeId'] as String?,
        agentExecutionId: json['agentExecutionId'] as String?,
        timestamp: (json['timestamp'] as num?)?.toInt() ?? 0,
        data: json['data'] as Map<String, dynamic>?,
      );
}
