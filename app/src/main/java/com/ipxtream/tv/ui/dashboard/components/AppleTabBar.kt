package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.GitHubRelease
import com.ipxtream.tv.ui.dashboard.ContentSection
import com.ipxtream.tv.ui.theme.AppleLens
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.appleGlass

/** Height the Apple tab bar takes at the top of the screen; content below starts here. */
val AppleTabBarHeight = 84.dp

/**
 * Apple design's main navigation (tvOS): a floating glass capsule of section tabs at the top, with
 * Search and Settings as icon tabs and the account avatar at the end. It replaces the side menu and
 * the header. OK on a tab switches section; Down goes into the page ([onEnterContent]).
 * The account menu under the avatar holds the old header actions.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleTabBar(
    activeSection:       ContentSection,
    searchOpen:          Boolean,
    sections:            List<NavSectionItem>,
    onSelect:            (ContentSection) -> Unit,
    onSearch:            () -> Unit,
    onEnterContent:      () -> Unit,
    /** Requesters that name "the menu" for the rest of the dashboard; they sit on the active tab. */
    activeTabModifier:   Modifier,
    onFocusChanged:      (Boolean) -> Unit,
    activeAccount:       AuthCredentials?,
    isCheckingForUpdate: Boolean,
    isCachingAll:        Boolean,
    updateRelease:       GitHubRelease?,
    onCheckForUpdates:   () -> Unit,
    onRefresh:           () -> Unit,
    onCacheAll:          () -> Unit,
    onSwitchAccount:     () -> Unit,
    onLogout:            () -> Unit,
    modifier:            Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    // "The bar has focus" from its own controls: the box's onFocusChanged also fired at launch with nothing in it focused.
    val focusedKeys = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val barFocused = focusedKeys.values.any { it }
    LaunchedEffect(barFocused) { onFocusChanged(barFocused) }
    // A control that leaves the bar while focused never reports "unfocused": drop its key, and tell the
    // dashboard when the whole bar goes away.
    LaunchedEffect(sections, menuOpen) {
        val live = sections.map { it.section.name } + listOf("search", "settings", "avatar") +
            (if (menuOpen) List(5) { "menu$it" } else emptyList())
        focusedKeys.keys.retainAll(live.toSet())
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onFocusChanged(false) } }
    // The dashboard's "menu" requesters must always sit on a composed tab: the active one, or the first
    // tab when the active section has none (a section hidden in Settings).
    val tabs = sections.filter { it.section != ContentSection.SETTINGS }
    val activeHasTab = searchOpen || activeSection == ContentSection.SETTINGS || tabs.any { it.section == activeSection }
    val track: (String) -> Modifier = { key -> Modifier.onFocusChanged { focusedKeys[key] = it.isFocused } }
    val menuRows = remember { List(5) { FocusRequester() } }
    var menuIndex by remember { mutableStateOf(0) }
    val avatarFocus = remember { FocusRequester() }
    LaunchedEffect(menuOpen) { if (menuOpen) runCatching { menuRows[0].requestFocus() } }

    val downToContent = Modifier.onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && !menuOpen) {
            onEnterContent(); true
        } else false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            
            .padding(horizontal = IpxSpacing.Gutter, vertical = 16.dp)
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { BrandGlyph(size = 34.dp) }

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp)
                .appleGlass(RoundedCornerShape(50))
                // TV scroll containers take focus themselves (nothing lit); only the tabs may.
                .focusProperties { canFocus = false }
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, item ->
                val active = item.section == activeSection && !searchOpen
                val holdsMenuFocus = active || (!activeHasTab && index == 0)
                AppleTab(
                    label    = stringResource(item.section.titleRes),
                    selected = active,
                    onClick  = { onSelect(item.section) },
                    modifier = downToContent.then(track(item.section.name)).then(if (holdsMenuFocus) activeTabModifier else Modifier)
                )
            }
            AppleTab(
                icon     = Icons.Rounded.Search,
                label    = stringResource(R.string.search),
                selected = searchOpen,
                onClick  = onSearch,
                modifier = downToContent.then(track("search")).then(if (searchOpen) activeTabModifier else Modifier)
            )
            val settingsActive = activeSection == ContentSection.SETTINGS && !searchOpen
            AppleTab(
                icon     = Icons.Rounded.Settings,
                label    = stringResource(R.string.section_settings),
                selected = settingsActive,
                onClick  = { onSelect(ContentSection.SETTINGS) },
                modifier = downToContent.then(track("settings")).then(if (settingsActive) activeTabModifier else Modifier)
            )
        }

        // Account avatar; OK opens the account menu below it.
        val username = activeAccount?.username ?: stringResource(R.string.account_guest)
        Surface(
            onClick  = { menuOpen = !menuOpen },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
                .focusRequester(avatarFocus)
                .then(track("avatar"))
                .then(downToContent),
            shape    = ClickableSurfaceDefaults.shape(CircleShape),
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = com.ipxtream.tv.ui.theme.AccentOrchid,
                contentColor          = androidx.compose.ui.graphics.Color.White,
                focusedContainerColor = ChipFocused,
                focusedContentColor   = TextOnFocus
            ),
            border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text  = username.take(2).uppercase(),
                    style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Inside the bar's box so focus moving into it doesn't count as leaving the bar.
        if (menuOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // Drops below the avatar without taking layout space, so the bar keeps its height.
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout(p.width, 0) { p.place(0, 60.dp.roundToPx()) }
                    }
                    .width(340.dp)
                    .appleGlass(RoundedCornerShape(28.dp))
                    .padding(10.dp)
                    // Keys stay inside the menu: Up/Down step through the rows, Up from the top or Back closes it.
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val closeMenu = {
                            menuOpen = false
                            runCatching { avatarFocus.requestFocus() }
                            true
                        }
                        when (e.key) {
                            Key.Back -> closeMenu()
                            Key.DirectionUp ->
                                if (menuIndex == 0) closeMenu()
                                else { runCatching { menuRows[menuIndex - 1].requestFocus() }; true }
                            Key.DirectionDown -> {
                                if (menuIndex < menuRows.lastIndex) runCatching { menuRows[menuIndex + 1].requestFocus() }
                                true
                            }
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UserProfileCard(
                    activeAccount       = activeAccount,
                    isCheckingForUpdate = isCheckingForUpdate,
                    updateRelease       = updateRelease,
                    onCheckForUpdates   = onCheckForUpdates,
                    modifier            = Modifier.fillMaxWidth().focusRequester(menuRows[0]).onFocusChanged { if (it.isFocused) menuIndex = 0 }.then(track("menu0"))
                )
                val close: (() -> Unit) -> () -> Unit = { action -> { menuOpen = false; runCatching { avatarFocus.requestFocus() }; action() } }
                val row: (Int) -> Modifier = { i -> Modifier.focusRequester(menuRows[i]).onFocusChanged { if (it.isFocused) menuIndex = i }.then(track("menu$i")) }
                AppleMenuRow(Icons.Rounded.Refresh, stringResource(R.string.action_refresh_server), close(onRefresh), modifier = row(1))
                AppleMenuRow(Icons.Rounded.Cloud, stringResource(R.string.action_cache_all), close(onCacheAll), highlighted = isCachingAll, modifier = row(2))
                AppleMenuRow(Icons.Rounded.People, stringResource(R.string.action_switch_account), close(onSwitchAccount), modifier = row(3))
                AppleMenuRow(Icons.AutoMirrored.Rounded.ExitToApp, stringResource(R.string.action_logout), close(onLogout), modifier = row(4))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleTab(
    label:    String,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
    icon:     ImageVector? = null
) {
    Surface(
        onClick  = onClick,
        modifier = modifier.height(48.dp),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (selected) AppleLens else androidx.compose.ui.graphics.Color.Transparent,
            contentColor          = if (selected) TextPrimary else TextSecondary,
            focusedContainerColor = ChipFocused,
            focusedContentColor   = TextOnFocus
        ),
        border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
    ) {
        Box(Modifier.padding(horizontal = if (icon != null) 14.dp else 20.dp).height(48.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text  = label,
                    style = IpxTypography.BodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleMenuRow(icon: ImageVector, label: String, onClick: () -> Unit, highlighted: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        onClick  = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = androidx.compose.ui.graphics.Color.Transparent,
            contentColor          = if (highlighted) com.ipxtream.tv.ui.theme.Accent else TextPrimary,
            focusedContainerColor = ChipFocused,
            focusedContentColor   = TextOnFocus
        ),
        border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(Modifier.padding(horizontal = 16.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Box(Modifier.width(14.dp))
            Text(label, style = IpxTypography.BodyLarge)
        }
    }
}
