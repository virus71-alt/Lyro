package com.lyro.app.recommendation.radio

/**
 * Types of Radio experiences supported by Lyro.
 * Phase 1 fully implements SONG radio.
 * ARTIST and PERSONAL ("My Radio") are architected for seamless future extension.
 */
enum class RadioType {
    SONG,
    ARTIST,
    PERSONAL
}
