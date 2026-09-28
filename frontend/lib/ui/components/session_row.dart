import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:loom_ui/models/session.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Single row in the Recent Sessions list.
class SessionRow extends StatelessWidget {
  final Session session;
  final String workflowName;
  final VoidCallback? onTap;

  const SessionRow({
    required this.session,
    required this.workflowName,
    this.onTap,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 11, horizontal: 8),
        decoration: BoxDecoration(
          border: Border(
            top: BorderSide(color: colors.line, width: 1),
          ),
        ),
        child: Row(
          children: [
            // Status word + dot
            SizedBox(
              width: 104,
              child: Row(
                children: [
                  _StatusDot(session.status, colors: colors),
                  const SizedBox(width: 6),
                  Text(
                    _statusLabel(session.status),
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w500,
                      color: _statusColor(session.status, colors),
                    ),
                  ),
                ],
              ),
            ),
            // Name + agent count
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(workflowName,
                      style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                          color: colors.ink)),
                  Text(
                    _agentCountLabel(session.agentCount),
                    style: TextStyle(fontSize: 12, color: colors.ink3),
                  ),
                ],
              ),
            ),
            // Relative time
            Text(
              _relativeTime(session.startedAt),
              style: TextStyle(fontSize: 12.5, color: colors.ink3),
            ),
          ],
        ),
      ),
    );
  }

  static String _statusLabel(SessionStatus s) => switch (s) {
        SessionStatus.running => 'running',
        SessionStatus.completed => 'completed',
        SessionStatus.failed => 'failed',
        SessionStatus.partial => 'partial',
        SessionStatus.created => 'created',
      };

  static Color _statusColor(SessionStatus s, LoomColors c) => switch (s) {
        SessionStatus.running => c.accentInk,
        SessionStatus.completed => c.success,
        SessionStatus.failed => c.danger,
        SessionStatus.partial => c.warning,
        SessionStatus.created => c.ink3,
      };

  static String _agentCountLabel(int? count) {
    if (count == null || count == 0) return '';
    return '$count ${count == 1 ? 'agent' : 'agents'}';
  }

  static String _relativeTime(int epochMs) {
    final diff = DateTime.now()
        .difference(DateTime.fromMillisecondsSinceEpoch(epochMs));
    if (diff.inSeconds < 60) return 'just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return DateFormat('MMM d')
        .format(DateTime.fromMillisecondsSinceEpoch(epochMs));
  }
}

class _StatusDot extends StatelessWidget {
  final SessionStatus status;
  final LoomColors colors;
  const _StatusDot(this.status, {required this.colors});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 8,
      height: 8,
      decoration: BoxDecoration(
        color: _statusColor(status, colors),
        shape: BoxShape.circle,
      ),
    );
  }

  static Color _statusColor(SessionStatus s, LoomColors c) => switch (s) {
        SessionStatus.running => c.accent,
        SessionStatus.completed => c.success,
        SessionStatus.failed => c.danger,
        SessionStatus.partial => c.warning,
        SessionStatus.created => c.ink3,
      };
}
