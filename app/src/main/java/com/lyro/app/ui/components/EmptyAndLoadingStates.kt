package com.lyro.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*

/**
 * Neo-Brutalist Empty State with iconic visual styling, customizable tip items,
 * and primary & secondary tactile action buttons.
 */
@Composable
fun NeoEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconBackgroundColor: Color = NeoCyberYellow,
    cardBackgroundColor: Color = NeoWhite,
    primaryButtonText: String? = null,
    primaryButtonColor: Color = NeoAcidGreen,
    onPrimaryButtonClick: (() -> Unit)? = null,
    secondaryButtonText: String? = null,
    secondaryButtonColor: Color = NeoCyan,
    onSecondaryButtonClick: (() -> Unit)? = null,
    tips: List<String> = emptyList()
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        NeoCard(
            backgroundColor = cardBackgroundColor,
            shadowOffset = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header Box
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(iconBackgroundColor, RoundedCornerShape(12.dp))
                        .border(2.5.dp, NeoBlack, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NeoBlack,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = title.uppercase(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = NeoBlack,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Description
                Text(
                    text = description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = NeoBlack.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Optional Tips Box
                if (tips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeoGrayLight, RoundedCornerShape(8.dp))
                            .border(1.5.dp, NeoBlack, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tips.forEach { tip ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(6.dp)
                                        .background(NeoBlack, CircleShape)
                                )
                                Text(
                                    text = tip,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeoBlack,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                if (primaryButtonText != null && onPrimaryButtonClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NeoButton(
                        onClick = onPrimaryButtonClick,
                        backgroundColor = primaryButtonColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = primaryButtonText.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = if (primaryButtonColor == NeoHotPink) NeoWhite else NeoBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (secondaryButtonText != null && onSecondaryButtonClick != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NeoButton(
                        onClick = onSecondaryButtonClick,
                        backgroundColor = secondaryButtonColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = secondaryButtonText.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = NeoBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Neo-Brutalist Loading State with animated spinning reels and progress indicator.
 */
@Composable
fun NeoLoadingState(
    title: String = "SCANNING STORAGE...",
    subtitle: String = "Searching for audio tracks on your device...",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reel_angle"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        NeoCard(
            backgroundColor = NeoWhite,
            shadowOffset = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cassette Tape / Reel animation preview
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(NeoCyberYellow, RoundedCornerShape(12.dp))
                        .border(2.5.dp, NeoBlack, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .rotate(angle)
                            .background(NeoWhite, CircleShape)
                            .border(2.dp, NeoBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Spoke cross in reel
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(NeoBlack)
                        )
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(NeoBlack)
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(NeoCyberYellow, CircleShape)
                                .border(1.5.dp, NeoBlack, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.5.dp,
                        color = NeoBlack
                    )
                    Text(
                        text = title.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = NeoBlack,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = NeoBlack.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
