package com.lyro.doyalabs

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import kotlin.math.abs

/**
 * Lyro's equalizer engine, attached to the player's audio session.
 *
 * Android 9+: DynamicsProcessing with a 32-band pre-EQ (band edges and gains
 * computed in Flutter from the IEM's parametric correction), an input gain
 * for headroom and a limiter as a safety net. Older Android: the system
 * Equalizer, with the same curve sampled at its fixed band centres.
 */
object LyroEq {
    private const val TAG = "LyroEq"
    private var session = -1
    private var dynamics: DynamicsProcessing? = null
    private var legacy: Equalizer? = null
    private var bandCount = 0

    fun attach(sessionId: Int): Boolean {
        if (sessionId == session && (dynamics != null || legacy != null)) return true
        release()
        session = sessionId
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bandCount = 32
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, bandCount, // pre-EQ
                    false, 0, // multiband compressor
                    false, 0, // post-EQ
                    true, // limiter
                ).build()
                dynamics = DynamicsProcessing(0, sessionId, config).apply {
                    setLimiterAllChannelsTo(
                        DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f)
                    )
                    enabled = false
                }
            } else {
                legacy = Equalizer(0, sessionId).apply { enabled = false }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer unavailable: $e")
            release()
            false
        }
    }

    fun apply(
        enabled: Boolean,
        inputGain: Double,
        cutoffs: List<Double>,
        centers: List<Double>,
        gains: List<Double>,
    ) {
        try {
            dynamics?.let { dp ->
                if (enabled) {
                    for (i in 0 until minOf(bandCount, cutoffs.size, gains.size)) {
                        dp.setPreEqBandAllChannelsTo(
                            i,
                            DynamicsProcessing.EqBand(true, cutoffs[i].toFloat(), gains[i].toFloat())
                        )
                    }
                    dp.setInputGainAllChannelsTo(inputGain.toFloat())
                }
                dp.enabled = enabled
            }
            legacy?.let { eq ->
                if (enabled) {
                    val range = eq.bandLevelRange // millibels
                    for (b in 0 until eq.numberOfBands) {
                        val hz = eq.getCenterFreq(b.toShort()) / 1000.0
                        // nearest computed band
                        var best = 0
                        for (i in centers.indices) {
                            if (abs(centers[i] - hz) < abs(centers[best] - hz)) best = i
                        }
                        val mb = ((gains[best] + inputGain) * 100).toInt()
                            .coerceIn(range[0].toInt(), range[1].toInt())
                        eq.setBandLevel(b.toShort(), mb.toShort())
                    }
                }
                eq.enabled = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer apply failed: $e")
        }
    }

    fun release() {
        try {
            dynamics?.release()
            legacy?.release()
        } catch (_: Exception) {
        }
        dynamics = null
        legacy = null
        session = -1
    }
}
