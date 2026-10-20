import 'package:flutter/material.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Chip input row: type a tag and press Enter to add; tap × to remove.
///
/// Calls [onChanged] after every addition or removal.
class TagChipInput extends StatefulWidget {
  final List<String> initialTags;
  final ValueChanged<List<String>> onChanged;

  const TagChipInput({
    super.key,
    required this.initialTags,
    required this.onChanged,
  });

  @override
  State<TagChipInput> createState() => _TagChipInputState();
}

class _TagChipInputState extends State<TagChipInput> {
  late final List<String> _tags;
  final TextEditingController _ctrl = TextEditingController();
  final FocusNode _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _tags = List<String>.from(widget.initialTags);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _submit() {
    final text = _ctrl.text.trim();
    if (text.isEmpty) return;
    setState(() {
      _tags.add(text);
      _ctrl.clear();
    });
    widget.onChanged(List.unmodifiable(_tags));
  }

  void _remove(int index) {
    setState(() => _tags.removeAt(index));
    widget.onChanged(List.unmodifiable(_tags));
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Existing chips
        if (_tags.isNotEmpty)
          Wrap(
            spacing: LoomSpace.s8,
            runSpacing: LoomSpace.s4,
            children: [
              for (int i = 0; i < _tags.length; i++)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: LoomSpace.s8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: colors.accentTint,
                    borderRadius: BorderRadius.circular(LoomRadius.control),
                    border: Border.all(color: colors.line),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        _tags[i],
                        style: TextStyle(
                          fontSize: 13,
                          color: colors.accentInk,
                        ),
                      ),
                      const SizedBox(width: 4),
                      GestureDetector(
                        key: ValueKey('tag-delete-$i'),
                        onTap: () => _remove(i),
                        child: Icon(
                          Icons.close,
                          size: 14,
                          color: colors.accentInk,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        if (_tags.isNotEmpty) const SizedBox(height: LoomSpace.s8),
        // Input field
        SizedBox(
          height: LoomSize.controlCompact,
          child: TextField(
            key: const ValueKey('tag-input'),
            controller: _ctrl,
            focusNode: _focus,
            style: TextStyle(fontSize: 13, color: colors.ink),
            decoration: InputDecoration(
              hintText: 'Add tag…',
              hintStyle: TextStyle(fontSize: 13, color: colors.ink3),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: LoomSpace.s8,
                vertical: 0,
              ),
              isDense: true,
              filled: true,
              fillColor: colors.sunken,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(LoomRadius.control),
                borderSide: BorderSide(color: colors.line),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(LoomRadius.control),
                borderSide: BorderSide(color: colors.line),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(LoomRadius.control),
                borderSide: BorderSide(color: colors.accent),
              ),
            ),
            onSubmitted: (_) => _submit(),
          ),
        ),
      ],
    );
  }
}
