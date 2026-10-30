package com.ipxtream.tv.ui.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.min
import kotlin.random.Random

enum class SynergySymbol(
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val value: Long,
    val rare: Boolean,
    val color: Color
) {
    METAL(R.string.game_synergy_metal, R.string.game_synergy_metal_detail, 1, false, Color(0xFF8E97A8)),
    SEED(R.string.game_synergy_seed, R.string.game_synergy_seed_detail, 1, false, Color(0xFF58B67F)),
    SPARK(R.string.game_synergy_spark, R.string.game_synergy_spark_detail, 2, false, Color(0xFFE9B949)),
    MAGNET(R.string.game_synergy_magnet, R.string.game_synergy_magnet_detail, 0, false, Color(0xFF5B9DFF)),
    FURNACE(R.string.game_synergy_furnace, R.string.game_synergy_furnace_detail, 0, true, Color(0xFFE66A3C)),
    CRYSTAL(R.string.game_synergy_crystal, R.string.game_synergy_crystal_detail, 6, true, Color(0xFFB58CFF))
}

class SynergyCell(val symbol: SynergySymbol, val growth: Int = 0)

/** Scoring for Synergy Grid; pure, and always in this order: furnaces, then values, then magnets. */
object SynergyRules {
    const val SIZE = 4
    const val TURNS_PER_STAGE = 5
    const val SCORE_CAP = 1_000_000_000L  // chained magnets double fast, and scores are stored as Int

    private fun neighbours(index: Int): List<Int> {
        val col = index % SIZE
        val row = index / SIZE
        return buildList {
            if (col > 0) add(index - 1)
            if (col < SIZE - 1) add(index + 1)
            if (row > 0) add(index - SIZE)
            if (row < SIZE - 1) add(index + SIZE)
        }
    }

    /** Runs the grid: returns the points and the grid as it stands afterwards. */
    fun run(grid: List<SynergyCell?>): Pair<Long, List<SynergyCell?>> {
        val after = grid.toMutableList()
        var bonus = 0L
        for (i in after.indices) {
            if (after[i]?.symbol != SynergySymbol.FURNACE) continue
            for (n in neighbours(i)) if (after[n]?.symbol == SynergySymbol.METAL) {
                after[n] = null
                bonus += 10
            }
        }
        val sparks = after.count { it?.symbol == SynergySymbol.SPARK }
        val values = LongArray(after.size) { i ->
            val cell = after[i] ?: return@LongArray 0L
            when (cell.symbol) {
                SynergySymbol.SEED -> cell.symbol.value + cell.growth
                SynergySymbol.SPARK -> cell.symbol.value + 2L * (sparks - 1)
                else -> cell.symbol.value
            }
        }
        for (i in after.indices) {
            if (after[i]?.symbol != SynergySymbol.MAGNET) continue
            for (n in neighbours(i)) values[n] = min(values[n] * 2, SCORE_CAP)
        }
        var total = bonus
        for (value in values) total = min(total + value, SCORE_CAP)
        for (i in after.indices) {
            val cell = after[i] ?: continue
            if (cell.symbol == SynergySymbol.SEED) after[i] = SynergyCell(cell.symbol, cell.growth + 1)
        }
        return total to after
    }

    /** Points needed across the five turns of [stage], counted from 1. */
    fun quota(stage: Int): Long = 25L * stage * stage + 15L * stage
}

/** The running game: place the three offered symbols, press Run, reach the quota every five turns. */
class SynergyGame(private val random: Random) {
    var grid: List<SynergyCell?> = List(SynergyRules.SIZE * SynergyRules.SIZE) { null }
        private set
    var hand: List<SynergySymbol> = deal()
        private set
    var turn = 1
        private set
    var stage = 1
        private set
    var stagePoints = 0L
        private set
    var total = 0L
        private set
    var lastRun = 0L
        private set
    var over = false
        private set

    private fun deal(): List<SynergySymbol> = List(3) {
        val rareChance = min(10 + turn * 2, 40)
        val pool = SynergySymbol.entries.filter { it.rare == (random.nextInt(100) < rareChance) }
        pool[random.nextInt(pool.size)]
    }

    /** Puts the next symbol from the hand on a cell, replacing whatever was there. */
    fun place(index: Int) {
        if (over || hand.isEmpty()) return
        grid = grid.toMutableList().also { it[index] = SynergyCell(hand.first()) }
        hand = hand.drop(1)
    }

    fun run() {
        if (over || hand.isNotEmpty()) return
        val (points, after) = SynergyRules.run(grid)
        grid = after
        lastRun = points
        stagePoints += points
        total = min(total + points, SynergyRules.SCORE_CAP)
        if (turn % SynergyRules.TURNS_PER_STAGE == 0) {
            if (stagePoints < SynergyRules.quota(stage)) {
                over = true
                return
            }
            stage++
            stagePoints = 0L
        }
        turn++
        hand = deal()
    }
}

@Composable
private fun SymbolTile(symbol: SynergySymbol?, growth: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(symbol?.color?.copy(alpha = 0.3f) ?: Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .border(1.dp, symbol?.color ?: GlassStroke, RoundedCornerShape(8.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (symbol != null) {
            Text(
                if (growth > 0) stringResource(symbol.title) + " +$growth" else stringResource(symbol.title),
                style = IpxTypography.BodyMedium.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/** No physics, no frame loop: nothing redraws until a tile is tapped. */
@Composable
internal fun SynergyGridScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("synergy_game")
    var game by remember { mutableStateOf(SynergyGame(Random.Default)) }
    var version by remember { mutableIntStateOf(0) }
    version  // the game object changes in place; this recomposes after each move

    fun moved() {
        version++
        if (game.over) best.submit(game.total.toInt())
    }

    Row(
        modifier
            .gameFocus(gameFocusRequester, sideNavFocusRequester, chipFocusRequester)
            .padding(horizontal = IpxSpacing.Gutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (row in 0 until SynergyRules.SIZE) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until SynergyRules.SIZE) {
                        val index = row * SynergyRules.SIZE + col
                        val cell = game.grid[index]
                        SymbolTile(
                            cell?.symbol,
                            cell?.growth ?: 0,
                            Modifier.weight(1f).fillMaxSize().clickable {
                                game.place(index)
                                moved()
                            }
                        )
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.game_synergy_total, game.total),
                style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(stringResource(R.string.game_best, best.value), style = IpxTypography.BodyMedium, color = TextSecondary)
            Text(
                stringResource(
                    R.string.game_synergy_quota,
                    game.stagePoints,
                    SynergyRules.quota(game.stage),
                    SynergyRules.TURNS_PER_STAGE - (game.turn - 1) % SynergyRules.TURNS_PER_STAGE
                ),
                style = IpxTypography.BodyMedium,
                color = Accent
            )
            val next = game.hand.firstOrNull()
            when {
                game.over -> Text(stringResource(R.string.game_synergy_over), style = IpxTypography.BodyMedium, color = TextPrimary)
                next != null -> {
                    Text(stringResource(R.string.game_synergy_place), style = IpxTypography.BodyMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        game.hand.forEach { SymbolTile(it, 0, Modifier.weight(1f).aspectRatio(1.4f)) }
                        repeat(3 - game.hand.size) { Box(Modifier.weight(1f)) }
                    }
                    Text(stringResource(next.detail), style = IpxTypography.BodyMedium, color = TextSecondary)
                }
                else -> Text(stringResource(R.string.game_synergy_last, game.lastRun), style = IpxTypography.BodyMedium, color = TextSecondary)
            }
            val ready = game.over || game.hand.isEmpty()
            Box(
                Modifier
                    .background(if (ready) ChipFocused else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .clickable(enabled = ready) {
                        if (game.over) game = SynergyGame(Random.Default) else game.run()
                        moved()
                    }
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(if (game.over) R.string.game_synergy_again else R.string.game_synergy_run),
                    style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (ready) TextOnFocus else TextSecondary
                )
            }
        }
    }
}
