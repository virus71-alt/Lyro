package com.lyro.app.link

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.BindException

/**
 * Embedded NanoHTTPD server manager for Lyro Link.
 * Automatically tries subsequent ports if the default port is occupied.
 */
class LyroLinkServer(
    initialPort: Int = 8765,
    private val routes: LyroLinkRoutes
) {

    companion object {
        private const val TAG = "LyroLinkServer"
        private const val SOCKET_TIMEOUT_MS = 30_000
    }

    private var innerServer: NanoHTTPD? = null
    private var activePort: Int = initialPort

    fun getActivePort(): Int = activePort

    /**
     * Attempts to start the server, retrying on subsequent ports if bound.
     */
    @Synchronized
    fun startSafe(): Int {
        var portToTry = activePort
        for (i in 0 until 20) {
            try {
                val server = object : NanoHTTPD(portToTry) {
                    override fun serve(session: IHTTPSession): Response {
                        return routes.handleRequest(session)
                    }
                }
                server.start(SOCKET_TIMEOUT_MS, false)
                innerServer = server
                activePort = portToTry
                Log.i(TAG, "Lyro Link server successfully started on port $activePort")
                return activePort
            } catch (e: BindException) {
                Log.w(TAG, "Port $portToTry is in use, trying ${portToTry + 1}...")
                portToTry++
            } catch (e: IOException) {
                if (e.message?.contains("bind", ignoreCase = true) == true || e.cause is BindException) {
                    portToTry++
                } else {
                    Log.e(TAG, "Failed to start server on port $portToTry: ${e.message}", e)
                    throw e
                }
            }
        }
        throw IOException("Could not find an open port for Lyro Link")
    }

    @Synchronized
    fun stop() {
        try {
            innerServer?.stop()
            innerServer = null
            Log.i(TAG, "Lyro Link server stopped cleanly")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping server: ${e.message}")
        }
    }
}
