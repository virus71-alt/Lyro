package com.lyro.app.data.download

/**
 * Distinguishes the ownership origin of a downloaded track.
 *
 * MANUAL: The user explicitly tapped Download / Keep offline. Never auto-deleted or rotated.
 * SMART: Lyro automatically downloaded based on TasteProfile. Can be rotated within storage budget.
 */
enum class DownloadOrigin {
    MANUAL,
    SMART
}
