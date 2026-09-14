package com.lyro.app.link

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.util.Log
import com.lyro.app.LyroApplication
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.recommendation.model.EventType
import com.lyro.app.recommendation.model.ListeningEvent
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Dispatches and processes all HTTP routes for Lyro Link.
 * Enforces session authentication for all stateful and media-serving endpoints.
 */
class LyroLinkRoutes(
    private val context: Context,
    private val auth: LyroLinkAuth,
    private val libraryProvider: LyroLinkLibraryProvider,
    private val streamProvider: LyroLinkStreamProvider,
    private val webAssets: LyroLinkWebAssets
) {

    companion object {
        private const val TAG = "LyroLinkRoutes"
    }

    fun handleRequest(session: IHTTPSession): Response {
        val uri = session.uri.trim()
        val method = session.method
        val clientIp = session.headers["remote-addr"] ?: try { session.remoteIpAddress } catch (e: Throwable) { "unknown" }

        return try {
            when {
                // 1. Web UI & Static Assets
                (uri == "/" || uri == "/index.html") && method == NanoHTTPD.Method.GET -> {
                    serveWebIndex()
                }

                uri == "/styles.css" && method == NanoHTTPD.Method.GET -> {
                    serveWebCss()
                }

                uri == "/app.js" && method == NanoHTTPD.Method.GET -> {
                    serveWebJs()
                }

                uri.startsWith("/assets/") && method == NanoHTTPD.Method.GET -> {
                    serveStaticAsset(uri.removePrefix("/assets/"))
                }

                uri == "/favicon.ico" && method == NanoHTTPD.Method.GET -> {
                    serveStaticAsset("logo.png")
                }

                // 2. Authentication: Pairing
                uri == "/api/auth/pair" && method == NanoHTTPD.Method.POST -> {
                    handlePairing(session, clientIp)
                }

                // 3. Authentication: Session verification
                uri == "/api/auth/check" && method == NanoHTTPD.Method.GET -> {
                    handleAuthCheck(session, clientIp)
                }

                // Authenticated routes below:
                else -> {
                    val token = extractToken(session)
                    if (!auth.validateSession(token, clientIp)) {
                        return unauthorizedResponse()
                    }

                    when {
                        uri == "/api/status" && method == NanoHTTPD.Method.GET -> {
                            handleStatus()
                        }

                        uri == "/api/home" && method == NanoHTTPD.Method.GET -> {
                            handleHome()
                        }

                        uri == "/api/library" && method == NanoHTTPD.Method.GET -> {
                            handleLibrary()
                        }

                        uri == "/api/quick-picks" && method == NanoHTTPD.Method.GET -> {
                            handleQuickPicks()
                        }

                        uri == "/api/liked" && method == NanoHTTPD.Method.GET -> {
                            handleLiked()
                        }

                        uri.startsWith("/api/search") && method == NanoHTTPD.Method.GET -> {
                            val query = session.parms["q"] ?: ""
                            handleSearch(query)
                        }

                        uri.startsWith("/api/artwork/") && method == NanoHTTPD.Method.GET -> {
                            val trackId = uri.removePrefix("/api/artwork/")
                            handleArtwork(trackId)
                        }

                        uri.startsWith("/api/tracks/") && uri.endsWith("/favorite") && method == NanoHTTPD.Method.POST -> {
                            val trackId = uri.removePrefix("/api/tracks/").removeSuffix("/favorite")
                            handleToggleFavorite(trackId)
                        }

                        uri == "/api/events" && method == NanoHTTPD.Method.POST -> {
                            handleListeningEvent(session)
                        }

                        uri.startsWith("/stream/") && method == NanoHTTPD.Method.GET -> {
                            val trackId = uri.removePrefix("/stream/")
                            streamProvider.serveStream(trackId, session.headers)
                        }

                        else -> {
                            NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request handling exception: ${e.message}", e)
            jsonResponse(Response.Status.INTERNAL_ERROR, JSONObject().apply { put("error", e.message ?: "Internal Error") })
        }
    }

    private fun serveWebIndex(): Response {
        val html = webAssets.getIndexHtml()
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun serveWebCss(): Response {
        val css = webAssets.getStylesCss()
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/css; charset=utf-8", css)
    }

    private fun serveWebJs(): Response {
        val js = webAssets.getAppJs()
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/javascript; charset=utf-8", js)
    }

    private fun serveStaticAsset(relativePath: String): Response {
        val asset = webAssets.openAsset(relativePath)
            ?: return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Asset Not Found")
        val mime = webAssets.getMimeType(relativePath)
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime, asset.first, asset.second)
    }

    private fun handleHome(): Response {
        val shelves = runBlocking { libraryProvider.getHomeShelves() }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", shelves.toJson().toString())
    }

    private fun handlePairing(session: IHTTPSession, clientIp: String): Response {
        val body = parseJsonBody(session) ?: return jsonResponse(
            Response.Status.BAD_REQUEST,
            JSONObject().apply { put("error", "Invalid JSON payload") }
        )

        val code = body.optString("code", "").trim()
        if (code.isBlank()) {
            return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().apply { put("error", "Code is required") })
        }

        return when (val result = auth.pair(clientIp, code)) {
            is LyroLinkAuth.PairResult.Success -> {
                val json = JSONObject().apply {
                    put("success", true)
                    put("token", result.token)
                }
                val resp = jsonResponse(Response.Status.OK, json)
                resp.addHeader("Set-Cookie", "lyro_link_session=${result.token}; Path=/; HttpOnly; SameSite=Lax")
                resp
            }
            is LyroLinkAuth.PairResult.InvalidCode -> {
                val json = JSONObject().apply {
                    put("success", false)
                    put("error", "Incorrect pairing code. Attempts remaining: ${result.remainingAttempts}")
                    put("remaining", result.remainingAttempts)
                }
                jsonResponse(Response.Status.BAD_REQUEST, json)
            }
            is LyroLinkAuth.PairResult.RateLimited -> {
                val json = JSONObject().apply {
                    put("success", false)
                    put("error", "Too many incorrect attempts. Please wait ${result.retryAfterSeconds} seconds.")
                    put("retryAfter", result.retryAfterSeconds)
                }
                jsonResponse(Response.Status.TOO_MANY_REQUESTS, json)
            }
        }
    }

    private fun handleAuthCheck(session: IHTTPSession, clientIp: String): Response {
        val token = extractToken(session)
        val valid = auth.validateSession(token, clientIp)
        val json = JSONObject().apply { put("authenticated", valid) }
        return if (valid) {
            jsonResponse(Response.Status.OK, json)
        } else {
            jsonResponse(Response.Status.UNAUTHORIZED, json)
        }
    }

    private fun handleStatus(): Response {
        val app = LyroApplication.instance
        val isOnline = app.networkMonitor.isOnline.value
        val connectedClients = auth.getConnectedClientsCount()

        val json = JSONObject().apply {
            put("connected", true)
            put("deviceName", "Rahul's Lyro")
            put("isOnline", isOnline)
            put("connectedClients", connectedClients)
        }
        return jsonResponse(Response.Status.OK, json)
    }

    private fun handleLibrary(): Response {
        val tracks = runBlocking { libraryProvider.getLibraryTracks() }
        val array = JSONArray()
        tracks.forEach { array.put(it.toJson()) }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
    }

    private fun handleQuickPicks(): Response {
        val tracks = runBlocking { libraryProvider.getQuickPicks() }
        val array = JSONArray()
        tracks.forEach { array.put(it.toJson()) }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
    }

    private fun handleLiked(): Response {
        val tracks = runBlocking { libraryProvider.getLikedTracks() }
        val array = JSONArray()
        tracks.forEach { array.put(it.toJson()) }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
    }

    private fun handleSearch(query: String): Response {
        val tracks = runBlocking { libraryProvider.search(query) }
        val array = JSONArray()
        tracks.forEach { array.put(it.toJson()) }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
    }

    private fun handleArtwork(trackId: String): Response {
        val sanitized = libraryProvider.sanitizeTrackId(trackId)
            ?: return fallbackArtworkResponse()

        val app = LyroApplication.instance
        try {
            if (sanitized.startsWith("local_")) {
                val songId = sanitized.removePrefix("local_").toLongOrNull()
                if (songId != null) {
                    val song = app.musicRepository.allSongs.value.firstOrNull { it.id == songId }
                    if (song != null) {
                        val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                        try {
                            val stream = context.contentResolver.openInputStream(artUri)
                            if (stream != null) {
                                return NanoHTTPD.newChunkedResponse(Response.Status.OK, "image/jpeg", stream)
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            } else if (sanitized.startsWith("yt_")) {
                val videoId = sanitized.removePrefix("yt_")
                val artworkFile = File(context.filesDir, "artwork/$videoId.jpg")
                if (artworkFile.exists()) {
                    return NanoHTTPD.newFixedLengthResponse(
                        Response.Status.OK,
                        "image/jpeg",
                        FileInputStream(artworkFile),
                        artworkFile.length()
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load artwork for $trackId: ${e.message}")
        }

        return fallbackArtworkResponse()
    }

    private fun handleToggleFavorite(trackId: String): Response {
        val sanitized = libraryProvider.sanitizeTrackId(trackId)
            ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().apply { put("error", "Invalid ID") })

        val isFav = runBlocking { libraryProvider.toggleFavorite(sanitized) }
        val json = JSONObject().apply {
            put("id", sanitized)
            put("isFavorite", isFav)
        }
        return jsonResponse(Response.Status.OK, json)
    }

    private fun handleListeningEvent(session: IHTTPSession): Response {
        val body = parseJsonBody(session)
            ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().apply { put("error", "Invalid payload") })

        val trackId = body.optString("trackId")
        val rawEventType = body.optString("eventType")
        val positionMs = body.optLong("positionMs", 0L)
        val durationMs = body.optLong("durationMs", 0L)
        val sessionId = body.optString("sessionId", "lyro_link_session")

        val sanitized = libraryProvider.sanitizeTrackId(trackId) ?: return jsonResponse(
            Response.Status.BAD_REQUEST,
            JSONObject().apply { put("error", "Invalid track ID") }
        )

        val mappedEvent = when (rawEventType) {
            "PLAY_STARTED" -> EventType.PLAY_STARTED
            "PLAY_50_PERCENT" -> EventType.PLAY_50_PERCENT
            "PLAY_COMPLETED" -> EventType.PLAY_COMPLETED
            "SKIPPED_EARLY" -> EventType.SKIPPED_EARLY
            else -> EventType.MANUAL_PLAY
        }

        runBlocking {
            val track = libraryProvider.resolveTrack(sanitized)
            if (track != null) {
                try {
                    val event = ListeningEvent(
                        playbackSessionId = sessionId,
                        videoId = (track as? OnlineTrack)?.videoId ?: track.onlineVideoId ?: sanitized,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        eventType = mappedEvent,
                        durationMs = durationMs,
                        positionMs = positionMs,
                        percentageListened = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        isManual = true
                    )
                    LyroApplication.instance.listeningEventRepository.recordEvent(event)
                    Log.d(TAG, "Recorded Lyro Link listening event: $mappedEvent for ${track.title}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record event: ${e.message}")
                }
            }
        }

        return jsonResponse(Response.Status.OK, JSONObject().apply { put("status", "recorded") })
    }

    private fun extractToken(session: IHTTPSession): String? {
        val headerToken = auth.extractToken(session.headers)
        if (!headerToken.isNullOrBlank()) return headerToken

        // Fallback check: query param "?session=xyz" for <audio src="..."> or <img>
        val queryToken = session.parms["session"]
        if (!queryToken.isNullOrBlank()) return queryToken

        return null
    }

    private fun parseJsonBody(session: IHTTPSession): JSONObject? {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val postData = files["postData"] ?: return null
            JSONObject(postData)
        } catch (e: Exception) {
            Log.w(TAG, "JSON body parse failed: ${e.message}")
            null
        }
    }

    private fun jsonResponse(status: Response.Status, json: JSONObject): Response {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", json.toString())
    }

    private fun unauthorizedResponse(): Response {
        val json = JSONObject().apply {
            put("error", "Unauthorized. Pair your browser with Lyro first.")
            put("authenticated", false)
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json; charset=utf-8", json.toString())
    }

    private fun fallbackArtworkResponse(): Response {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 24 24" fill="#222222"><rect width="24" height="24" fill="#161616"/><path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z" fill="#E0FE10"/></svg>"""
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "image/svg+xml", svg)
    }
}
