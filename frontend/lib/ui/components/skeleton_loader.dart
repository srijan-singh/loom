import 'package:flutter/material.dart';
import 'package:loom_ui/ui/components/stat_card.dart' show SkeletonLoader;
import 'package:loom_ui/ui/theme/loom_theme.dart';

// Re-export the existing shimmer primitive so consumers can import from here.
export 'package:loom_ui/ui/components/stat_card.dart' show SkeletonLoader;

/// A column of [itemCount] shimmer placeholder rows, each [height] dp tall.
/// Used as a loading state for list views throughout the Library screens.
///
/// The spec contract name is [SkeletonLoader] with {itemCount, height};
/// that class is provided here as [SkeletonListLoader] to avoid collision
/// with the shimmer primitive of the same name already in stat_card.dart.
/// Import this file and use [SkeletonListLoader] for list-level skeletons.
class SkeletonListLoader extends StatelessWidget {
  final int itemCount;
  final double height;

  const SkeletonListLoader({
    super.key,
    this.itemCount = 3,
    this.height = 96,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    return Column(
      children: [
        for (int i = 0; i < itemCount; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(LoomRadius.card),
            child: SkeletonLoader(
              width: double.infinity,
              height: height,
              colors: colors,
            ),
          ),
        ],
      ],
    );
  }
}
