import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/mcp_card.dart';
import 'package:loom_ui/ui/screens/library/add_mcp_bottom_sheet.dart';
import 'package:loom_ui/ui/screens/library/mcps_tab_view.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// In-memory [McpsNotifier] stub that records calls.
class _FakeMcpsNotifier extends McpsNotifier {
  _FakeMcpsNotifier(List<MCPConnection> mcps, this.created)
      : _mcps = List.from(mcps);

  final List<MCPConnection> _mcps;
  final List<Map<String, dynamic>> created;
  final List<(String, String)> statusUpdates = [];

  @override
  Future<List<MCPConnection>> build() async => List.from(_mcps);

  @override
  Future<MCPConnection> createMcp(Map<String, dynamic> payload) async {
    created.add(payload);
    final mcp = MCPConnection(
      id: 'new-id',
      name: payload['name'] as String,
      type: payload['type'] as String,
      createdAt: 0,
    );
    _mcps.add(mcp);
    ref.invalidateSelf();
    return mcp;
  }

  @override
  Future<void> updateMcpStatus(String id, String status) async {
    statusUpdates.add((id, status));
    final idx = _mcps.indexWhere((m) => m.id == id);
    if (idx != -1) {
      _mcps[idx] = _mcps[idx].copyWith(
        status: status == 'CONNECTED'
            ? MCPStatus.CONNECTED
            : MCPStatus.DISCONNECTED,
      );
    }
    ref.invalidateSelf();
  }

  @override
  Future<void> deleteMcp(String id) async {
    _mcps.removeWhere((m) => m.id == id);
    ref.invalidateSelf();
  }
}

const _mcp1 = MCPConnection(
  id: 'm-1',
  name: 'Repo Server',
  type: 'GITHUB',
  status: MCPStatus.CONNECTED,
  tools: ['search', 'read'],
  detail: 'github.com/acme',
  createdAt: 0,
);

const _mcp2 = MCPConnection(
  id: 'm-2',
  name: 'Local Files',
  type: 'FILESYSTEM',
  status: MCPStatus.DISCONNECTED,
  createdAt: 1,
);

Widget _build(Widget home, _FakeMcpsNotifier notifier) {
  return ProviderScope(
    overrides: [mcpsProvider.overrideWith(() => notifier)],
    child: MaterialApp(
      theme: LoomTheme.light,
      home: Scaffold(body: home),
    ),
  );
}

Color _dotColor(WidgetTester tester, Finder card) {
  final dot = find.descendant(
    of: card,
    matching: find.byKey(const ValueKey('mcp-status-dot')),
  );
  final box = tester.widget<Container>(dot).decoration as BoxDecoration;
  return box.color!;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('McpsTabView', () {
    testWidgets('renders list of MCP cards with status indicators',
        (tester) async {
      await tester.pumpWidget(
          _build(const McpsTabView(), _FakeMcpsNotifier([_mcp1, _mcp2], [])));
      await tester.pumpAndSettle();

      expect(find.byType(MCPCard), findsNWidgets(2));
      expect(find.text('Repo Server'), findsOneWidget);
      expect(find.text('Local Files'), findsOneWidget);

      const colors = LoomColors.light;
      expect(_dotColor(tester, find.byKey(const ValueKey('mcp-card-m-1'))),
          colors.success);
      expect(_dotColor(tester, find.byKey(const ValueKey('mcp-card-m-2'))),
          colors.ink3);
    });

    testWidgets('displays EmptyState when no MCPs are configured',
        (tester) async {
      await tester.pumpWidget(
          _build(const McpsTabView(), _FakeMcpsNotifier([], [])));
      await tester.pumpAndSettle();

      expect(find.byType(EmptyState), findsOneWidget);
      expect(find.text('No MCP connections'), findsOneWidget);
      expect(find.text('Add your first connection.'), findsOneWidget);
    });

    testWidgets('tapping "Add Connection" displays AddMCPBottomSheet',
        (tester) async {
      await tester.pumpWidget(
          _build(const McpsTabView(), _FakeMcpsNotifier([_mcp1], [])));
      await tester.pumpAndSettle();

      expect(find.byType(AddMCPBottomSheet), findsNothing);

      await tester.tap(find.byKey(const ValueKey('add-mcp-btn')));
      await tester.pumpAndSettle();

      expect(find.byType(AddMCPBottomSheet), findsOneWidget);
    });

    testWidgets('disconnecting an MCP updates status to DISCONNECTED',
        (tester) async {
      final notifier = _FakeMcpsNotifier([_mcp1], []);
      await tester.pumpWidget(_build(const McpsTabView(), notifier));
      await tester.pumpAndSettle();

      expect(_dotColor(tester, find.byType(MCPCard)),
          LoomColors.light.success);

      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('mcp-disconnect')));
      await tester.pumpAndSettle();

      expect(notifier.statusUpdates, [('m-1', 'DISCONNECTED')]);
      expect(_dotColor(tester, find.byType(MCPCard)), LoomColors.light.ink3);
    });

    testWidgets('shows ConfirmDialog and deletes MCP on confirmation',
        (tester) async {
      await tester.pumpWidget(
          _build(const McpsTabView(), _FakeMcpsNotifier([_mcp1], [])));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('mcp-delete')));
      await tester.pumpAndSettle();

      expect(find.text('Delete connection?'), findsOneWidget);

      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      expect(find.byType(MCPCard), findsNothing);
      expect(find.byType(EmptyState), findsOneWidget);
    });
  });

  group('AddMCPBottomSheet', () {
    Future<void> openSheet(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('add-mcp-btn')));
      await tester.pumpAndSettle();
    }

    testWidgets('updates config label when switching type dropdown',
        (tester) async {
      await tester.pumpWidget(
          _build(const McpsTabView(), _FakeMcpsNotifier([_mcp1], [])));
      await tester.pumpAndSettle();
      await openSheet(tester);

      expect(find.text('Repository URL or org'), findsOneWidget);

      const expected = {
        'FIGMA': 'Team or file URL',
        'FILESYSTEM': 'Local path',
        'CUSTOM': 'Config JSON',
        'GITHUB': 'Repository URL or org',
      };
      for (final entry in expected.entries) {
        await tester.tap(find.byKey(const ValueKey('mcp-type-dropdown')));
        await tester.pumpAndSettle();
        await tester.tap(find.text(entry.key).last);
        await tester.pumpAndSettle();
        expect(find.text(entry.value), findsOneWidget);
      }
    });

    testWidgets('submitting invokes createMcp and dismisses sheet',
        (tester) async {
      final created = <Map<String, dynamic>>[];
      await tester.pumpWidget(
          _build(const McpsTabView(), _FakeMcpsNotifier([], created)));
      await tester.pumpAndSettle();
      await openSheet(tester);

      await tester.enterText(
          find.byKey(const ValueKey('mcp-name-field')), 'My Repo');
      await tester.enterText(find.byKey(const ValueKey('mcp-config-field')),
          'https://github.com/acme');
      await tester.tap(find.byKey(const ValueKey('mcp-submit-btn')));
      await tester.pumpAndSettle();

      expect(created, [
        {
          'name': 'My Repo',
          'type': 'GITHUB',
          'config': {'url': 'https://github.com/acme'},
        }
      ]);
      expect(find.byType(AddMCPBottomSheet), findsNothing);
      expect(find.text('My Repo'), findsOneWidget);
    });

    testWidgets('requires a name and does not submit when empty',
        (tester) async {
      final created = <Map<String, dynamic>>[];
      await tester.pumpWidget(
          _build(const McpsTabView(), _FakeMcpsNotifier([], created)));
      await tester.pumpAndSettle();
      await openSheet(tester);

      await tester.tap(find.byKey(const ValueKey('mcp-submit-btn')));
      await tester.pumpAndSettle();

      expect(created, isEmpty);
      expect(find.text('Name is required'), findsOneWidget);
      expect(find.byType(AddMCPBottomSheet), findsOneWidget);
    });
  });
}
