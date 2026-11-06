package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.Hairline
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SurfaceElevated
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.premiumFocus

/** The grid of games. Every card stays composed, so the shared focus requester never detaches. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun GameMenu(
    games: List<GameId>,
    lastPlayed: GameId?,
    firstItemFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    headerFocusRequester: FocusRequester,
    onOpen: (GameId) -> Unit,
    modifier: Modifier = Modifier
) {
    val target = lastPlayed ?: games.first()
    BoxWithConstraints(modifier) {
        val columns = (maxWidth / 230.dp).toInt().coerceIn(2, 4)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = IpxSpacing.Gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            games.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEachIndexed { columnIndex, game ->
                        val focus = if (game == target) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                        GameCard(
                            game = game,
                            onClick = { onOpen(game) },
                            modifier = Modifier
                                .weight(1f)
                                .then(focus)
                                .focusProperties {
                                    if (columnIndex == 0) start = sideNavFocusRequester
                                    if (rowIndex == 0) up = headerFocusRequester
                                }
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(game: GameId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .premiumFocus(focused, focusedScale = 1.05f, shape = shape)
            .onFocusChanged { focused = it.isFocused }
            .aspectRatio(16f / 10f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceElevated,
            focusedContainerColor = SurfaceElevated
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(1.dp, GlassStroke), shape = shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = shape)
    ) {
        Box(Modifier.fillMaxSize()) {
            GameThumbnail(game, Modifier.fillMaxSize())
            Text(
                text = stringResource(game.title),
                style = IpxTypography.BodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)
            )
        }
    }
}

/** Painted key art where a game has it, else the thumbnail drawn in code. */
@Composable
internal fun GameThumbnail(game: GameId, modifier: Modifier = Modifier) {
    val art = when (game) {
        GameId.DRIFT -> R.drawable.game_art_drift
        GameId.SLING -> R.drawable.game_art_sling
        GameId.NOVA -> R.drawable.game_art_nova
        else -> null
    }
    if (art != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(art),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Canvas(modifier) { drawThumbnail(game) }
    }
}

private val Teal = Color(0xFF2FB8A6)
private val Wood = Color(0xFFB9774A)
private val Grass = Color(0xFF2E7D4F)

/** Each thumbnail is drawn in code from the game's own look, on a 160 by 100 sketch. */
internal fun DrawScope.drawThumbnail(game: GameId) {
    val u = size.height / 100f
    val margin = (size.width - 160f * u) / 2f
    fun p(x: Float, y: Float) = Offset(margin + x * u, y * u)
    fun box(color: Color, x: Float, y: Float, w: Float, h: Float) = drawRect(color, p(x, y), Size(w * u, h * u))
    fun dot(color: Color, x: Float, y: Float, r: Float) = drawCircle(color, r * u, p(x, y))

    when (game) {
        GameId.STACK -> {
            drawRect(Color(0xFF2A1830))
            for (i in 0..5) box(blockColor(i * 3), 50f + i * 3f, 72f - i * 11f, 60f - i * 5f, 10f)
            box(blockColor(19).copy(alpha = 0.9f), 92f, 6f, 34f, 10f)
        }
        GameId.PENALTY -> {
            drawRect(Grass)
            val post = Stroke(2.5f * u)
            drawRect(Color.White, p(35f, 12f), Size(90f * u, 46f * u), style = post)
            for (i in 1..5) drawLine(Color.White.copy(alpha = 0.3f), p(35f + i * 15f, 12f), p(35f + i * 15f, 58f), 0.8f * u)
            for (i in 1..2) drawLine(Color.White.copy(alpha = 0.3f), p(35f, 12f + i * 15f), p(125f, 12f + i * 15f), 0.8f * u)
            dot(ChipFocused, 108f, 28f, 4f)
            dot(Accent, 80f, 40f, 5f)
            box(Accent, 76f, 44f, 8f, 14f)
            dot(Color.White, 80f, 72f, 6f)
        }
        GameId.DRIFT -> {
            drawRect(Color(0xFF0C2A3A))
            val round = CornerRadius(5f * u)
            drawRoundRect(Teal, p(104f, -6f), Size(22f * u, 42f * u), round)
            drawRoundRect(Teal, p(104f, 72f), Size(22f * u, 40f * u), round)
            drawRoundRect(Teal, p(16f, -6f), Size(22f * u, 26f * u), round)
            drawRoundRect(Teal, p(16f, 56f), Size(22f * u, 50f * u), round)
            for (i in -1..1) drawLine(AccentOrchid, p(68f + i * 6f, 48f), p(66f + i * 7f, 66f), 1.5f * u)
            drawArc(ChipFocused, 180f, 180f, true, p(56f, 34f), Size(24f * u, 28f * u))
        }
        GameId.SLING -> {
            drawRect(Color(0xFF17132B))
            box(Color.White.copy(alpha = 0.12f), 0f, 86f, 160f, 14f)
            drawLine(Wood, p(32f, 86f), p(32f, 62f), 3f * u)
            for (i in 1..6) dot(Color.White.copy(alpha = 0.7f - i * 0.08f), 32f + i * 13f, 58f - i * 9f + i * i * 1.4f, 1.3f)
            dot(Accent, 32f, 60f, 6f)
            dot(ChipFocused, 32f, 60f, 3f)
            box(Wood, 106f, 62f, 5f, 24f)
            box(Wood, 134f, 62f, 5f, 24f)
            box(Wood, 102f, 56f, 42f, 6f)
            dot(AccentOrchid, 122f, 76f, 9f)
            dot(AccentOrchid, 122f, 46f, 9f)
            dot(Color.White, 121f, 45f, 3.5f)
            dot(Color.Black, 120f, 45f, 1.7f)
        }
        GameId.NOVA -> {
            drawRect(Color(0xFF120A24))
            dot(Accent.copy(alpha = 0.4f), 78f, 48f, 28f)
            dot(Accent.copy(alpha = 0.3f), 114f, 34f, 16f)
            dot(Accent.copy(alpha = 0.25f), 46f, 66f, 12f)
            listOf(78f to 48f, 114f to 34f, 46f to 66f, 136f to 66f, 22f to 26f, 100f to 76f, 60f to 16f)
                .forEach { (x, y) -> dot(ChipFocused, x, y, 2.6f) }
            dot(AccentOrchid, 132f, 18f, 3.4f)
        }
        GameId.SLICER -> {
            drawRect(Color(0xFF0D0811))
            box(Color.White.copy(alpha = 0.14f), 100f, 0f, 60f, 100f)
            box(Color.White.copy(alpha = 0.14f), 0f, 0f, 100f, 28f)
            drawLine(Accent, p(100f, 0f), p(100f, 100f), 2.5f * u)
            drawLine(Accent, p(0f, 28f), p(100f, 28f), 2.5f * u)
            dot(ChipFocused, 40f, 56f, 6f)
            dot(AccentOrchid, 72f, 72f, 6f)
        }
        GameId.WRECK -> {
            drawRect(Color(0xFF1A1020))
            drawLine(Color.White.copy(alpha = 0.5f), p(58f, 62f), p(108f, 34f), 1.2f * u)
            dot(Accent, 108f, 34f, 14f)
            dot(ChipFocused, 58f, 62f, 5f)
            listOf(136f to 22f, 142f to 56f, 30f to 24f, 24f to 74f, 96f to 74f)
                .forEach { (x, y) -> dot(AccentOrchid, x, y, 5f) }
        }
        GameId.BOUNCER -> {
            drawRect(Color(0xFF1B1830))
            drawRoundRect(ChipFocused, p(58f, 18f), Size(44f * u, 56f * u), CornerRadius(5f * u))
            dot(Color(0xFFE5484D), 80f, 38f, 12f)
            drawPath(
                Path().apply {
                    moveTo(p(80f, 52f).x, p(80f, 52f).y)
                    lineTo(p(92f, 68f).x, p(92f, 68f).y)
                    lineTo(p(68f, 68f).x, p(68f, 68f).y)
                    close()
                },
                Color(0xFF3B82F6)
            )
            drawLine(Color(0xFFE5484D), p(38f, 46f), p(22f, 46f), 3f * u)
            drawLine(Teal, p(122f, 46f), p(138f, 46f), 3f * u)
        }
        GameId.SYNERGY -> {
            drawRect(Color(0xFF141022))
            val colors = listOf(Accent, AccentOrchid, Teal, ChipFocused, Color.White.copy(alpha = 0.08f))
            for (row in 0..3) for (column in 0..3) {
                val color = colors[(row * 3 + column * 2 + row * column) % colors.size]
                box(color, 36f + column * 23f, 4f + row * 19f, 20f, 16f)
            }
        }
    }
}
