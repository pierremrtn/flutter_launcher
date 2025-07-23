import 'package:flutter_launcher/src/plugin_api.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'src/domain.dart';

abstract class FlutterLauncherPlatform extends PlatformInterface {
  /// Constructs a FlutterLauncherPlatform.
  FlutterLauncherPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterLauncherPlatform _instance = FlutterLauncherAndroidPlugin();

  /// The default instance of [FlutterLauncherPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterLauncher].
  static FlutterLauncherPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterLauncherPlatform] when
  /// they register themselves.
  static set instance(FlutterLauncherPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  List<AppDetails>? get launchableApps;
  Stream<List<AppDetails>> get launchableAppsStream;
  Stream<void> get homeIntentEventsStream;

  Future<void> initialize();

  Future<void> uninstallApp(String packageName);

  Future<void> launchApp(String packageName);

  Future<AppDetails> getAppDetails(String packageName);

  Future<void> openAppSettings(String packageName);
}
