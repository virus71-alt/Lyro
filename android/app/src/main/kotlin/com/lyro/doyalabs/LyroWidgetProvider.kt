package com.lyro.doyalabs

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.ryanheise.audioservice.MediaButtonReceiver
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Lyro home-screen widgets. Flutter writes the now-playing state into
 * [WidgetRender.PREFS] (see MainActivity) and calls [WidgetRender.refreshAll].
 * Playback buttons send standard media-button key events to audio_service's
 * receiver; quick-mix buttons open Lyro with an action extra. Colours come
 * from resources, so widgets follow the phone's light / dark theme.
 */
object WidgetRender {
    const val PREFS = "lyro_widget"
    const val EXTRA_ACTION = "lyro_action"

    private val providers = listOf(
        LyroWidgetProvider::class.java,
        LyroDiscWidget::class.java,
        LyroBarWidget::class.java,
        LyroMixesWidget::class.java,
    )

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (cls in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, build(context, cls))
        }
    }

    fun build(context: Context, cls: Class<*>): RemoteViews = when (cls) {
        LyroDiscWidget::class.java -> disc(context)
        LyroBarWidget::class.java -> bar(context)
        LyroMixesWidget::class.java -> mixes(context)
        else -> nowPlaying(context)
    }

    // ------------------------------------------------------------ layouts

    private fun nowPlaying(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.lyro_widget)
        bindText(context, views)
        bindArt(context, views, R.id.widget_art)
        bindControls(context, views, prev = true, next = true)
        views.setImageViewBitmap(R.id.widget_progress, dots(context, state(context).progress))
        return views
    }

    private fun bar(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.lyro_widget_bar)
        bindText(context, views)
        bindArt(context, views, R.id.widget_art)
        bindControls(context, views, prev = true, next = true)
        return views
    }

    private fun disc(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.lyro_widget_disc)
        val s = state(context)
        views.setImageViewBitmap(R.id.widget_disc, discBitmap(context, s.artPath, s.progress))
        views.setContentDescription(
            R.id.widget_disc,
            if (s.hasSong) "${s.title}, ${s.artist}" else "Lyro, nothing playing"
        )
        bindControls(context, views, prev = false, next = false)
        return views
    }

    private fun mixes(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.lyro_widget_mixes)
        views.setOnClickPendingIntent(R.id.mix_shuffle, action(context, "shuffle_all"))
        views.setOnClickPendingIntent(R.id.mix_favorites, action(context, "favorites"))
        views.setOnClickPendingIntent(R.id.mix_most_played, action(context, "most_played"))
        return views
    }

    // ------------------------------------------------------------ binding

    private data class State(
        val hasSong: Boolean,
        val title: String,
        val artist: String,
        val playing: Boolean,
        val progress: Float,
        val artPath: String?,
    )

    private fun state(context: Context): State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            p.getBoolean("hasSong", false),
            p.getString("title", "") ?: "",
            p.getString("artist", "") ?: "",
            p.getBoolean("playing", false),
            p.getFloat("progress", 0f),
            p.getString("artPath", null),
        )
    }

    private fun bindText(context: Context, views: RemoteViews) {
        val s = state(context)
        views.setTextViewText(R.id.widget_title, if (s.hasSong) s.title else "Lyro")
        views.setTextViewText(R.id.widget_artist, (if (s.hasSong) s.artist else "Nothing playing").uppercase())
    }

    private fun bindArt(context: Context, views: RemoteViews, id: Int) {
        val art = state(context).artPath?.let { decodeArt(it, 256) }
        if (art != null) views.setImageViewBitmap(id, art) else views.setImageViewResource(id, R.mipmap.ic_launcher)
    }

    private fun bindControls(context: Context, views: RemoteViews, prev: Boolean, next: Boolean) {
        val playing = state(context).playing
        views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.widget_pause else R.drawable.widget_play)
        views.setContentDescription(R.id.widget_play, if (playing) "Pause" else "Play")
        views.setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        if (prev) views.setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        if (next) views.setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
        openApp(context)?.let { views.setOnClickPendingIntent(R.id.widget_root, it) }
    }

    // ------------------------------------------------------------ intents

    private fun openApp(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /** Opens Lyro and asks it to start a mix (handled in Flutter). */
    private fun action(context: Context, name: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction("com.lyro.doyalabs.MIX_$name")
            .putExtra(EXTRA_ACTION, name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, name.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun mediaButton(context: Context, keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return PendingIntent.getBroadcast(
            context, keyCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ------------------------------------------------------------ bitmaps

    private fun decodeArt(path: String, target: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }

    /** Dotted progress line: red dots played, faint dots remaining. */
    private fun dots(context: Context, progress: Float): Bitmap {
        val w = 560
        val h = 12
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.widget_red) }
        val rest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.widget_dot_rest) }
        val pitch = 14f
        for (i in 0 until (w / pitch).toInt()) {
            val x = i * pitch + pitch / 2
            canvas.drawCircle(x, h / 2f, 3f, if (x / w <= progress) played else rest)
        }
        return bmp
    }

    /** Round album art inside a ring of dots lit red for the progress. */
    private fun discBitmap(context: Context, artPath: String?, progress: Float): Bitmap {
        val size = 420
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val ringR = size * 0.465f
        val artR = size * 0.39f

        // Ring of 48 dots, starting at 12 o'clock, clockwise
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.widget_red) }
        val rest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.widget_dot_rest) }
        val n = 48
        for (i in 0 until n) {
            val a = -Math.PI / 2 + i * 2 * Math.PI / n
            canvas.drawCircle(
                (c + ringR * cos(a)).toFloat(), (c + ringR * sin(a)).toFloat(), 5.5f,
                if ((i + 0.5f) / n <= progress) lit else rest
            )
        }

        // Circular art (or an empty disc)
        val art = artPath?.let { decodeArt(it, 320) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (art != null) {
            val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = (artR * 2) / min(art.width, art.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(c - art.width * scale / 2, c - art.height * scale / 2)
            })
            paint.shader = shader
        } else {
            paint.color = ContextCompat.getColor(context, R.color.widget_tile)
        }
        canvas.drawCircle(c, c, artR, paint)

        // Spindle hole, like a record
        canvas.drawCircle(c, c, max(6f, artR * 0.07f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.widget_bg_solid)
        })
        return bmp
    }
}

/** Now playing (4x2): art, title, artist, dotted progress, controls. */
class LyroWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, WidgetRender.build(context, javaClass))
    }

    companion object {
        const val PREFS = WidgetRender.PREFS
        fun refresh(context: Context) = WidgetRender.refreshAll(context)
    }
}

/** Glyph Disc (2x2). */
class LyroDiscWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, WidgetRender.build(context, javaClass))
    }
}

/** Compact bar (4x1). */
class LyroBarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, WidgetRender.build(context, javaClass))
    }
}

/** Quick mixes (4x1). */
class LyroMixesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, WidgetRender.build(context, javaClass))
    }
}
