package com.lyro.app.core.matcher

import kotlin.math.abs

object TrackMetadataNormalizer {

    private val JUNK_PATTERNS = listOf(
        Regex("""\s*[\(\[](official\s*(music\s*)?(video|audio|lyric\s*video)?|audio|lyrics?|visualizer|hd|hq|4k|remastered)[\)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s*-\s*(official\s*(music\s*)?(video|audio)?|audio|lyrics?)""", RegexOption.IGNORE_CASE),
        Regex("""\s*[\(\[](feat\.|ft\.).*?[\)\]]""", RegexOption.IGNORE_CASE)
    )

    private val MODIFIERS = listOf(
        "remix",
        "live",
        "acoustic",
        "instrumental",
        "cover",
        "slowed",
        "reverb",
        "sped up"
    )

    fun normalizeTitle(title: String): String {
        var clean = title.trim().lowercase()
        for (pattern in JUNK_PATTERNS) {
            clean = pattern.replace(clean, "")
        }
        clean = clean.replace(Regex("""[^\w\s]"""), " ")
        return clean.replace(Regex("""\s+"""), " ").trim()
    }

    fun normalizeArtist(artist: String): String {
        var clean = artist.trim().lowercase()
        clean = clean.replace(" - topic", "").replace("vevo", "")
        clean = clean.replace(Regex("""[^\w\s]"""), " ")
        return clean.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Conservatively checks if two tracks represent the exact same musical recording.
     */
    fun matches(
        title1: String,
        artist1: String,
        duration1: Long,
        title2: String,
        artist2: String,
        duration2: Long,
        toleranceMs: Long = 10000L
    ): Boolean {
        val t1Lower = title1.lowercase()
        val t2Lower = title2.lowercase()

        // 1. Modifiers check (e.g. remix, live, acoustic, cover must match in both)
        for (mod in MODIFIERS) {
            if (t1Lower.contains(mod) != t2Lower.contains(mod)) {
                return false
            }
        }

        // 2. Duration check if both durations are available
        if (duration1 > 0L && duration2 > 0L) {
            if (abs(duration1 - duration2) > toleranceMs) {
                return false
            }
        }

        // 3. Normalized Title check
        val normT1 = normalizeTitle(title1)
        val normT2 = normalizeTitle(title2)
        if (normT1.isBlank() || normT2.isBlank()) return false
        val titleMatches = normT1 == normT2

        if (!titleMatches) return false

        // 4. Normalized Artist check (Do NOT use title alone)
        val normA1 = normalizeArtist(artist1)
        val normA2 = normalizeArtist(artist2)
        if (normA1.isBlank() || normA2.isBlank()) return false

        val artistMatches = normA1 == normA2 ||
                normA1.contains(normA2) ||
                normA2.contains(normA1)

        return artistMatches
    }
}
