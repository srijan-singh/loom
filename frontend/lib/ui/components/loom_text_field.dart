import 'package:flutter/material.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Loom-styled text field with label and optional obscure toggle.
/// Errors display inline below the field and persist until value changes.
class LoomTextField extends StatefulWidget {
  final String label;
  final String? hint;
  final bool obscure;
  final TextEditingController? controller;
  final void Function(String)? onChanged;
  final String? errorText;

  const LoomTextField({
    required this.label,
    this.hint,
    this.obscure = false,
    this.controller,
    this.onChanged,
    this.errorText,
    super.key,
  });

  @override
  State<LoomTextField> createState() => _LoomTextFieldState();
}

class _LoomTextFieldState extends State<LoomTextField> {
  bool _hidden = true;

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          widget.label,
          style: TextStyle(
              fontSize: 12.5,
              fontWeight: FontWeight.w500,
              color: colors.ink2),
        ),
        const SizedBox(height: 6),
        TextField(
          controller: widget.controller,
          onChanged: widget.onChanged,
          obscureText: widget.obscure && _hidden,
          style: TextStyle(color: colors.ink, fontSize: 14),
          decoration: InputDecoration(
            hintText: widget.hint,
            hintStyle: TextStyle(color: colors.ink3, fontSize: 14),
            errorText: widget.errorText,
            errorStyle: TextStyle(color: colors.danger, fontSize: 12),
            filled: true,
            fillColor: colors.surface,
            isDense: true,
            contentPadding: const EdgeInsets.symmetric(
                horizontal: 12, vertical: 9),
            enabledBorder: OutlineInputBorder(
              borderSide: BorderSide(color: colors.lineStrong),
              borderRadius: BorderRadius.circular(LoomRadius.control),
            ),
            focusedBorder: OutlineInputBorder(
              borderSide: BorderSide(color: colors.accent, width: 1.5),
              borderRadius: BorderRadius.circular(LoomRadius.control),
            ),
            errorBorder: OutlineInputBorder(
              borderSide: BorderSide(color: colors.danger),
              borderRadius: BorderRadius.circular(LoomRadius.control),
            ),
            focusedErrorBorder: OutlineInputBorder(
              borderSide: BorderSide(color: colors.danger, width: 1.5),
              borderRadius: BorderRadius.circular(LoomRadius.control),
            ),
            suffixIcon: widget.obscure
                ? IconButton(
                    icon: Icon(
                      _hidden ? Icons.visibility_off : Icons.visibility,
                      size: 18,
                      color: colors.ink3,
                    ),
                    onPressed: () =>
                        setState(() => _hidden = !_hidden),
                  )
                : null,
          ),
        ),
      ],
    );
  }
}
