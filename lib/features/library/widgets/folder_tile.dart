import 'dart:typed_data';
import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/models/folder_model.dart';
import '../../player/widgets/track_artwork.dart';

/// Nothing-style folder row: mono index, cover art from the folder's first
/// track, name over a readable parent path, and a dot-matrix track count.
/// Rows are separated by dotted rules rather than solid lines.
class FolderTile extends StatelessWidget {
  final MusicFolder folder;
  final int index;
  final VoidCallback onTap;

  const FolderTile({
    super.key,
    required this.folder,
    required this.onTap,
    this.index = 0,
  });

  /// "/storage/emulated/0/Music/Dilwale" → "INTERNAL / MUSIC"
  static String parentLabel(String path) {
    var p = path.replaceAll('\\', '/');
    var root = '';
    const internal = '/storage/emulated/0';
    if (p.startsWith(internal)) {
      root = 'INTERNAL';
      p = p.substring(internal.length);
    } else if (p.startsWith('/storage/')) {
      root = 'SD CARD';
      p = p.split('/').skip(3).join('/');
    }
    final parts = p.split('/').where((s) => s.isNotEmpty).toList();
    if (parts.isNotEmpty) parts.removeLast(); // the folder itself
    return [if (root.isNotEmpty) root, ...parts].join(' / ').toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final display = isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
    final muted = isDark ? NothingColors.darkMuted : NothingColors.lightMuted;
    final cover = folder.songs.isNotEmpty ? folder.songs.first : null;
    final count = folder.songCount;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 14.0),
              child: Row(
                children: [
                  SizedBox(
                    width: 26,
                    child: Text(
                      (index + 1).toString().padLeft(2, '0'),
                      style: TextStyle(
                        fontFamily: NothingTypography.fontMono,
                        fontSize: 10,
                        letterSpacing: 0.6,
                        color: muted,
                      ),
                    ),
                  ),
                  SizedBox(
                    width: 50,
                    height: 50,
                    child: cover == null
                        ? _EmptyCover(isDark: isDark)
                        : TrackArtwork(
                            songId: cover.id,
                            title: folder.name,
                            size: 50,
                            radius: 6,
                          ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          folder.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontFamily: NothingTypography.fontUi,
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                            letterSpacing: -0.1,
                            color: display,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          parentLabel(folder.path),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontFamily: NothingTypography.fontMono,
                            fontSize: 9.5,
                            letterSpacing: 1.0,
                            color: muted,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        count.toString().padLeft(2, '0'),
                        style: NothingTypography.dottedBold(
                          isDark: isDark,
                          fontSize: 22,
                          letterSpacing: 0.5,
                          color: display,
                        ),
                      ),
                      Text(
                        count == 1 ? 'TRACK' : 'TRACKS',
                        style: TextStyle(
                          fontFamily: NothingTypography.fontMono,
                          fontSize: 8.5,
                          letterSpacing: 1.2,
                          color: muted,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            DottedRule(isDark: isDark),
          ],
        ),
      ),
    );
  }
}

class _EmptyCover extends StatelessWidget {
  final bool isDark;
  const _EmptyCover({required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Icon(
        Icons.folder_outlined,
        size: 20,
        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
      ),
    );
  }
}

/// Horizontal rule made of dots.
class DottedRule extends StatelessWidget {
  final bool isDark;
  const DottedRule({super.key, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 2,
      width: double.infinity,
      child: CustomPaint(painter: _DottedRulePainter(isDark)),
    );
  }
}

class _DottedRulePainter extends CustomPainter {
  final bool isDark;
  _DottedRulePainter(this.isDark);

  @override
  void paint(Canvas canvas, Size size) {
    const pitch = 6.0;
    final n = (size.width / pitch).floor();
    final pts = Float32List(n * 2);
    for (var i = 0; i < n; i++) {
      pts[i * 2] = i * pitch + 1;
      pts[i * 2 + 1] = size.height / 2;
    }
    canvas.drawRawPoints(
      PointMode.points,
      pts,
      Paint()
        ..color = isDark ? NothingColors.darkLine2 : NothingColors.lightLine2
        ..strokeWidth = 1.6
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(_DottedRulePainter old) => old.isDark != isDark;
}
