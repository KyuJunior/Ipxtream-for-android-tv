package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.dashboard.ContentSection
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentCyanDim
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.SlateNav
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

/**
 * Left-side vertical navigation bar for switching between content sections.
 *
 * ### D-Pad behaviour
 * - DPAD_UP / DPAD_DOWN navigate between section items within the sidebar.
 * - DPAD_RIGHT from this component naturally moves focus to the CategoryRow
 *   (because it is the geometrically nearest focusable to the right).
 *
 * ### Focus appearance
 * Each [NavItem] uses **manual focus handling** (not TV Material Card) to
 * demonstrate the alternative pattern with a left-edge accent bar indicator,
 * which is a common TV sidebar motif (Netflix, Disney+, etc.).
 *
 * @param activeSection       Currently active section.
 * @param onSectionSelected   Callback when the user selects a section.
 */
@Composable
fun SideNavBar(
    activeSection:     ContentSection,
    onSectionSelected: (ContentSection) -> Unit,
    onRefresh:         () -> Unit,
    modifier:          Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (isExpanded) 240.dp else 66.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "navWidth"
    )

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(SlateNav)    // Uses the new pitch-black alpha
            .onFocusChanged { state -> isExpanded = state.hasFocus }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp)
                .selectableGroup(),
            verticalArrangement   = Arrangement.spacedBy(8.dp, Alignment.Top),
            horizontalAlignment   = Alignment.Start
        ) {
            // ── App Logo ──────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isExpanded) {
                    Text(
                        text       = "IPXtream",
                        style      = IpxTypography.HeadlineMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 20.sp,
                        modifier   = Modifier.padding(start = 28.dp) // align text nicely with expanded icons
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Section items ─────────────────────────────────────────────────────
            // ── Section items ─────────────────────────────────────────────────────
            val navSections = listOf(
                ContentSection.HOME,
                ContentSection.WHATS_NEW,
                ContentSection.MY_LIBRARY,
                ContentSection.DOWNLOADS
            )

            navSections.forEach { section ->
                val icon = when(section) {
                    ContentSection.HOME -> Icons.Rounded.Home
                    ContentSection.WHATS_NEW -> Icons.Rounded.PlayArrow
                    ContentSection.MY_LIBRARY -> Icons.Rounded.Favorite
                    ContentSection.DOWNLOADS -> Icons.Rounded.ArrowDownward
                    else -> Icons.Rounded.Home
                }
                
                val isActive = when (section) {
                    ContentSection.HOME -> activeSection == ContentSection.HOME || 
                                           activeSection == ContentSection.LIVE || 
                                           activeSection == ContentSection.VOD || 
                                           activeSection == ContentSection.SERIES
                    else -> section == activeSection
                }
                
                NavItem(
                    title          = section.displayName,
                    icon           = icon,
                    isExpanded     = isExpanded,
                    isActive       = isActive,
                    onSelected     = { onSectionSelected(section) }
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * A single navigable section item in the sidebar.
 *
 * ### Manual D-Pad focus pattern (for reference)
 * This component uses `onFocusChanged` + `animateFloatAsState` directly,
 * showing how to implement focus visuals without TV Material Card.
 *
 * Focus indicators applied:
 * 1. Scale: 1.0 → 1.05 (subtle, left-nav items should not grow too large)
 * 2. Left accent bar: hidden → 3dp wide cyan bar
 * 3. Background: dark → slightly lighter glass surface
 * 4. Text color: muted → primary white
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavItem(
    title:      String,
    icon:       ImageVector,
    isExpanded: Boolean,
    isActive:   Boolean,
    onSelected: () -> Unit,
    modifier:   Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue   = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label         = "navItemScale"
    )
    
    // Smooth fade logic: if it's inactive AND the sidebar is collapsed AND NOT focused, dim it.
    // BUT if the sidebar is expanding, make everything visible!
    val contentAlpha by animateFloatAsState(
        targetValue = if (isFocused || isActive || isExpanded) 1f else 0.5f,
        label       = "navAlpha"
    )

    val textColor by animateColorAsState(
        targetValue = if (isFocused || isActive) TextPrimary else TextSecondary,
        label       = "navItemText"
    )

    Surface(
        onClick    = onSelected,
        modifier   = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = contentAlpha }
            .onFocusChanged { state -> isFocused = state.isFocused }
            .semantics { role = Role.Tab },
        colors     = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape      = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 20.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isFocused || isActive) Color.White else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
            
            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text       = title,
                    style      = IpxTypography.TitleMedium,
                    color      = textColor,
                    fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Normal,
                    modifier   = Modifier.padding(start = 20.dp)
                )
            }
        }
    }
}
