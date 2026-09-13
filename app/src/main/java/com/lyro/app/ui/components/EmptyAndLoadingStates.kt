package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*

/**
 * Modern minimal dark-first Empty State.
 * Displays an icon, title, description, optional tips, and action buttons.
 */
@Composable
fun LyroEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconBackgroundColor: Color = LyroSurfaceHighlight,
    iconTint: Color = LyroAccent,
    cardBackgroundColor: Color = LyroSurface,
    primaryButtonText: String? = null,
    primaryButtonColor: Color = LyroAccent,
    onPrimaryButtonClick: (() -> Unit)? = null,
    secondaryButtonText: String? = null,
    secondaryButtonColor: Color = LyroSurfaceHighlight,
    onSecondaryButtonClick: (() -> Unit)? = null,
    tips: List<String> = emptyList()
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(16.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackgroundColor, shape)
                .border(1.dp, LyroDivider, shape)
                .clip(shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Header
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(iconBackgroundColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = LyroTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = description,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = LyroTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Optional Tips
            if (tips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LyroSurfaceElevated, RoundedCornerShape(12.dp))
                        .border(1.dp, LyroDivider, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tips.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(5.dp)
                                    .background(LyroAccent, CircleShape)
                            )
                            Text(
                                text = tip,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = LyroTextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Action Buttons
            if (primaryButtonText != null && onPrimaryButtonClick != null) {
                Spacer(modifier = Modifier.height(20.dp))
                LyroButton(
                    onClick = onPrimaryButtonClick,
                    backgroundColor = primaryButtonColor,
                    contentColor = if (primaryButtonColor == LyroAccent) Color.Black else LyroTextPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = primaryButtonText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (secondaryButtonText != null && onSecondaryButtonClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LyroButton(
                    onClick = onSecondaryButtonClick,
                    backgroundColor = secondaryButtonColor,
                    contentColor = LyroTextPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = secondaryButtonText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Modern minimal dark-first Loading State.
 */
@Composable
fun LyroLoadingState(
    title: String = "Scanning Storage...",
    subtitle: String = "Searching for audio tracks on your device...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(16.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LyroSurface, shape)
                .border(1.dp, LyroDivider, shape)
                .clip(shape)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                color = LyroAccent
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = LyroTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = LyroTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
