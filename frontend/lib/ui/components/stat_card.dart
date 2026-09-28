import 'package:flutter/material.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Displays a numeric count with a label — used in the Dashboard stats row.
/// Supports three display states: loading (skeleton), error (retry), data.
class StatCard extends StatelessWidget {
  final String label;
  final int? count;
  final bool loading;
  final bool hasError;
  final VoidCallback? onRetry;
  final VoidCallback? onTap;

  const StatCard({
    required this.label,
    this.count,
    this.loading = false,
    this.hasError = false,
    this.onRetry,
    this.onTap,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    Widget body;
    if (loading) {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SkeletonLoader(width: 48, height: 28, colors: colors),
          const SizedBox(height: 6),
          SkeletonLoader(width: 80, height: 12, colors: colors),
        ],
      );
    } else if (hasError) {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: TextStyle(fontSize: 12.5, color: colors.ink2)),
          const SizedBox(height: 4),
          Text('Couldn\'t load',
              style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: colors.danger)),
          if (onRetry != null) ...[
            const SizedBox(height: 4),
            GestureDetector(
              onTap: onRetry,
              child: Text('Retry',
                  style: TextStyle(
                      fontSize: 12.5,
                      color: colors.accentInk,
                      decoration: TextDecoration.underline,
                      decorationColor: colors.accentInk)),
            ),
          ],
        ],
      );
    } else {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: TextStyle(fontSize: 12.5, color: colors.ink2)),
          const SizedBox(height: 2),
          Text(
            count != null ? '$count' : '—',
            style: TextStyle(
              fontSize: 28,
              height: 1.15,
              fontWeight: FontWeight.w600,
              letterSpacing: -0.01 * 28,
              color: colors.ink,
            ),
          ),
        ],
      );
    }

    return GestureDetector(
      onTap: !loading && !hasError ? onTap : null,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: colors.sunken,
          borderRadius: BorderRadius.circular(LoomRadius.card),
          border: Border.all(color: Colors.transparent),
        ),
        child: body,
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Skeleton shimmer — shared across the app.
// ---------------------------------------------------------------------------

class SkeletonLoader extends StatefulWidget {
  final double width;
  final double height;
  final LoomColors? colors;

  const SkeletonLoader({
    required this.width,
    required this.height,
    this.colors,
    super.key,
  });

  @override
  State<SkeletonLoader> createState() => _SkeletonLoaderState();
}

class _SkeletonLoaderState extends State<SkeletonLoader>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..repeat();
    _anim = Tween<double>(begin: -1, end: 2).animate(
      CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = widget.colors ?? LoomColors.of(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(LoomRadius.control),
      child: AnimatedBuilder(
        animation: _anim,
        builder: (_, __) => Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.centerLeft,
              end: Alignment.centerRight,
              stops: [
                (_anim.value - 0.3).clamp(0.0, 1.0),
                _anim.value.clamp(0.0, 1.0),
                (_anim.value + 0.3).clamp(0.0, 1.0),
              ],
              colors: [
                colors.line,
                colors.lineStrong,
                colors.line,
              ],
            ),
          ),
        ),
      ),
    );
  }
}
