import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/markdown_preview.dart';
import 'package:loom_ui/ui/components/skill_card.dart';
import 'package:loom_ui/ui/screens/library/skill_editor_screen.dart';
import 'package:loom_ui/ui/screens/library/skills_tab_view.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// A minimal [AsyncNotifier] stub backed by an in-memory list.
class _FakeSkillsNotifier extends SkillsNotifier {
  _FakeSkillsNotifier(List<Skill> skills) : _skills = List.from(skills);

  final List<Skill> _skills;
  int createCount = 0;
  int updateCount = 0;

  @override
  Future<List<Skill>> build() async => _skills;

  @override
  Future<void> deleteSkill(String id) async {
    _skills.removeWhere((s) => s.id == id);
    ref.invalidateSelf();
  }

  @override
  Future<Skill> createSkill(Skill skill) async {
    createCount++;
    final created = skill.copyWith(id: 'new-id');
    _skills.add(created);
    ref.invalidateSelf();
    return created;
  }

  @override
  Future<Skill> updateSkill(String id, Skill skill) async {
    updateCount++;
    final idx = _skills.indexWhere((s) => s.id == id);
    if (idx != -1) _skills[idx] = skill;
    ref.invalidateSelf();
    return skill;
  }
}

class _FailingDeleteNotifier extends _FakeSkillsNotifier {
  _FailingDeleteNotifier(super.skills);

  @override
  Future<void> deleteSkill(String id) async => throw Exception('boom');
}

/// Two sample skills used across tests.
const _skill1 = Skill(
  id: 'sk-1',
  name: 'Code Reviewer',
  description: 'Reviews pull requests.',
  content: '## Review guidelines',
  tags: ['git', 'review'],
  createdAt: 0,
  updatedAt: 0,
);

const _skill2 = Skill(
  id: 'sk-2',
  name: 'Triage Bot',
  description: 'Triages issues.',
  content: '',
  tags: ['bugs'],
  createdAt: 1,
  updatedAt: 1,
);

Widget _buildTabView(
  List<Skill> skills, {
  VoidCallback? onNewSkill,
  void Function(String id)? onOpenSkill,
  _FakeSkillsNotifier? notifier,
}) {
  return ProviderScope(
    overrides: [
      skillsProvider.overrideWith(() => notifier ?? _FakeSkillsNotifier(skills)),
    ],
    child: MaterialApp(
      theme: LoomTheme.light,
      home: Scaffold(
        body: SkillsTabView(
          onNewSkill: onNewSkill ?? () {},
          onOpenSkill: onOpenSkill ?? (_) {},
        ),
      ),
    ),
  );
}

Widget _buildEditor({
  String? skillId,
  List<Skill> skills = const [],
  VoidCallback? onBack,
  _FakeSkillsNotifier? notifier,
}) {
  return ProviderScope(
    overrides: [
      skillsProvider.overrideWith(
        () => notifier ?? _FakeSkillsNotifier(List.from(skills)),
      ),
    ],
    child: MaterialApp(
      theme: LoomTheme.light,
      home: SkillEditorScreen(
        skillId: skillId,
        onBack: onBack ?? () {},
      ),
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  // ─────────────────────────────────────────────────────────────────────────
  // SkillsTabView
  // ─────────────────────────────────────────────────────────────────────────

  group('SkillsTabView', () {
    testWidgets(
        'renders list of skills and filters correctly when typing in search',
        (tester) async {
      await tester.pumpWidget(_buildTabView([_skill1, _skill2]));
      await tester.pumpAndSettle();

      // Both cards visible initially
      expect(find.text('Code Reviewer'), findsOneWidget);
      expect(find.text('Triage Bot'), findsOneWidget);

      // Type into search to filter
      await tester.enterText(
          find.byKey(const ValueKey('skills-search')), 'code');
      await tester.pump();

      expect(find.text('Code Reviewer'), findsOneWidget);
      expect(find.text('Triage Bot'), findsNothing);
    });

    testWidgets('displays EmptyState when skill list is empty',
        (tester) async {
      await tester.pumpWidget(_buildTabView([]));
      await tester.pumpAndSettle();

      expect(find.byType(EmptyState), findsOneWidget);
      expect(find.text('No skills yet'), findsOneWidget);
    });

    testWidgets(
        'shows search-specific empty state when no skills match the query',
        (tester) async {
      await tester.pumpWidget(_buildTabView([_skill1, _skill2]));
      await tester.pumpAndSettle();

      await tester.enterText(
          find.byKey(const ValueKey('skills-search')), 'zzzz');
      await tester.pump();

      expect(find.byType(EmptyState), findsOneWidget);
      expect(find.text('No matching skills'), findsOneWidget);
      expect(find.text('No skills yet'), findsNothing);

      await tester.tap(find.text('Clear search'));
      await tester.pumpAndSettle();
      expect(find.byType(SkillCard), findsNWidgets(2));
    });

    testWidgets('shows SnackBar when deleting a skill fails', (tester) async {
      await tester.pumpWidget(
        _buildTabView([], notifier: _FailingDeleteNotifier([_skill1])),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('skill-delete')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Delete failed'), findsOneWidget);
      expect(find.byType(SkillCard), findsOneWidget);
    });

    testWidgets(
        'shows ConfirmDialog and deletes skill on confirmation',
        (tester) async {
      await tester.pumpWidget(_buildTabView([_skill1]));
      await tester.pumpAndSettle();

      // Open overflow menu and tap Delete
      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('skill-delete')));
      await tester.pumpAndSettle();

      // Confirm dialog should appear
      expect(find.text('Delete skill?'), findsOneWidget);

      // Tap the destructive confirm button
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      // Skill card should be gone
      expect(find.byType(SkillCard), findsNothing);
    });
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SkillEditorScreen
  // ─────────────────────────────────────────────────────────────────────────

  group('SkillEditorScreen', () {
    testWidgets(
        'initializes fields with existing skill data',
        (tester) async {
      await tester.pumpWidget(
        _buildEditor(skillId: 'sk-1', skills: [_skill1]),
      );
      await tester.pumpAndSettle();

      expect(
        tester
            .widget<TextField>(
                find.byKey(const ValueKey('skill-name-field')))
            .controller!
            .text,
        'Code Reviewer',
      );
      expect(
        tester
            .widget<TextField>(
                find.byKey(const ValueKey('skill-desc-field')))
            .controller!
            .text,
        'Reviews pull requests.',
      );
    });

    testWidgets('initializes with empty defaults for new skill',
        (tester) async {
      await tester.pumpWidget(_buildEditor());
      await tester.pumpAndSettle();

      expect(find.text('New Skill'), findsOneWidget);
      expect(
        tester
            .widget<TextField>(
                find.byKey(const ValueKey('skill-name-field')))
            .controller!
            .text,
        '',
      );
    });

    testWidgets('saving again after create updates instead of creating',
        (tester) async {
      final notifier = _FakeSkillsNotifier([]);
      await tester.pumpWidget(_buildEditor(notifier: notifier));
      await tester.pumpAndSettle();

      await tester.enterText(
          find.byKey(const ValueKey('skill-name-field')), 'First');
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('save-btn')));
      await tester.pumpAndSettle();
      expect(notifier.createCount, 1);

      // Title reflects saved skill and Delete menu is available
      expect(find.text('New Skill'), findsNothing);
      expect(find.byKey(const ValueKey('editor-overflow-menu')),
          findsOneWidget);

      await tester.enterText(
          find.byKey(const ValueKey('skill-name-field')), 'Second');
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('save-btn')));
      await tester.pumpAndSettle();

      expect(notifier.createCount, 1);
      expect(notifier.updateCount, 1);
    });

    testWidgets('enables Save button only when changes are made',
        (tester) async {
      await tester.pumpWidget(_buildEditor());
      await tester.pumpAndSettle();

      // Save should be disabled initially (no changes on new skill)
      final saveButtonInitial = tester.widget<FilledButton>(
        find.byKey(const ValueKey('save-btn')),
      );
      expect(saveButtonInitial.onPressed, isNull);

      // Type a name — Save should become enabled
      await tester.enterText(
          find.byKey(const ValueKey('skill-name-field')), 'My New Skill');
      await tester.pump();

      final saveButtonAfter = tester.widget<FilledButton>(
        find.byKey(const ValueKey('save-btn')),
      );
      expect(saveButtonAfter.onPressed, isNotNull);
    });

    testWidgets(
        'switches between raw text and MarkdownPreview on preview toggle',
        (tester) async {
      await tester.pumpWidget(
        _buildEditor(skillId: 'sk-1', skills: [_skill1]),
      );
      await tester.pumpAndSettle();

      // Content field visible initially
      expect(find.byKey(const ValueKey('skill-content-field')), findsOneWidget);
      expect(find.byType(MarkdownPreview), findsNothing);

      // Tap the preview toggle in the app bar
      await tester.tap(find.byKey(const ValueKey('preview-toggle-btn')));
      await tester.pumpAndSettle();

      // Preview mode: markdown preview visible, editor hidden
      expect(find.byKey(const ValueKey('markdown-preview-container')),
          findsOneWidget);
      expect(find.byKey(const ValueKey('skill-content-field')), findsNothing);
    });

    testWidgets(
        'prompts with confirm dialog when attempting to pop with unsaved changes',
        (tester) async {
      bool popped = false;

      await tester.pumpWidget(
        _buildEditor(onBack: () => popped = true),
      );
      await tester.pumpAndSettle();

      // Make a change to make form dirty
      await tester.enterText(
          find.byKey(const ValueKey('skill-name-field')), 'Draft Skill');
      await tester.pump();

      // Tap the back button
      await tester.tap(find.byKey(const ValueKey('editor-back-btn')));
      await tester.pumpAndSettle();

      // Discard dialog should appear
      expect(find.text('Discard changes?'), findsOneWidget);

      // Cancel — should not pop
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(popped, isFalse);

      // Tap back again, then confirm discard
      await tester.tap(find.byKey(const ValueKey('editor-back-btn')));
      await tester.pumpAndSettle();
      expect(find.text('Discard changes?'), findsOneWidget);

      await tester.tap(find.text('Discard'));
      await tester.pumpAndSettle();
      expect(popped, isTrue);
    });
  });
}
