package com.lyro.app.link

import android.content.Context
import android.util.Log
import java.io.InputStream

/**
 * Loads embedded HTML, CSS, and JS web assets directly from app assets.
 * Guarantees 100% offline functionality without any external CDN dependency.
 */
class LyroLinkWebAssets(private val context: Context) {

    companion object {
        private const val TAG = "LyroLinkWebAssets"
        private const val WEB_INDEX_PATH = "link_web/index.html"
        private const val WEB_CSS_PATH = "link_web/styles.css"
        private const val WEB_JS_PATH = "link_web/app.js"
        private const val WEB_ASSETS_DIR = "link_web/assets"
    }

    private var cachedIndexHtml: String? = null
    private var cachedStylesCss: String? = null
    private var cachedAppJs: String? = null

    /**
     * Returns the complete Single Page Application HTML content.
     */
    fun getIndexHtml(): String {
        cachedIndexHtml?.let { return it }

        return try {
            context.assets.open(WEB_INDEX_PATH).use { input: InputStream ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }.also { cachedIndexHtml = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $WEB_INDEX_PATH from assets: ${e.message}", e)
            "<!DOCTYPE html><html><body><h1>Lyro Link</h1><p>Web player asset loading failed: ${e.message}</p></body></html>"
        }
    }

    /**
     * Returns the embedded CSS stylesheet.
     */
    fun getStylesCss(): String {
        cachedStylesCss?.let { return it }

        return try {
            context.assets.open(WEB_CSS_PATH).use { input: InputStream ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }.also { cachedStylesCss = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $WEB_CSS_PATH from assets: ${e.message}", e)
            ""
        }
    }

    /**
     * Returns the embedded JavaScript application code.
     */
    fun getAppJs(): String {
        cachedAppJs?.let { return it }

        return try {
            context.assets.open(WEB_JS_PATH).use { input: InputStream ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }.also { cachedAppJs = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $WEB_JS_PATH from assets: ${e.message}", e)
            ""
        }
    }

    /**
     * Opens an asset file from link_web/assets/ safely.
     */
    fun openAsset(relativePath: String): Pair<InputStream, Long>? {
        val sanitized = relativePath.replace('\\', '/').trimStart('/')
        if (sanitized.contains("..") || sanitized.contains("//")) {
            Log.w(TAG, "Blocked path traversal in asset request: $relativePath")
            return null
        }

        val assetPath = "$WEB_ASSETS_DIR/$sanitized"
        return try {
            val fd = try {
                context.assets.openFd(assetPath)
            } catch (ignored: Exception) {
                null
            }

            val length = fd?.length ?: -1L
            fd?.close()

            val stream = context.assets.open(assetPath)
            Pair(stream, if (length >= 0) length else stream.available().toLong())
        } catch (e: Exception) {
            Log.w(TAG, "Asset not found: $assetPath (${e.message})")
            null
        }
    }

    fun getMimeType(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".ico") -> "image/x-icon"
            lower.endsWith(".css") -> "text/css; charset=utf-8"
            lower.endsWith(".js") -> "application/javascript; charset=utf-8"
            lower.endsWith(".json") -> "application/json; charset=utf-8"
            lower.endsWith(".html") -> "text/html; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}
