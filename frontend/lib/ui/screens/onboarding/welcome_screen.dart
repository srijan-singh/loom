import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Route: /onboarding/welcome
/// Shown when isOnboardingComplete() == false.
class OnboardingWelcomeScreen extends StatelessWidget {
  const OnboardingWelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return Scaffold(
      backgroundColor: colors.bg,
      body: Stack(
        children: [
          // Decorative woven grid at bottom — matches reference spec
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 96,
            child: _WovenStrip(colors: colors),
          ),
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(48, 48, 48, 120),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Logo mark — 56px
                    Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        color: colors.accentTint,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Center(
                        child: Text(
                          'L',
                          style: TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.w700,
                            color: colors.accent,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'Loom',
                      style: TextStyle(
                        fontSize: 28,
                        height: 34 / 28,
                        fontWeight: FontWeight.w600,
                        letterSpacing: -0.015 * 28,
                        color: colors.ink,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      'Your AI workforce, on your machine',
                      style: TextStyle(fontSize: 16, color: colors.ink2),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 340),
                      child: Text(
                        'Bring your own AI provider. Connect your tools. We handle the rest.',
                        style: TextStyle(fontSize: 14, color: colors.ink2),
                        textAlign: TextAlign.center,
                      ),
                    ),
                    const SizedBox(height: 24),
                    SizedBox(
                      width: double.infinity,
                      child: LoomButton(
                        label: 'Get started',
                        onPressed: () => context.go(Routes.onboardingApiKey),
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(
                      'Data goes only to the AI provider and tools you choose.',
                      style: TextStyle(fontSize: 12, color: colors.ink3),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Subtle grid at the bottom of the welcome screen.
class _WovenStrip extends StatelessWidget {
  final LoomColors colors;
  const _WovenStrip({required this.colors});

  @override
  Widget build(BuildContext context) {
    return ShaderMask(
      shaderCallback: (rect) => LinearGradient(
        begin: Alignment.bottomCenter,
        end: Alignment.topCenter,
        colors: [colors.line.withValues(alpha: 0.8), Colors.transparent],
      ).createShader(rect),
      blendMode: BlendMode.srcATop,
      child: CustomPaint(
        painter: _GridPainter(color: colors.line),
      ),
    );
  }
}

class _GridPainter extends CustomPainter {
  final Color color;
  const _GridPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 1;
    const step = 12.0;
    for (double x = 0; x <= size.width; x += step) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
    for (double y = 0; y <= size.height; y += step) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  @override
  bool shouldRepaint(_GridPainter old) => old.color != color;
}
