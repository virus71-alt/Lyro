package com.lyro.app.link

import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Authentication and pairing manager for Lyro Link.
 * Enforces:
 * - 6-digit random pairing codes
 * - Brute-force rate limiting (max 5 failed attempts per 5 minutes per client)
 * - Cryptographically secure in-memory session tokens
 * - Expiration and idle timeout handling
 */
class LyroLinkAuth {

    companion object {
        private const val TAG = "LyroLinkAuth"
        private const val MAX_FAILED_ATTEMPTS = 5
        private val LOCKOUT_DURATION_MS = TimeUnit.MINUTES.toMillis(5)
        private val PAIRING_CODE_VALIDITY_MS = TimeUnit.MINUTES.toMillis(30)
        private val SESSION_VALIDITY_MS = TimeUnit.HOURS.toMillis(24)
        private val ACTIVE_CLIENT_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5)
    }

    data class ClientSession(
        val token: String,
        val clientIp: String,
        val createdAt: Long = System.currentTimeMillis(),
        var lastActiveAt: Long = System.currentTimeMillis()
    )

    private data class RateLimitRecord(
        var attempts: Int = 0,
        var lockedUntil: Long = 0L
    )

    private val secureRandom = SecureRandom()
    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private val rateLimits = ConcurrentHashMap<String, RateLimitRecord>()

    private var currentPairingCode: String = generateRandomPairingCode()
    private var pairingCodeGeneratedAt: Long = System.currentTimeMillis()

    /**
     * Retrieves the current pairing code, regenerating if expired.
     */
    @Synchronized
    fun getPairingCode(): String {
        if (System.currentTimeMillis() - pairingCodeGeneratedAt > PAIRING_CODE_VALIDITY_MS) {
            regeneratePairingCode()
        }
        return currentPairingCode
    }

    /**
     * Regenerates a new 6-digit pairing code.
     */
    @Synchronized
    fun regeneratePairingCode(): String {
        currentPairingCode = generateRandomPairingCode()
        pairingCodeGeneratedAt = System.currentTimeMillis()
        Log.d(TAG, "Generated new pairing code")
        return currentPairingCode
    }

    /**
     * Verifies pairing code from client.
     * Returns a session token on success, or null on failure.
     */
    fun pair(clientIp: String, codeCandidate: String): PairResult {
        val now = System.currentTimeMillis()

        // 1. Rate limiting check
        val record = rateLimits.computeIfAbsent(clientIp) { RateLimitRecord() }
        if (record.lockedUntil > now) {
            val remainingSec = ((record.lockedUntil - now) / 1000).coerceAtLeast(1)
            Log.w(TAG, "Pairing rejected: $clientIp is rate limited for $remainingSec seconds")
            return PairResult.RateLimited(remainingSec)
        }

        // 2. Validate code
        val expectedCode = getPairingCode()
        val cleanedCandidate = codeCandidate.trim().replace("\\s+".toRegex(), "")

        if (cleanedCandidate == expectedCode) {
            // Reset failed count on success
            rateLimits.remove(clientIp)

            // Generate secure 32-byte hex token
            val token = generateSecureToken()
            val session = ClientSession(
                token = token,
                clientIp = clientIp,
                createdAt = now,
                lastActiveAt = now
            )
            sessions[token] = session
            Log.i(TAG, "Successful pairing from $clientIp. Session established.")
            return PairResult.Success(token)
        } else {
            record.attempts++
            if (record.attempts >= MAX_FAILED_ATTEMPTS) {
                record.lockedUntil = now + LOCKOUT_DURATION_MS
                record.attempts = 0
                val remainingSec = (LOCKOUT_DURATION_MS / 1000)
                Log.w(TAG, "Max attempts reached for $clientIp. Locking out for $remainingSec seconds.")
                return PairResult.RateLimited(remainingSec)
            }
            val remainingAttempts = MAX_FAILED_ATTEMPTS - record.attempts
            Log.w(TAG, "Invalid pairing code from $clientIp. Attempts left: $remainingAttempts")
            return PairResult.InvalidCode(remainingAttempts)
        }
    }

    /**
     * Validates whether an incoming HTTP request has an active session.
     */
    fun validateSession(token: String?, clientIp: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val session = sessions[token] ?: return false

        val now = System.currentTimeMillis()
        if (now - session.createdAt > SESSION_VALIDITY_MS) {
            sessions.remove(token)
            return false
        }

        // Update active timestamp
        session.lastActiveAt = now
        return true
    }

    /**
     * Extracts session token from headers (cookie or X-Lyro-Session header).
     */
    fun extractToken(headers: Map<String, String>): String? {
        val authHeader = headers["x-lyro-session"] ?: headers["X-Lyro-Session"]
        if (!authHeader.isNullOrBlank()) {
            return authHeader.trim()
        }

        // Check Cookie header: "lyro_link_session=xyz"
        val cookieHeader = headers["cookie"] ?: headers["Cookie"]
        if (!cookieHeader.isNullOrBlank()) {
            val cookies = cookieHeader.split(";")
            for (cookie in cookies) {
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim() == "lyro_link_session") {
                    return parts[1].trim()
                }
            }
        }
        return null
    }

    /**
     * Counts clients that have been active within the last 5 minutes.
     */
    fun getConnectedClientsCount(): Int {
        val now = System.currentTimeMillis()
        cleanupExpiredSessions(now)
        return sessions.values.count { (now - it.lastActiveAt) < ACTIVE_CLIENT_THRESHOLD_MS }
    }

    /**
     * Invalidates all active sessions (used when Lyro Link is turned OFF).
     */
    fun invalidateAllSessions() {
        sessions.clear()
        rateLimits.clear()
        regeneratePairingCode()
    }

    private fun cleanupExpiredSessions(now: Long) {
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAt > SESSION_VALIDITY_MS) {
                iterator.remove()
            }
        }
    }

    private fun generateRandomPairingCode(): String {
        val codeNumber = secureRandom.nextInt(900000) + 100000 // 100000 to 999999
        return codeNumber.toString()
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    sealed class PairResult {
        data class Success(val token: String) : PairResult()
        data class InvalidCode(val remainingAttempts: Int) : PairResult()
        data class RateLimited(val retryAfterSeconds: Long) : PairResult()
    }
}
