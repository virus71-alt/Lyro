import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_typography.dart';

/// A list with a Nothing-style A–Z fast scroller on the right edge.
///
/// Tap or drag along the dot-letter rail to jump; a large dot-matrix letter
/// shows where you are. Rows must be the same height (the first row is used
/// as the prototype), which makes each jump exact. The rail is hidden when
/// [letterOf] is null (e.g. the list isn't sorted alphabetically) or the
/// list is short.
class AlphabetScrollList<T> extends StatefulWidget {
  final List<T> items;
  final Widget Function(BuildContext context, int index) itemBuilder;
  final String Function(T item)? letterOf;
  final EdgeInsets padding;
  final int minItemsForRail;

  const AlphabetScrollList({
    super.key,
    required this.items,
    required this.itemBuilder,
    this.letterOf,
    this.padding = EdgeInsets.zero,
    this.minItemsForRail = 25,
  });

  /// Upper-case first letter, or '#' for digits / symbols / other scripts.
  static String initialOf(String s) {
    final t = s.trim();
    if (t.isEmpty) return '#';
    final c = t[0].toUpperCase();
    return RegExp(r'[A-Z]').hasMatch(c) ? c : '#';
  }

  @override
  State<AlphabetScrollList<T>> createState() => _AlphabetScrollListState<T>();
}

class _AlphabetScrollListState<T> extends State<AlphabetScrollList<T>> {
  final ScrollController _scroll = ScrollController();
  String? _active;

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  /// letter -> first index, in list order
  Map<String, int> get _index {
    final letterOf = widget.letterOf!;
    final map = <String, int>{};
    for (var i = 0; i < widget.items.length; i++) {
      map.putIfAbsent(letterOf(widget.items[i]), () => i);
    }
    return map;
  }

  void _jump(Map<String, int> index, List<String> letters, double dy, double railHeight) {
    final i = (dy / railHeight * letters.length).floor().clamp(0, letters.length - 1);
    final letter = letters[i];
    if (letter == _active) return;
    HapticFeedback.selectionClick();
    setState(() => _active = letter);
    if (!_scroll.hasClients) return;
    final pos = _scroll.position;
    final rowExtent = (pos.maxScrollExtent + pos.viewportDimension - widget.padding.vertical) /
        widget.items.length;
    _scroll.jumpTo((index[letter]! * rowExtent).clamp(0.0, pos.maxScrollExtent));
  }

  @override
  Widget build(BuildContext context) {
    final list = ListView.builder(
      controller: _scroll,
      padding: widget.padding,
      itemCount: widget.items.length,
      itemBuilder: widget.itemBuilder,
    );
    if (widget.letterOf == null || widget.items.length < widget.minItemsForRail) return list;

    final index = _index;
    final letters = index.keys.toList();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Stack(
      children: [
        list,
        Positioned(
          right: 0,
          top: 8,
          bottom: 8 + widget.padding.bottom,
          width: 28,
          child: LayoutBuilder(
            builder: (context, c) => GestureDetector(
              key: const Key('alphabet_rail'),
              behavior: HitTestBehavior.opaque,
              onVerticalDragStart: (d) => _jump(index, letters, d.localPosition.dy, c.maxHeight),
              onVerticalDragUpdate: (d) => _jump(index, letters, d.localPosition.dy, c.maxHeight),
              onTapDown: (d) => _jump(index, letters, d.localPosition.dy, c.maxHeight),
              onVerticalDragEnd: (_) => setState(() => _active = null),
              onTapUp: (_) => setState(() => _active = null),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  for (final l in letters)
                    Text(
                      l,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontMono,
                        fontSize: 9.5,
                        fontWeight: l == _active ? FontWeight.w700 : FontWeight.w500,
                        color: l == _active
                            ? NothingColors.signalRedBright
                            : (isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
        // Big dot-matrix letter while scrubbing
        if (_active != null)
          Center(
            child: IgnorePointer(
              child: Container(
                width: 96,
                height: 96,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: const Color(0xEE111113),
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: NothingColors.darkLine2),
                ),
                child: Text(
                  _active!,
                  style: NothingTypography.dottedBold(
                    isDark: true,
                    fontSize: 56,
                    color: NothingColors.darkDisplay,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
