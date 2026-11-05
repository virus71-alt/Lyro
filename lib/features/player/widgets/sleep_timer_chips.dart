import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';

/// One-tap sleep timer choices, same steps as the speaker's dial.
class SleepTimerChips extends StatelessWidget {
  static const labels = ['OFF', '15M', '30M', '1H', 'END'];
  static const _spoken = ['off', '15 minutes', '30 minutes', '1 hour', 'end of track'];

  final int selected;
  final ValueChanged<int> onSelect;
  final Color line;
  final Color display;

  const SleepTimerChips({
    super.key,
    required this.selected,
    required this.onSelect,
    required this.line,
    required this.display,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final (i, label) in labels.indexed) ...[
          if (i > 0) const SizedBox(width: 6),
          Expanded(
            child: Semantics(
              button: true,
              selected: i == selected,
              label: 'Sleep timer ${_spoken[i]}',
              child: GestureDetector(
                key: Key('sleep_chip_$i'),
                behavior: HitTestBehavior.opaque,
                onTap: () {
                  HapticFeedback.selectionClick();
                  onSelect(i);
                },
                child: Container(
                  height: 38,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: i == selected ? NothingColors.signalRed : null,
                    borderRadius: BorderRadius.circular(NothingColors.radiusPill),
                    border: Border.all(color: i == selected ? NothingColors.signalRed : line),
                  ),
                  child: Text(
                    label,
                    style: TextStyle(
                      fontFamily: NothingTypography.fontMono,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      letterSpacing: 0.8,
                      color: i == selected ? Colors.white : display,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ],
    );
  }
}
