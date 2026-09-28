import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Persistent left-rail navigation shell.
/// Rail is 72 px wide — icon + short label stacked vertically.
/// Watches [engineStateProvider] and shows a full-window overlay when the
/// engine is starting or unreachable — content is never shown with stale counts.
class AppShell extends ConsumerWidget {
  final Widget child;

  const AppShell({required this.child, super.key});

  static const _items = [
    _NavItem(icon: Icons.grid_view_rounded, label: 'Dashboard', route: Routes.dashboard),
    _NavItem(icon: Icons.collections_bookmark_rounded, label: 'Library', route: Routes.library),
    _NavItem(icon: Icons.workspaces_rounded, label: 'Workspaces', route: Routes.workspaces),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colors = LoomColors.of(context);
    final location = GoRouterState.of(context).uri.path;
    final engineAsync = ref.watch(engineStateProvider);

    return Scaffold(
      backgroundColor: colors.bg,
      body: Stack(
        children: [
          Row(
            children: [
              // ----------------------------------------------------------------
              // Left rail — 72 px, icon + label stacked
              // ----------------------------------------------------------------
              SizedBox(
                width: LoomSize.rail,
                child: Container(
                  color: colors.rail,
                  child: Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        child: _LogoMark(colors: colors),
                      ),
                      const SizedBox(height: 4),
                      for (final item in _items)
                        _RailItem(
                          item: item,
                          active: location.startsWith(item.route),
                          colors: colors,
                        ),
                      const Spacer(),
                      _RailItem(
                        item: const _NavItem(
                          icon: Icons.settings_rounded,
                          label: 'Settings',
                          route: Routes.settings,
                        ),
                        active: location.startsWith(Routes.settings),
                        colors: colors,
                      ),
                      // Engine status dot
                      _EngineIndicator(
                          state: engineAsync.valueOrNull, colors: colors),
                      const SizedBox(height: 12),
                    ],
                  ),
                ),
              ),
              VerticalDivider(width: 1, thickness: 1, color: colors.line),
              Expanded(child: child),
            ],
          ),

          // ----------------------------------------------------------------
          // Engine overlays — sit on top of the entire shell
          // ----------------------------------------------------------------
          if (engineAsync.valueOrNull == EngineState.starting)
            _EngineStartingOverlay(colors: colors),
          if (engineAsync.valueOrNull == EngineState.unreachable)
            _EngineDownOverlay(
              colors: colors,
              logPath: ref.read(engineProcessServiceProvider).logPath,
              onRetry: () => ref.invalidate(engineStateProvider),
            ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Engine status indicator — bottom of rail
// ---------------------------------------------------------------------------

class _EngineIndicator extends StatelessWidget {
  final EngineState? state;
  final LoomColors colors;
  const _EngineIndicator({required this.state, required this.colors});

  @override
  Widget build(BuildContext context) {
    final (dotColor, label) = switch (state) {
      EngineState.running => (colors.success, 'Engine'),
      EngineState.starting => (colors.warning, 'Starting'),
      EngineState.unreachable => (colors.danger, 'Engine'),
      null => (colors.warning, 'Starting'),
    };

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: dotColor,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(height: 4),
          Text(label,
              style: TextStyle(fontSize: 11, color: colors.ink3)),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Engine starting overlay
// ---------------------------------------------------------------------------

class _EngineStartingOverlay extends StatelessWidget {
  final LoomColors colors;
  const _EngineStartingOverlay({required this.colors});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: colors.surface,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: 28,
              height: 28,
              child: CircularProgressIndicator(
                strokeWidth: 3,
                color: colors.accent,
              ),
            ),
            const SizedBox(height: 16),
            Text('Starting the Loom engine…',
                style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: colors.ink)),
            const SizedBox(height: 6),
            Text('This usually takes a few seconds.',
                style: TextStyle(color: colors.ink2)),
            const SizedBox(height: 12),
            SizedBox(
              width: 200,
              child: _ProgressBar(colors: colors),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProgressBar extends StatefulWidget {
  final LoomColors colors;
  const _ProgressBar({required this.colors});

  @override
  State<_ProgressBar> createState() => _ProgressBarState();
}

class _ProgressBarState extends State<_ProgressBar>
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
    _anim = Tween<double>(begin: -0.4, end: 1.4).animate(
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
    return Container(
      height: 4,
      decoration: BoxDecoration(
        color: widget.colors.line,
        borderRadius: BorderRadius.circular(2),
      ),
      clipBehavior: Clip.hardEdge,
      child: AnimatedBuilder(
        animation: _anim,
        builder: (_, __) => FractionallySizedBox(
          alignment: Alignment(_anim.value * 2 - 1, 0),
          widthFactor: 0.4,
          child: Container(
            decoration: BoxDecoration(
              color: widget.colors.accent,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Engine down / unreachable overlay
// ---------------------------------------------------------------------------

class _EngineDownOverlay extends StatelessWidget {
  final LoomColors colors;
  final String? logPath;
  final VoidCallback onRetry;
  const _EngineDownOverlay(
      {required this.colors, required this.logPath, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: colors.surface,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: colors.dangerTint,
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.warning_amber_rounded,
                  size: 22, color: colors.danger),
            ),
            const SizedBox(height: 16),
            Text("Can't reach the Loom engine",
                style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: colors.ink)),
            const SizedBox(height: 6),
            Text(
              'It may have stopped. Retry, or check the logs for details.',
              style: TextStyle(color: colors.ink2),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                GestureDetector(
                  onTap: onRetry,
                  child: Container(
                    height: LoomSize.control,
                    padding:
                        const EdgeInsets.symmetric(horizontal: 16),
                    decoration: BoxDecoration(
                      color: colors.accent,
                      borderRadius:
                          BorderRadius.circular(LoomRadius.control),
                    ),
                    child: Center(
                      child: Text('Retry',
                          style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                              color: colors.onAccent)),
                    ),
                  ),
                ),
                if (logPath != null) ...[
                  const SizedBox(width: 8),
                  GestureDetector(
                    onTap: () {
                      Clipboard.setData(ClipboardData(text: logPath!));
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Log path copied: $logPath'),
                          duration: const Duration(seconds: 3),
                        ),
                      );
                    },
                    child: Container(
                      height: LoomSize.control,
                      padding:
                          const EdgeInsets.symmetric(horizontal: 16),
                      decoration: BoxDecoration(
                        border: Border.all(color: colors.lineStrong),
                        borderRadius:
                            BorderRadius.circular(LoomRadius.control),
                      ),
                      child: Center(
                        child: Text('View logs',
                            style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
                                color: colors.ink2)),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Navigation items
// ---------------------------------------------------------------------------

class _LogoMark extends StatelessWidget {
  final LoomColors colors;
  const _LogoMark({required this.colors});

  @override
  Widget build(BuildContext context) => Container(
        width: 32,
        height: 32,
        decoration: BoxDecoration(
          color: colors.accentTint,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Center(
          child: Text(
            'L',
            style: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w700,
              color: colors.accent,
            ),
          ),
        ),
      );
}

class _NavItem {
  final IconData icon;
  final String label;
  final String route;
  const _NavItem(
      {required this.icon, required this.label, required this.route});
}

class _RailItem extends StatelessWidget {
  final _NavItem item;
  final bool active;
  final LoomColors colors;

  const _RailItem(
      {required this.item, required this.active, required this.colors});

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: item.label,
      preferBelow: false,
      child: GestureDetector(
        onTap: () => context.go(item.route),
        child: Container(
          width: double.infinity,
          margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: active ? colors.accentTint : Colors.transparent,
            borderRadius: BorderRadius.circular(LoomRadius.control),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                item.icon,
                size: 20,
                color: active ? colors.accentInk : colors.ink3,
              ),
              const SizedBox(height: 3),
              Text(
                item.label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight:
                      active ? FontWeight.w500 : FontWeight.w400,
                  color: active ? colors.accentInk : colors.ink3,
                ),
                textAlign: TextAlign.center,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
