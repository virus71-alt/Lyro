import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:permission_handler/permission_handler.dart';

enum AudioPermissionStatus {
  granted,
  denied,
  permanentlyDenied,
  restricted,
}

class PermissionService {
  PermissionService._();

  static final PermissionService instance = PermissionService._();

  /// Checks the current permission status for local audio files.
  Future<AudioPermissionStatus> checkAudioPermission() async {
    if (kIsWeb) return AudioPermissionStatus.granted;

    try {
      if (Platform.isAndroid) {
        // For Android 13+ (API 33+), audio is in Permission.audio
        // For older Android, it's Permission.storage
        final audioStatus = await Permission.audio.status;
        if (audioStatus.isGranted) return AudioPermissionStatus.granted;
        if (audioStatus.isPermanentlyDenied) return AudioPermissionStatus.permanentlyDenied;

        final storageStatus = await Permission.storage.status;
        if (storageStatus.isGranted) return AudioPermissionStatus.granted;
        if (storageStatus.isPermanentlyDenied) return AudioPermissionStatus.permanentlyDenied;

        return AudioPermissionStatus.denied;
      } else if (Platform.isIOS) {
        final mediaLibrary = await Permission.mediaLibrary.status;
        if (mediaLibrary.isGranted) return AudioPermissionStatus.granted;
        if (mediaLibrary.isPermanentlyDenied) return AudioPermissionStatus.permanentlyDenied;
        if (mediaLibrary.isRestricted) return AudioPermissionStatus.restricted;
        return AudioPermissionStatus.denied;
      }

      return AudioPermissionStatus.granted;
    } catch (e) {
      debugPrint('Permission check error: $e');
      return AudioPermissionStatus.denied;
    }
  }

  /// Requests the necessary permission to access audio files.
  Future<AudioPermissionStatus> requestAudioPermission() async {
    if (kIsWeb) return AudioPermissionStatus.granted;

    try {
      if (Platform.isAndroid) {
        // Request audio permission (Android 13+)
        var status = await Permission.audio.request();
        if (status.isGranted) return AudioPermissionStatus.granted;

        // If not granted (older Android or fallback), request storage
        if (!status.isGranted) {
          final storageStatus = await Permission.storage.request();
          if (storageStatus.isGranted) return AudioPermissionStatus.granted;
          if (storageStatus.isPermanentlyDenied) {
            return AudioPermissionStatus.permanentlyDenied;
          }
        }

        if (status.isPermanentlyDenied) {
          return AudioPermissionStatus.permanentlyDenied;
        }

        return AudioPermissionStatus.denied;
      } else if (Platform.isIOS) {
        final status = await Permission.mediaLibrary.request();
        if (status.isGranted) return AudioPermissionStatus.granted;
        if (status.isPermanentlyDenied) return AudioPermissionStatus.permanentlyDenied;
        if (status.isRestricted) return AudioPermissionStatus.restricted;
        return AudioPermissionStatus.denied;
      }

      return AudioPermissionStatus.granted;
    } catch (e) {
      debugPrint('Permission request error: $e');
      return AudioPermissionStatus.denied;
    }
  }

  /// Opens application settings when permission is permanently denied.
  Future<bool> openSettings() async {
    return await openAppSettings();
  }
}

