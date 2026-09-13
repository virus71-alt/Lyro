package com.lyro.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Song

@Composable
fun CassetteArtwork(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    cassetteColor: Color = LyroSurfaceElevated,
    labelColor: Color = LyroSurfaceHighlight
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
    val cassetteShape = RoundedCornerShape(16.dp)

    // Main Cassette Body (Minimal Dark Deck)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cassetteShape)
            .background(cassetteColor)
            .border(1.dp, LyroDivider, cassetteShape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Cassette Header: Brand & Side A
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LYRO AUDIO",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
                color = LyroTextSecondary
            )

            Box(
                modifier = Modifier
                    .background(LyroAccentMuted, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SIDE A",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    color = LyroAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Middle Label / Song Info Card
        val labelShape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(labelShape)
                .background(labelColor)
                .border(1.dp, LyroDivider, labelShape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (song != null) {
                    SongArtworkThumbnail(
                        song = song,
                        size = 44.dp,
                        highRes = true,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = if (song != null) Alignment.Start else Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song?.title ?: "No Track Playing",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song?.artist ?: "Lyro Hi-Fi Audio",
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LyroTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Cassette Window with Rotating Dual Spools
        val windowShape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(windowShape)
                .background(LyroSurface)
                .border(1.dp, LyroDivider, windowShape)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            // Tape bridge line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(6.dp)
                    .background(LyroDivider)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left spool
                CassetteSpool(rotation = currentRotation)

                // Center tape status indicator
                Box(
                    modifier = Modifier
                        .background(LyroSurfaceElevated, RoundedCornerShape(4.dp))
                        .border(1.dp, LyroDivider, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isPlaying) "PLAY" else "PAUSE",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isPlaying) LyroAccent else LyroTextMuted
                    )
                }

                // Right spool
                CassetteSpool(rotation = currentRotation)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Tape Guide & Metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(LyroTextMuted.copy(alpha = 0.4f), CircleShape)
            )

            Text(
                text = "HI-FI STEREO • 90 MIN",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                fontFamily = FontFamily.Monospace,
                color = LyroTextMuted
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(LyroTextMuted.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
}

@Composable
fun CassetteSpool(rotation: Float) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .rotate(rotation)
            .background(LyroSurfaceElevated, CircleShape)
            .border(1.dp, LyroDivider, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = size.width / 3,
                style = Stroke(width = 2.dp.toPx())
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
                    color = Color.White.copy(alpha = 0.5f),
                    start = start,
                    end = end,
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}

/**
 * High-performance thumbnail with LruCache and zero-jank background decoding.
 * Selects highest resolution artwork for player screens (highRes = true or size > 64.dp)
 * and lightweight thumbnails for song lists.
 */
@Composable
fun SongArtworkThumbnail(
    song: Song,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    highRes: Boolean = size > 64.dp
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(song.id, highRes) {
        mutableStateOf(com.lyro.app.core.artwork.ArtworkCache.get(song.id, highRes = highRes))
    }

    val rawArtUrl = song.albumArtUriString?.takeIf { it.isNotBlank() }
        ?: song.contentUriString.takeIf { it.startsWith("http") }

    val artUrl = remember(rawArtUrl, highRes) {
        if (highRes && !rawArtUrl.isNullOrBlank()) {
            com.lyro.app.core.artwork.ArtworkUtils.getHighResArtworkUrl(rawArtUrl) ?: rawArtUrl
        } else {
            rawArtUrl
        }
    }

    val fallbackUrl = remember(rawArtUrl, highRes) {
        if (highRes && !rawArtUrl.isNullOrBlank()) {
            com.lyro.app.core.artwork.ArtworkUtils.getFallbackArtworkUrl(rawArtUrl)
        } else {
            null
        }
    }

    var isImageError by remember(song.id, artUrl) { mutableStateOf(false) }

    LaunchedEffect(song.id, artUrl, isImageError, highRes) {
        if ((artUrl.isNullOrBlank() || isImageError) && bitmap == null && !com.lyro.app.core.artwork.ArtworkCache.hasAttempted(song.id, highRes = highRes)) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.lyro.app.core.artwork.ArtworkCache.loadArtwork(context, song.id, song.contentUri, highRes = highRes)
            }
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    val currentBitmap = bitmap
    val thumbShape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(thumbShape)
            .background(LyroSurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!artUrl.isNullOrBlank() && !isImageError) {
            val imageRequest = remember(artUrl, fallbackUrl, context) {
                coil.request.ImageRequest.Builder(context)
                    .data(artUrl)
                    .crossfade(true)
                    .apply {
                        if (fallbackUrl != null && fallbackUrl != artUrl) {
                            error(fallbackUrl)
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = imageRequest,
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
            LyroFallbackArtwork(song = song, size = size)
        }
    }
}

@Composable
fun LyroFallbackArtwork(
    song: Song,
    size: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LyroSurfaceHighlight),
        contentAlignment = Alignment.Center
    ) {
        val initial = song.title.firstOrNull { it.isLetterOrDigit() }?.toString()?.uppercase()
        if (initial != null) {
            Text(
                text = initial,
                fontSize = (size.value * 0.35f).sp,
                fontWeight = FontWeight.SemiBold,
                color = LyroTextSecondary
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = LyroTextMuted,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}
