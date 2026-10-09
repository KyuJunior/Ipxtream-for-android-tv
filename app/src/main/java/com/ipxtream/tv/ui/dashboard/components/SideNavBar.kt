package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.dashboard.ContentSection
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateNav
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

/**
 * Data representation of a section item in the side navigation drawer.
 */
data class NavSectionItem(
    val section: ContentSection,
    val icon:    ImageVector
)

private val NAV_SECTIONS = listOf(
    NavSectionItem(ContentSection.HOME,       Icons.Rounded.Home),
    NavSectionItem(ContentSection.LIVE,       Icons.Rounded.Tv),
    NavSectionItem(ContentSection.VOD,        Icons.Rounded.Movie),
    NavSectionItem(ContentSection.SERIES,     Icons.Rounded.Slideshow),
    NavSectionItem(ContentSection.MY_LIBRARY, Icons.Rounded.Favorite),
    NavSectionItem(ContentSection.WHATS_NEW,  Icons.Rounded.AutoAwesome),
    NavSectionItem(ContentSection.DOWNLOADS,  Icons.Rounded.ArrowDownward),
    NavSectionItem(ContentSection.SETTINGS,   Icons.Rounded.Settings)
)

/**
 * Left-side vertical navigation bar for Android TV.
 *
 * Expands smoothly on D-Pad focus (72.dp -> 240.dp) to reveal section labels.
 * Pressing D-Pad Right from any item seamlessly transfers focus into the active screen.
 */
@Composable
fun SideNavBar(
    activeSection:         ContentSection,
    onSectionSelected:     (ContentSection) -> Unit,
    onNavigateToContent:   () -> Unit,
    onRefresh:             () -> Unit,
    sideNavFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged:        (Boolean) -> Unit = {},
    settings:              com.ipxtream.tv.data.local.AppSettings? = null,
    modifier:              Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue   = if (isExpanded) 240.dp else 72.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label         = "navWidth"
    )

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(SlateNav)
            .onFocusChanged { state ->
                isExpanded = state.hasFocus
                onFocusChanged(state.hasFocus)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
            horizontalAlignment = Alignment.Start
        ) {
            // ── App Brand / Header ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isExpanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("IP", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Text(
                            text       = "IPXtream",
                            style      = IpxTypography.TitleLarge,
                            color      = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 19.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AccentCyan)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Dynamic Content Sections (Configured via Settings) ─────────────
            val visibleSections = remember(settings) {
                NAV_SECTIONS.filter { item ->
                    when (item.section) {
                        ContentSection.WHATS_NEW  -> settings?.showWhatsNewSection ?: true
                        ContentSection.DOWNLOADS  -> settings?.showDownloadsSection ?: true
                        ContentSection.MY_LIBRARY -> settings?.showLibrarySection ?: true
                        else                      -> true
                    }
                }
            }

            visibleSections.forEach { item ->
                val isActive = item.section == activeSection

                NavItem(
                    title               = item.section.displayName,
                    icon                = item.icon,
                    isExpanded          = isExpanded,
                    isActive            = isActive,
                    onSelected          = { onSectionSelected(item.section) },
                    onNavigateToContent = onNavigateToContent,
                    modifier            = if (isActive) Modifier.focusRequester(sideNavFocusRequester) else Modifier
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * A single interactive section item in the sidebar.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavItem(
    title:               String,
    icon:                ImageVector,
    isExpanded:          Boolean,
    isActive:            Boolean,
    onSelected:          () -> Unit,
    onNavigateToContent: () -> Unit,
    modifier:            Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue   = if (isFocused) 1.04f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label         = "navItemScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isFocused || isActive || isExpanded) 1f else 0.65f,
        label       = "navAlpha"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isActive  -> AccentCyan
            else      -> TextSecondary
        },
        label = "navItemText"
    )

    val containerColor = when {
        isFocused -> Color.White.copy(alpha = 0.18f)
        isActive  -> AccentCyan.copy(alpha = 0.12f)
        else      -> Color.Transparent
    }

    val navItemShape = RoundedCornerShape(10.dp)

    Surface(
        onClick  = onSelected,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = contentAlpha }
            .onFocusChanged { state -> isFocused = state.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            onNavigateToContent()
                            true
                        }
                        Key.Back -> {
                            onNavigateToContent()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .semantics { role = Role.Tab },
        colors = ClickableSurfaceDefaults.colors(
            containerColor        = containerColor,
            focusedContainerColor = Color.White.copy(alpha = 0.18f)
        ),
        border = ClickableSurfaceDefaults.border(
            border        = Border(BorderStroke(0.dp, Color.Transparent), shape = navItemShape),
            focusedBorder = Border(BorderStroke(1.5.dp, AccentCyan.copy(alpha = 0.85f)), shape = navItemShape)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = navItemShape)
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // Left accent indicator pill
            if (isFocused || isActive) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentCyan)
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Spacer(Modifier.width(14.dp))
            }

            // Section Icon
            Icon(
                imageVector        = icon,
                contentDescription = title,
                tint               = when {
                    isFocused -> Color.White
                    isActive  -> AccentCyan
                    else      -> TextSecondary
                },
                modifier = Modifier.size(24.dp)
            )

            // Section Title (Visible when expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Text(
                    text       = title,
                    style      = IpxTypography.TitleMedium,
                    color      = textColor,
                    fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
                    fontSize   = 15.sp,
                    maxLines   = 1,
                    modifier   = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
