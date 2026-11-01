import 'package:flutter/foundation.dart';
import '../../../data/repositories/preferences_repository.dart';

/// The listener's display name and chosen dot-matrix avatar.
class ProfileProvider extends ChangeNotifier {
  final PreferencesRepository prefsRepo;

  late String _name = prefsRepo.getProfileName();
  late int _avatarId = prefsRepo.getProfileAvatar();

  ProfileProvider(this.prefsRepo);

  String get name => _name;
  int get avatarId => _avatarId;

  Future<void> update({required String name, required int avatarId}) async {
    final trimmed = name.trim();
    _name = trimmed.isEmpty ? 'Lyro Listener' : trimmed;
    _avatarId = avatarId;
    notifyListeners();
    await prefsRepo.setProfileName(_name);
    await prefsRepo.setProfileAvatar(_avatarId);
  }
}
