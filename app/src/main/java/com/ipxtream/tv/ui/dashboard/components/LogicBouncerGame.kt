package com.ipxtream.tv.ui.dashboard.components

import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

enum class BouncerShape { CIRCLE, SQUARE, TRIANGLE }
enum class BouncerColor(val color: Color) {
    RED(Color(0xFFFF5D5D)), BLUE(Color(0xFF5B9DFF)), YELLOW(Color(0xFFFFD25A))
}

class BouncerCard(val shape: BouncerShape, var color: BouncerColor, val number: Int, val gold: Boolean, val changesTo: BouncerColor?)

/** Rules are whole sentences, so each reads properly in Arabic; never join them from parts. */
enum class BouncerRule(@StringRes val text: Int, val hard: Boolean, val accepts: (BouncerCard) -> Boolean) {
    ONLY_RED(R.string.game_rule_red, false, { it.color == BouncerColor.RED }),
    ONLY_BLUE(R.string.game_rule_blue, false, { it.color == BouncerColor.BLUE }),
    ONLY_CIRCLES(R.string.game_rule_circles, false, { it.shape == BouncerShape.CIRCLE }),
    ONLY_SQUARES(R.string.game_rule_squares, false, { it.shape == BouncerShape.SQUARE }),
    ONLY_EVEN(R.string.game_rule_even, false, { it.number % 2 == 0 }),
    ONLY_ODD(R.string.game_rule_odd, false, { it.number % 2 == 1 }),
    EVEN_NOT_BLUE_TRIANGLES(R.string.game_rule_even_not_blue_triangles, true, {
        it.number % 2 == 0 && !(it.color == BouncerColor.BLUE && it.shape == BouncerShape.TRIANGLE)
    }),
    RED_OR_CIRCLES(R.string.game_rule_red_or_circles, true, { it.color == BouncerColor.RED || it.shape == BouncerShape.CIRCLE }),
    BIG_NOT_YELLOW(R.string.game_rule_big_not_yellow, true, { it.number > 5 && it.color != BouncerColor.YELLOW }),
    ODD_SQUARES(R.string.game_rule_odd_squares, true, { it.number % 2 == 1 && it.shape == BouncerShape.SQUARE }),
    BLUE_NOT_CIRCLES(R.string.game_rule_blue_not_circles, true, { it.color == BouncerColor.BLUE && it.shape != BouncerShape.CIRCLE }),
    SMALL_OR_TRIANGLES(R.string.game_rule_small_or_triangles, true, { it.number < 5 || it.shape == BouncerShape.TRIANGLE })
}

/** Logic Bouncer: swipe each card right to accept or left to reject before it reaches the gate. */
class BouncerGame(private val random: Random) {
    var rule = BouncerRule.ONLY_RED
        private set
    var card = newCard()
        private set
    var cardAge = 0f
        private set
    var ruleAge = 0f
        private set
    var score = 0
        private set
    var lives = LIVES
        private set
    val over: Boolean get() = lives <= 0
    private var changed = false

    fun travelTime(): Float = max(3.6f - score * 0.05f, 1.5f)

    fun step(dt: Float) {
        if (over) return
        ruleAge += dt
        if (ruleAge >= RULE_TIME) {
            ruleAge = 0f
            rule = nextRule()
        }
        cardAge += dt
        val change = card.changesTo
        if (!changed && change != null && cardAge > travelTime() * 0.6f) {
            card.color = change
            changed = true
        }
        if (cardAge >= travelTime()) settle(right = false)
    }

    /** The player's swipe. Returns whether it was right. */
    fun judge(accept: Boolean): Boolean {
        val right = rule.accepts(card) == accept
        settle(right)
        return right
    }

    private fun settle(right: Boolean) {
        val weight = if (card.gold) 2 else 1
        if (right) score += weight else lives = (lives - weight).coerceAtLeast(0)
        card = newCard()
        cardAge = 0f
        changed = false
    }

    private fun nextRule(): BouncerRule {
        val pool = BouncerRule.entries.filter { it != rule && (score >= HARD_AT || !it.hard) }
        return pool[random.nextInt(pool.size)]
    }

    private fun newCard(): BouncerCard {
        val colors = BouncerColor.entries
        val color = colors[random.nextInt(colors.size)]
        return BouncerCard(
            shape = BouncerShape.entries[random.nextInt(BouncerShape.entries.size)],
            color = color,
            number = 1 + random.nextInt(9),
            gold = score >= 5 && random.nextInt(8) == 0,
            changesTo = if (score >= 8 && random.nextInt(4) == 0) colors[(color.ordinal + 1) % colors.size] else null
        )
    }

    companion object {
        const val LIVES = 3
        const val RULE_TIME = 15f
        const val HARD_AT = 10
    }
}

private fun DrawScope.drawCard(card: BouncerCard, center: Offset, radius: Float, paint: Paint) {
    val c = card.color.color
    when (card.shape) {
        BouncerShape.CIRCLE -> drawCircle(c, radius, center)
        BouncerShape.SQUARE -> drawRect(c, center - Offset(radius, radius), Size(radius * 2f, radius * 2f))
        BouncerShape.TRIANGLE -> drawPath(
            Path().apply {
                moveTo(center.x, center.y - radius * 1.1f)
                lineTo(center.x + radius * 1.15f, center.y + radius)
                lineTo(center.x - radius * 1.15f, center.y + radius)
                close()
            },
            c
        )
    }
    if (card.gold) drawCircle(Color(0xFFFFE6A0), radius * 1.45f, center, style = Stroke(radius * 0.14f))
    drawContext.canvas.nativeCanvas.drawText(card.number.toString(), center.x, center.y + radius * 0.34f, paint)
}

/** Three lives; a gold card counts double either way, and some cards change colour on the way down. */
@Composable
internal fun LogicBouncerScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("bouncer_game")
    val density = LocalDensity.current.density
    var game by remember { mutableStateOf(BouncerGame(Random.Default)) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(BouncerGame.LIVES) }
    var rule by remember { mutableStateOf(game.rule) }
    var frame by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    var downX by remember { mutableStateOf(0f) }
    var fingerX by remember { mutableStateOf(0f) }
    val paint = remember {
        Paint().apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
            textSize = 24f * density
        }
    }

    fun sync() {
        score = game.score
        lives = game.lives
        rule = game.rule
        if (game.over && !over) {
            over = true
            overAt = System.currentTimeMillis()
            best.submit(game.score)
        }
    }

    GameLoop(running = !over) { dt ->
        game.step(dt)
        frame++
        sync()
    }

    fun restart() {
        if (System.currentTimeMillis() - overAt <= 500) return
        game = BouncerGame(Random.Default)
        over = false
        sync()
    }

    Box(
        modifier
            .gameFocus(gameFocusRequester, sideNavFocusRequester, chipFocusRequester)
            .fingerPosition(
                onDown = {
                    if (over) restart()
                    downX = it.x
                    fingerX = it.x
                },
                onMove = { fingerX = it.x },
                onUp = {
                    val swipe = fingerX - downX
                    if (!over && abs(swipe) > 24f * density) {
                        game.judge(accept = swipe > 0f)
                        sync()
                    }
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame  // redraw only, no recomposition
            val gateY = size.height - 26f * density
            drawLine(ChipFocused, Offset(size.width * 0.3f, gateY), Offset(size.width * 0.7f, gateY), 3f * density)
            if (!over) {
                val travel = (game.cardAge / game.travelTime()).coerceIn(0f, 1f)
                val top = 92f * density
                drawCard(game.card, Offset(size.width / 2f, top + (gateY - top - 26f * density) * travel), 24f * density, paint)
            }
        }
        GameScore(
            score.toString(),
            best.value,
            extra = stringResource(R.string.game_bouncer_lives, lives)
        )
        Text(
            stringResource(if (over) R.string.game_bouncer_over else rule.text),
            style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.34f).padding(start = IpxSpacing.Gutter, top = 12.dp)
        )
        // The swipe sides stay physical in Arabic too: right always accepts.
        Text(
            stringResource(R.string.game_bouncer_reject),
            style = IpxTypography.BodyMedium,
            color = TextSecondary,
            modifier = Modifier.align(AbsoluteAlignment.CenterLeft).padding(horizontal = IpxSpacing.Gutter)
        )
        Text(
            stringResource(R.string.game_bouncer_accept),
            style = IpxTypography.BodyMedium,
            color = TextSecondary,
            modifier = Modifier.align(AbsoluteAlignment.CenterRight).padding(horizontal = IpxSpacing.Gutter)
        )
    }
}
