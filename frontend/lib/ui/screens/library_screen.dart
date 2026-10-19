import 'package:flutter/material.dart';
import 'package:loom_ui/ui/screens/library/agents_tab_view.dart';
import 'package:loom_ui/ui/screens/library/mcps_tab_view.dart';
import 'package:loom_ui/ui/screens/library/skills_tab_view.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Top-level Library screen hosting three tabs: Skills, MCPs, and Agents.
class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        backgroundColor: colors.bg,
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              decoration: BoxDecoration(
                color: colors.surface,
                border: Border(
                  bottom: BorderSide(color: colors.line),
                ),
              ),
              child: TabBar(
                isScrollable: true,
                tabAlignment: TabAlignment.start,
                labelColor: colors.accent,
                unselectedLabelColor: colors.ink3,
                indicatorColor: colors.accent,
                indicatorWeight: 2,
                dividerColor: Colors.transparent,
                labelStyle: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
                unselectedLabelStyle: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                ),
                tabs: const [
                  Tab(text: 'Skills'),
                  Tab(text: 'MCPs'),
                  Tab(text: 'Agents'),
                ],
              ),
            ),
            const Expanded(
              child: TabBarView(
                children: [
                  _KeepAliveWrapper(child: SkillsTabView()),
                  _KeepAliveWrapper(child: McpsTabView()),
                  _KeepAliveWrapper(child: AgentsTabView()),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _KeepAliveWrapper extends StatefulWidget {
  final Widget child;

  const _KeepAliveWrapper({required this.child});

  @override
  State<_KeepAliveWrapper> createState() => _KeepAliveWrapperState();
}

class _KeepAliveWrapperState extends State<_KeepAliveWrapper>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return widget.child;
  }
}
