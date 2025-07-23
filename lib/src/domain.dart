import 'dart:typed_data';

class AppDetails {
  final String name;
  final Uint8List? icon;
  final String packageName;
  final String versionName;
  final int versionCode;
  final DateTime lastUpdate;
  final bool isLaunchable;
  final bool isSystemApp;

  const AppDetails({
    required this.isLaunchable,
    required this.isSystemApp,
    required this.name,
    this.icon,
    required this.packageName,
    required this.versionName,
    required this.versionCode,
    required this.lastUpdate,
  });
}
