package com.lyro.app.ui.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.service.PlaybackManager

@Composable
fun EqualizerScreen(
    playbackManager: PlaybackManager,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val equalizer = remember { playbackManager.getEqualizer() }
    val bassBoost = remember { playbackManager.getBassBoost() }

    var bassStrength by remember { mutableFloatStateOf(300f) }
    var selectedPresetIndex by remember { mutableIntStateOf(0) }

    // Equalizer Bands (Default to 5 standard bands if hardware supports, or mock defaults)
    val numBands = remember { equalizer?.numberOfBands?.toInt() ?: 5 }
    val bandLevels = remember {
        mutableStateListOf<Float>().apply {
            repeat(numBands) { add(0f) }
        }
    }

    val minEQLevel = remember { equalizer?.bandLevelRange?.get(0)?.toFloat() ?: -1500f }
    val maxEQLevel = remember { equalizer?.bandLevelRange?.get(1)?.toFloat() ?: 1500f }

    val defaultPresets = listOf("Flat", "Rock", "Pop", "Bass Boost", "Electronic", "Jazz")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBgLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeoIconButton(
                onClick = onBackClick,
                backgroundColor = NeoWhite,
                size = 40.dp
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = NeoBlack,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "AUDIO EQUALIZER",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = NeoBlack
            )

            NeoBadge(
                text = "HI-FI FX",
                backgroundColor = NeoAcidGreen,
                textColor = NeoBlack
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Presets Horizontal Row
        Text(
            text = "EQUALIZER PRESETS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = NeoBlack,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            defaultPresets.forEachIndexed { index, presetName ->
                NeoChip(
                    text = presetName.uppercase(),
                    selected = selectedPresetIndex == index,
                    onClick = {
                        selectedPresetIndex = index
                        playbackManager.usePreset(index.toShort())
                    },
                    activeColor = NeoCyberYellow
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bass Boost Card
        NeoCard(
            backgroundColor = NeoWhite,
            shadowOffset = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BASS BOOST LEVEL",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = NeoBlack
                    )
                    NeoBadge(
                        text = "${(bassStrength / 10).toInt()}%",
                        backgroundColor = NeoHotPink,
                        textColor = NeoWhite
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = bassStrength,
                    onValueChange = {
                        bassStrength = it
                        playbackManager.setBassBoostStrength(it.toInt().toShort())
                    },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeoBlack,
                        activeTrackColor = NeoHotPink,
                        inactiveTrackColor = NeoGrayMedium
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Equalizer Frequency Bands
        Text(
            text = "FREQUENCY BANDS (dB)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = NeoBlack,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            backgroundColor = NeoWhite,
            shadowOffset = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val bandFrequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

                for (band in 0 until numBands) {
                    val label = if (band < bandFrequencies.size) bandFrequencies[band] else "Band ${band + 1}"
                    val level = bandLevels.getOrElse(band) { 0f }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NeoBlack
                            )
                            Text(
                                text = String.format("%+.1f dB", level / 100f),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = NeoBlack
                            )
                        }

                        Slider(
                            value = level,
                            onValueChange = { newLevel ->
                                bandLevels[band] = newLevel
                                playbackManager.setBandLevel(band.toShort(), newLevel.toInt().toShort())
                            },
                            valueRange = minEQLevel..maxEQLevel,
                            colors = SliderDefaults.colors(
                                thumbColor = NeoBlack,
                                activeTrackColor = NeoAcidGreen,
                                inactiveTrackColor = NeoGrayMedium
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Reset to Default button
        NeoButton(
            onClick = {
                selectedPresetIndex = 0
                bassStrength = 0f
                playbackManager.setBassBoostStrength(0)
                for (i in 0 until numBands) {
                    bandLevels[i] = 0f
                    playbackManager.setBandLevel(i.toShort(), 0)
                }
            },
            backgroundColor = NeoCyan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "RESET TO FLAT",
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = NeoBlack
            )
        }
    }
}
