import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/services/web_stream_server.dart';
import '../../../shared/widgets/nothing_card.dart';

/// Profile → WEB STREAM: serve the library to a browser on the same Wi-Fi.
class WebStreamCard extends StatelessWidget {
  const WebStreamCard({super.key});

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final server = context.watch<WebStreamServer>();
    final display = dark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
    final secondary = dark ? NothingColors.darkSecondary : NothingColors.lightSecondary;
    final line = dark ? NothingColors.darkLine : NothingColors.lightLine;

    TextStyle title() => TextStyle(
          fontFamily: NothingTypography.fontMono,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.8,
          color: display,
        );
    TextStyle hint() => TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 11, color: secondary);
    TextStyle mono(double size, Color color) =>
        TextStyle(fontFamily: NothingTypography.fontMono, fontSize: size, letterSpacing: 0.8, color: color);

    return NothingCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('WEB STREAM', style: title()),
                    const SizedBox(height: 2),
                    Text('Play this library in a browser on the same Wi-Fi', style: hint()),
                  ],
                ),
              ),
              Switch(
                key: const Key('web_stream_switch'),
                value: server.running,
                activeThumbColor: NothingColors.darkDisplay,
                activeTrackColor: NothingColors.signalRed,
                onChanged: (v) {
                  HapticFeedback.selectionClick();
                  v ? server.start() : server.stop();
                },
              ),
            ],
          ),
          if (server.error != null) ...[
            const SizedBox(height: 10),
            Text(server.error!, style: hint().copyWith(color: NothingColors.signalRedBright)),
          ],
          if (server.running) ...[
            Divider(height: 28, color: line),
            Text('OPEN ON YOUR LAPTOP', style: mono(9, secondary)),
            const SizedBox(height: 6),
            SelectableText(
              server.address!.replaceFirst('http://', ''),
              style: NothingTypography.dottedBold(isDark: dark, fontSize: 22),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Text('KEY', style: mono(9, secondary)),
                const SizedBox(width: 10),
                Text(server.key.toUpperCase(), style: mono(16, NothingColors.signalRedBright)),
                const Spacer(),
                Text(
                  server.clients == 0 ? 'WAITING' : '${server.clients} CONNECTED',
                  style: mono(9, server.clients == 0 ? secondary : NothingColors.signalRedBright),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: _button(context, 'COPY LINK', () {
                    Clipboard.setData(ClipboardData(text: server.link!));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Link copied, key included')),
                    );
                  }),
                ),
                const SizedBox(width: 10),
                Expanded(child: _button(context, 'NEW KEY', server.newKey)),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              'Audio plays on the laptop, so use its headphones. The phone stays awake while this is on.',
              style: hint(),
            ),
          ],
        ],
      ),
    );
  }

  Widget _button(BuildContext context, String label, VoidCallback onTap) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return OutlinedButton(
      onPressed: () {
        HapticFeedback.selectionClick();
        onTap();
      },
      style: OutlinedButton.styleFrom(
        foregroundColor: dark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
        side: BorderSide(color: dark ? NothingColors.darkLine2 : NothingColors.lightLine2),
        shape: const StadiumBorder(),
        padding: const EdgeInsets.symmetric(vertical: 12),
      ),
      child: Text(label, style: const TextStyle(fontFamily: NothingTypography.fontMono, fontSize: 11, letterSpacing: 0.8)),
    );
  }
}
