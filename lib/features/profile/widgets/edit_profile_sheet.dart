import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../providers/profile_provider.dart';

/// Bottom sheet to rename the listener and pick a dot-matrix avatar.
class EditProfileSheet extends StatefulWidget {
  const EditProfileSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const EditProfileSheet(),
    );
  }

  @override
  State<EditProfileSheet> createState() => _EditProfileSheetState();
}

class _EditProfileSheetState extends State<EditProfileSheet> {
  late final ProfileProvider _profile = context.read<ProfileProvider>();
  late final TextEditingController _name =
      TextEditingController(text: _profile.name);
  late int _avatar = _profile.avatarId;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    HapticFeedback.mediumImpact();
    await _profile.update(name: _name.text, avatarId: _avatar);
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    final label = TextStyle(
      fontFamily: NothingTypography.fontMono,
      fontSize: 10,
      letterSpacing: 1.4,
      color: NothingColors.darkMuted,
    );

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: Container(
        decoration: const BoxDecoration(
          color: NothingColors.darkSurface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
          border: Border(top: BorderSide(color: NothingColors.darkLine)),
        ),
        child: SafeArea(
          top: false,
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: NothingColors.darkLine2,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 20),

                // Live preview
                Center(
                  child: ValueListenableBuilder<TextEditingValue>(
                    valueListenable: _name,
                    builder: (context, value, _) => NothingAvatar(
                      avatarId: _avatar,
                      name: value.text,
                      size: 96,
                      selected: true,
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                Text('DISPLAY NAME', style: label),
                const SizedBox(height: 8),
                TextField(
                  controller: _name,
                  maxLength: 24,
                  textCapitalization: TextCapitalization.words,
                  cursorColor: NothingColors.signalRed,
                  style: const TextStyle(
                    fontFamily: NothingTypography.fontUi,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: NothingColors.darkDisplay,
                  ),
                  decoration: InputDecoration(
                    counterStyle: label,
                    filled: true,
                    fillColor: NothingColors.darkRaised,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: NothingColors.darkLine),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: NothingColors.signalRed),
                    ),
                  ),
                  onSubmitted: (_) => _save(),
                ),
                const SizedBox(height: 12),

                Text('AVATAR', style: label),
                const SizedBox(height: 12),
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: NothingAvatars.count,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 4,
                    mainAxisSpacing: 14,
                    crossAxisSpacing: 14,
                  ),
                  itemBuilder: (context, i) => GestureDetector(
                    key: Key('avatar_option_$i'),
                    onTap: () {
                      HapticFeedback.selectionClick();
                      setState(() => _avatar = i);
                    },
                    child: Semantics(
                      label: NothingAvatars.names[i],
                      selected: _avatar == i,
                      child: NothingAvatar(
                        avatarId: i,
                        name: _name.text,
                        size: double.infinity,
                        selected: _avatar == i,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    key: const Key('profile_save'),
                    onPressed: _save,
                    style: FilledButton.styleFrom(
                      backgroundColor: NothingColors.darkDisplay,
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(NothingColors.radiusPill),
                      ),
                    ),
                    child: const Text(
                      'SAVE',
                      style: TextStyle(
                        fontFamily: NothingTypography.fontMono,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 1.6,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
