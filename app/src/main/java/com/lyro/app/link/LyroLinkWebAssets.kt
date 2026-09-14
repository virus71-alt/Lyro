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
    }

    private var cachedIndexHtml: String? = null

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
}
