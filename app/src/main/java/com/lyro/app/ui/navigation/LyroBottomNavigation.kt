package com.lyro.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.core.haptics.rememberLyroHaptics

/**
 * Premium minimal bottom navigation bar for Lyro.
 * Inspired by YouTube Music navigation: flat dark background, icon + label,
 * equal width items, subtle scale transitions, and zero neo/brutalist styling.
 */
@Composable
fun LyroBottomNavigation(
    currentDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberLyroHaptics()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(LyroBackground)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainDestination.values().forEach { destination ->
            LyroBottomNavItem(
                destination = destination,
                selected = destination == currentDestination,
                onClick = {
                    if (destination != currentDestination) {
                        haptics.selection()
                        onDestinationSelected(destination)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LyroBottomNavItem(
    destination: MainDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Subtle scale animation for selected icon (0.96f -> 1.0f over ~140ms)
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nav_icon_scale"
    )

    // Smooth color transition
    val iconColor by animateColorAsState(
        targetValue = if (selected) LyroTextPrimary else LyroTextMuted,
        animationSpec = tween(durationMillis = 150),
        label = "nav_icon_color"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) LyroTextPrimary else LyroTextMuted,
        animationSpec = tween(durationMillis = 150),
        label = "nav_text_color"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = 48.dp)
            .semantics {
                this.contentDescription = destination.contentDescription
            }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null, // Semantics defined on the parent Column
            tint = iconColor,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = destination.title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}
