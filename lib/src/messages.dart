import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/messages.g.dart',
    dartOptions: DartOptions(),
    kotlinOut:
        "android/src/main/kotlin/com/pierremrtn/flutter_launcher/Messages.g.kt",
    kotlinOptions: KotlinOptions(
      errorClassName: "FlutterLauncherError",
      package: "com.pierremrtn.flutter_launcher",
    ),
    dartPackageName: 'flutter_launcher',
  ),
)
// ignore: unused_element
const _config = 0;

class AppDetails {
  final String name;
  final Uint8List? icon;
  final String packageName;
  final String versionName;
  final int versionCode;
  final String lastUpdateISO;
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
    required this.lastUpdateISO,
  });
}

@HostApi()
abstract class KotlinSideApi {
  @async
  void initialize();

  @async
  void uninstallApp(String packageName);

  @async
  void launchApp(String packageName);

  @async
  AppDetails getAppDetails(
    String packageName, {
    required bool withIcon,
  });

  @async
  void openAppSettings(String packageName);
}

@FlutterApi()
abstract class FlutterSideApi {
  void onAppListReceived(List<AppDetails> apps);
  void onHomeIntentReceived();
}
