package com.pierremrtn.flutter_launcher


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.core.net.toUri
import com.pierremrtn.flutter_launcher.KotlinSideApi.Companion.setUp
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** FlutterLauncherPlugin */
class FlutterLauncherPlugin : FlutterPlugin, ActivityAware, KotlinSideApi {
    private var flutterApi: FlutterSideApi? = null;
    private var context: Context? = null;
    private var activityBinding: ActivityPluginBinding? = null
    private lateinit var pluginScope: CoroutineScope


    companion object {
        private const val TAG = "FlutterLauncherPlugin"
    }


    private fun ApplicationInfo.toPigeon(
        packageManager: PackageManager,
        withIcon: Boolean
    ): AppDetails? {
        try {
            val icon =
                if (withIcon) DrawableUtil.drawableToByteArray(
                    this.loadIcon(packageManager)
                )
                else ByteArray(0)
            val packageInfo = packageManager.getPackageInfo(packageName, 0)

            val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val isLaunchable = packageManager.getLaunchIntentForPackage(packageName) != null

            return AppDetails(
                name = packageManager.getApplicationLabel(this).toString(),
                icon = icon,
                packageName = packageName,
                versionName = packageInfo.versionName.toString(),
                versionCode = getVersionCode(packageInfo),
                lastUpdateISO = Instant.ofEpochMilli(packageInfo.lastUpdateTime).toString(),
                isLaunchable = isLaunchable,
                isSystemApp = isSystem,
            );
        } catch (e: Exception) {
            return null
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        setUp(flutterPluginBinding.binaryMessenger, this)
        context = flutterPluginBinding.applicationContext
        flutterApi = FlutterSideApi(flutterPluginBinding.binaryMessenger)

        pluginScope = MainScope()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        setUp(binding.binaryMessenger, null)
        pluginScope.cancel()
        flutterApi = null;
        context = null;
    }


    private var newIntentListener: PluginRegistry.NewIntentListener? = null

    // Called when the plugin is connected to an activity.
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activityBinding = binding
        newIntentListener = PluginRegistry.NewIntentListener { intent ->
            if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
                onHomeButtonPressed()
            }
            true // We handled the intent
        }
        binding.addOnNewIntentListener(newIntentListener!!)
    }

    // Called when the plugin is detached from an activity.
    override fun onDetachedFromActivity() {
        activityBinding?.removeOnNewIntentListener(newIntentListener!!)
        newIntentListener = null
        activityBinding = null
    }

    // Required boilerplate methods for ActivityAware
    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }


    override fun initialize(callback: (Result<Unit>) -> Unit) {

        pluginScope.launch(Dispatchers.IO) {
            observeAppsUpdates(true).collectLatest { list ->
                sendAppUpdatesToFlutterApp(list)
            };
        }

        callback(Result.success(Unit))
    }

    override fun uninstallApp(
        packageName: String,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                addFlags(FLAG_ACTIVITY_NEW_TASK)
            }
            intent.data = "package:$packageName".toUri()
            context!!.startActivity(intent)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun launchApp(
        packageName: String,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            val launchIntent = context!!.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent!!.addFlags(FLAG_ACTIVITY_NEW_TASK)
            context!!.startActivity(launchIntent)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    override fun getAppDetails(
        packageName: String,
        withIcon: Boolean,
        callback: (Result<AppDetails>) -> Unit
    ) {
        try {
            var installedApp = context!!.packageManager.getApplicationInfo(packageName, 0)
            val details = installedApp.toPigeon(context!!.packageManager, withIcon)
            callback(Result.success(details!!))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }

    }

    override fun openAppSettings(
        packageName: String,
        callback: (Result<Unit>) -> Unit
    ) {
        try {
            if (!isAppInstalled(packageName)) {
                throw Exception("App $packageName is not installed on this device.")
            }
            val intent = Intent().apply {
                flags = FLAG_ACTIVITY_NEW_TASK
                action = ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", packageName, null)
            }
            context!!.startActivity(intent)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }

    }

    private fun sendAppUpdatesToFlutterApp(apps: List<AppDetails>) {
        pluginScope.launch(Dispatchers.Main.immediate) {
            flutterApi!!.onAppListReceived(apps) {}
        }
    }

    private fun observeAppsUpdates(
        withIcon: Boolean,
    ): Flow<List<AppDetails>> = callbackFlow {
        // A helper function to fetch apps on a background thread
        suspend fun sendUpdatedApps() {
            val apps = withContext(Dispatchers.IO) { // Switch to a background thread
                getLaunchableApps(withIcon)
            }
            trySend(apps)

        }

        // Initial emission
        sendUpdatedApps()

        // Create broadcast receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // It's a good practice to re-fetch apps in a coroutine
                // launched in the flow's scope to handle background work.
                launch {
                    sendUpdatedApps()
                }
            }
        }

        // On Android 13 (API 33) and above, you should specify the receiver export flag
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        // Register receiver
        context!!.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            receiverFlags
        )

        // Cleanup when the flow is cancelled
        awaitClose {
            context?.unregisterReceiver(receiver)
        }
    }.flowOn(Dispatchers.IO) // Ensure upstream operations run on the IO dispatcher


    private fun getLaunchableApps(
        withIcon: Boolean,
    ): List<AppDetails> {
        val packageManager = context!!.packageManager

        // Create an intent for MAIN/LAUNCHER activities
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        var launchableApps = packageManager.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.applicationInfo }


        return launchableApps.mapNotNull { app ->
            app.toPigeon(packageManager, withIcon)
        }
    }

    private fun isAppInstalled(packageName: String?): Boolean {
        val packageManager: PackageManager = context!!.packageManager
        return try {
            packageManager.getPackageInfo(packageName ?: "", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun onHomeButtonPressed() {
        pluginScope.launch(Dispatchers.Main.immediate) {
            flutterApi?.onHomeIntentReceived { }
        }
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(packageInfo: PackageInfo): Long {
        return if (SDK_INT < P) packageInfo.versionCode.toLong()
        else packageInfo.longVersionCode
    }


}
