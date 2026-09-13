package com.lyro.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Song

@Composable
fun CassetteArtwork(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    cassetteColor: Color = NeoCyberYellow,
    labelColor: Color = NeoAcidGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tapeSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val currentRotation = if (isPlaying) rotation else 0f

    // Outer Brutalist Shadow and Frame
    Box(
        modifier = modifier
            .padding(end = 6.dp, bottom = 6.dp)
    ) {
        // Drop shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(NeoBlack, RoundedCornerShape(14.dp))
        )

        // Main Cassette Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cassetteColor, RoundedCornerShape(14.dp))
                .border(3.dp, NeoBlack, RoundedCornerShape(14.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Cassette Header: Brand & Side A
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LYRO TAPE",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    color = NeoBlack
                )

                Box(
                    modifier = Modifier
                        .background(NeoHotPink, RoundedCornerShape(4.dp))
                        .border(1.5.dp, NeoBlack, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SIDE A",
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        color = NeoWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle Label / Song Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(labelColor, RoundedCornerShape(8.dp))
                    .border(2.5.dp, NeoBlack, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song?.title ?: "No Track Playing",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack
                    )
                    Text(
                        text = song?.artist ?: "Lyro Hi-Fi Audio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cassette Window with Rotating Dual Spools
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(NeoGrayLight, RoundedCornerShape(8.dp))
                    .border(2.5.dp, NeoBlack, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Tape bridge line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(8.dp)
                        .background(NeoBlack)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left spool
                    CassetteSpool(rotation = currentRotation)

                    // Center tape window counter
                    Box(
                        modifier = Modifier
                            .background(NeoWhite, RoundedCornerShape(3.dp))
                            .border(1.5.dp, NeoBlack, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "PLAY" else "PAUSE",
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = NeoBlack
                        )
                    }

                    // Right spool
                    CassetteSpool(rotation = currentRotation)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Tape Guide & Screws
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NeoWhite, CircleShape)
                        .border(1.5.dp, NeoBlack, CircleShape)
                )

                Text(
                    text = "HI-FI STEREO • 90 MIN",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = NeoBlack
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NeoWhite, CircleShape)
                        .border(1.5.dp, NeoBlack, CircleShape)
                )
            }
        }
    }
}

@Composable
fun CassetteSpool(rotation: Float) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .rotate(rotation)
            .background(NeoWhite, CircleShape)
            .border(2.dp, NeoBlack, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = NeoBlack,
                radius = size.width / 3,
                style = Stroke(width = 3.dp.toPx())
            )
            for (i in 0 until 6) {
                val angle = (i * 60) * (Math.PI / 180).toFloat()
                val start = Offset(
                    center.x + (size.width / 4) * kotlin.math.cos(angle),
                    center.y + (size.height / 4) * kotlin.math.sin(angle)
                )
                val end = Offset(
                    center.x + (size.width / 2) * kotlin.math.cos(angle),
                    center.y + (size.height / 2) * kotlin.math.sin(angle)
                )
                drawLine(
                    color = NeoBlack,
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

/**
 * High-performance thumbnail with LruCache and zero-jank background decoding
 */
@Composable
fun SongArtworkThumbnail(
    song: Song,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(song.id) { mutableStateOf(com.lyro.app.core.artwork.ArtworkCache.get(song.id)) }
    val accentColor = remember(song.id) {
        val hash = (song.id.hashCode() * 31 + song.title.hashCode())
        val index = kotlin.math.abs(hash) % NeoAccentPalette.size
        NeoAccentPalette[index]
    }

    val artUrl = song.albumArtUriString?.takeIf { it.isNotBlank() }
        ?: song.contentUriString.takeIf { it.startsWith("http") }
    var isImageError by remember(song.id, artUrl) { mutableStateOf(false) }

    LaunchedEffect(song.id, artUrl, isImageError) {
        if ((artUrl.isNullOrBlank() || isImageError) && bitmap == null && !com.lyro.app.core.artwork.ArtworkCache.hasAttempted(song.id)) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.lyro.app.core.artwork.ArtworkCache.loadThumbnail(context, song.id, song.contentUri)
            }
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    val currentBitmap = bitmap

    Box(
        modifier = modifier
            .size(size)
            .background(accentColor, RoundedCornerShape(8.dp))
            .border(2.dp, NeoBlack, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!artUrl.isNullOrBlank() && !isImageError) {
            AsyncImage(
                model = artUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    isImageError = true
                }
            )
        } else if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            BrutalistFallbackArtwork(song = song, accentColor = accentColor, size = size)
        }
    }
}

@Composable
fun BrutalistFallbackArtwork(
    song: Song,
    accentColor: Color,
    size: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(accentColor),
        contentAlignment = Alignment.Center
    ) {
        // Stylized Mini Brutalist Vinyl Record
        Box(
            modifier = Modifier
                .size(size * 0.74f)
                .background(NeoBlack, CircleShape)
                .border(1.5.dp, NeoWhite, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.32f)
                    .background(NeoWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = song.title.firstOrNull { it.isLetterOrDigit() }?.toString()?.uppercase() ?: "♪",
                    fontSize = (size.value * 0.22f).sp,
                    fontWeight = FontWeight.Black,
                    color = NeoBlack
                )
            }
        }
    }
}
