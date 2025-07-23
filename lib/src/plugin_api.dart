import 'dart:async';

import 'package:flutter_launcher/flutter_launcher_platform_interface.dart';
import 'package:flutter_launcher/src/messages.g.dart' as pigeon;
import 'package:rxdart/subjects.dart';

import 'domain.dart';

extension _AppDetailsToDomain on pigeon.AppDetails {
  AppDetails toDomain() => AppDetails(
        isLaunchable: isLaunchable,
        isSystemApp: isSystemApp,
        icon: icon,
        name: name,
        packageName: packageName,
        versionName: versionName,
        versionCode: versionCode,
        lastUpdate: DateTime.parse(lastUpdateISO),
      );
}

class FlutterLauncherAndroidPlugin extends FlutterLauncherPlatform {
  FlutterLauncherAndroidPlugin();

  final _kotlinApi = pigeon.KotlinSideApi();
  final _receiver = _Receiver();

  @override
  List<AppDetails>? get launchableApps =>
      _receiver._userDataSubject.valueOrNull;

  @override
  Stream<List<AppDetails>> get launchableAppsStream =>
      _receiver._userDataSubject.stream;

  @override
  Stream<void> get homeIntentEventsStream =>
      _receiver._homeIntentEventsStreamController.stream;

  @override
  Future<void> initialize() async {
    return await _kotlinApi.initialize();
  }

  @override
  Future<AppDetails> getAppDetails(
    String packageName, {
    bool withIcon = true,
  }) async {
    return await _kotlinApi
        .getAppDetails(packageName, withIcon: withIcon)
        .then((details) => details.toDomain());
  }

  @override
  Future<void> launchApp(String packageName) async {
    return _kotlinApi.launchApp(packageName);
  }

  @override
  Future<void> openAppSettings(String packageName) async {
    return await _kotlinApi.openAppSettings(packageName);
  }

  @override
  Future<void> uninstallApp(String packageName) async {
    return await _kotlinApi.uninstallApp(packageName);
  }
}

class _Receiver extends pigeon.FlutterSideApi {
  _Receiver() {
    pigeon.FlutterSideApi.setUp(this);
  }

  final BehaviorSubject<List<AppDetails>> _userDataSubject = BehaviorSubject();

  final StreamController<void> _homeIntentEventsStreamController =
      StreamController.broadcast();

  @override
  void onAppListReceived(List<pigeon.AppDetails> apps) {
    _userDataSubject.add(apps.map((e) => e.toDomain()).toList());
  }

  @override
  void onHomeIntentReceived() {
    _homeIntentEventsStreamController.add(null);
  }
}
