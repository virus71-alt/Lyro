package com.lyro.doyalabs

import android.content.Context
import android.content.Intent
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : AudioServiceActivity() {

    /** Quick-mix action from a widget tap, waiting for Flutter to ask. */
    private var pendingAction: String? = null
    private var actions: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val appContext = applicationContext

        // Flutter → home-screen widgets: store now-playing state and redraw.
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "lyro/widget")
            .setMethodCallHandler { call, result ->
                if (call.method == "update") {
                    appContext.getSharedPreferences(WidgetRender.PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("hasSong", call.argument<Boolean>("hasSong") ?: false)
                        .putString("title", call.argument<String>("title") ?: "")
                        .putString("artist", call.argument<String>("artist") ?: "")
                        .putBoolean("playing", call.argument<Boolean>("playing") ?: false)
                        .putFloat("progress", (call.argument<Double>("progress") ?: 0.0).toFloat())
                        .putString("artPath", call.argument<String>("artPath"))
                        .apply()
                    WidgetRender.refreshAll(appContext)
                    result.success(null)
                } else {
                    result.notImplemented()
                }
            }

        // Equalizer engine on the player's audio session (see LyroEq).
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "lyro/eq")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "attach" -> result.success(LyroEq.attach(call.argument<Int>("session") ?: 0))
                    "apply" -> {
                        LyroEq.apply(
                            call.argument<Boolean>("enabled") ?: false,
                            call.argument<Double>("inputGain") ?: 0.0,
                            call.argument<List<Double>>("cutoffs") ?: emptyList(),
                            call.argument<List<Double>>("centers") ?: emptyList(),
                            call.argument<List<Double>>("gains") ?: emptyList(),
                        )
                        result.success(null)
                    }
                    "release" -> {
                        LyroEq.release()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        // Web stream: keep the phone awake and on Wi-Fi while serving.
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "lyro/stream")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "start" -> {
                        LyroStreamService.start(appContext, call.argument<String>("address"))
                        result.success(null)
                    }
                    "stop" -> {
                        LyroStreamService.stop(appContext)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        // Widget quick mixes → Flutter ("shuffle_all", "favorites", "most_played").
        pendingAction = intent?.getStringExtra(WidgetRender.EXTRA_ACTION)
        actions = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "lyro/actions").apply {
            setMethodCallHandler { call, result ->
                if (call.method == "takePendingAction") {
                    result.success(pendingAction)
                    pendingAction = null
                } else {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val action = intent.getStringExtra(WidgetRender.EXTRA_ACTION) ?: return
        val channel = actions
        if (channel != null) channel.invokeMethod("action", action) else pendingAction = action
    }
}
