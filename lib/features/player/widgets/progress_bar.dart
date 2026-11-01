import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';

class LyroProgressBar extends StatefulWidget {
  final Duration position;
  final Duration duration;
  final Duration bufferedPosition;
  final ValueListenable<Duration>? positionListenable;
  final ValueListenable<Duration>? bufferedPositionListenable;
  final ValueChanged<Duration> onSeek;

  const LyroProgressBar({
    super.key,
    this.position = Duration.zero,
    required this.duration,
    this.bufferedPosition = Duration.zero,
    this.positionListenable,
    this.bufferedPositionListenable,
    required this.onSeek,
  });

  @override
  State<LyroProgressBar> createState() => _LyroProgressBarState();
}

class _LyroProgressBarState extends State<LyroProgressBar> {
  double? _dragValue;

  @override
  Widget build(BuildContext context) {
    if (widget.positionListenable != null) {
      return ValueListenableBuilder<Duration>(
        valueListenable: widget.positionListenable!,
        builder: (context, currentPos, _) {
          return _buildSlider(context, currentPos);
        },
      );
    }
    return _buildSlider(context, widget.position);
  }

  Widget _buildSlider(BuildContext context, Duration currentPos) {
    final isDark = context.isDark;
    final maxMs = widget.duration.inMilliseconds.toDouble();
    final currentMs = _dragValue ?? currentPos.inMilliseconds.toDouble().clamp(0.0, maxMs > 0 ? maxMs : 1.0);
    final displayPosition = _dragValue != null
        ? Duration(milliseconds: _dragValue!.toInt())
        : currentPos;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            trackHeight: 2.5,
            activeTrackColor: NothingColors.signalRed,
            inactiveTrackColor: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
            secondaryActiveTrackColor: isDark ? NothingColors.darkLine : NothingColors.lightLine,
            thumbColor: Colors.white,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6.0),
            overlayColor: NothingColors.signalRed.withValues(alpha: 0.2),
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 12.0),
            trackShape: const RectangularSliderTrackShape(),
          ),
          child: Slider(
            min: 0.0,
            max: maxMs > 0 ? maxMs : 1.0,
            value: (maxMs > 0) ? currentMs.clamp(0.0, maxMs) : 0.0,
            onChanged: (val) {
              setState(() {
                _dragValue = val;
              });
            },
            onChangeEnd: (val) {
              widget.onSeek(Duration(milliseconds: val.toInt()));
              setState(() {
                _dragValue = null;
              });
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                Formatters.formatDuration(displayPosition),
                style: TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 11,
                  letterSpacing: 0.8,
                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                ),
              ),
              Text(
                Formatters.formatDuration(widget.duration),
                style: TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 11,
                  letterSpacing: 0.8,
                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
