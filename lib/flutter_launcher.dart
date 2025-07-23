import 'flutter_launcher_platform_interface.dart';

export 'src/domain.dart';

abstract class FlutterLauncher {
  static FlutterLauncherPlatform get instance =>
      FlutterLauncherPlatform.instance;
}
