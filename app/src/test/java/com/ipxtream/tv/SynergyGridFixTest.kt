package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.SynergyBeatKind
import com.ipxtream.tv.ui.dashboard.components.SynergyCell
import com.ipxtream.tv.ui.dashboard.components.SynergyRules
import com.ipxtream.tv.ui.dashboard.components.SynergySymbol
import org.junit.Assert.assertEquals
import org.junit.Test

class SynergyGridFixTest {
    private fun grid(vararg cells: Pair<Int, SynergySymbol>): List<SynergyCell?> {
        val list = MutableList<SynergyCell?>(16) { null }
        for ((index, symbol) in cells) list[index] = SynergyCell(symbol)
        return list
    }

    private val mixed = grid(
        0 to SynergySymbol.FURNACE,
        1 to SynergySymbol.METAL,
        5 to SynergySymbol.CRYSTAL,
        6 to SynergySymbol.MAGNET,
        7 to SynergySymbol.SPARK,
        10 to SynergySymbol.SEED,
        11 to SynergySymbol.SPARK
    )

    @Test fun beatsAddUpToWhatTheRunScores() {
        val play = SynergyRules.play(mixed)
        assertEquals(play.total, play.beats.sumOf { it.gain })
        assertEquals(play.total, SynergyRules.run(mixed).first)
    }

    @Test fun beatsPlayBurnsThenValuesThenMagnets() {
        val kinds = SynergyRules.play(mixed).beats.map { it.kind }
        assertEquals(kinds.sortedBy { it.ordinal }, kinds)
        assertEquals(SynergyBeatKind.BURN, kinds.first())
        assertEquals(SynergyBeatKind.MAGNET, kinds.last())
    }

    @Test fun aMagnetLinksOnlySquaresThatEarnSomething() {
        val play = SynergyRules.play(grid(0 to SynergySymbol.MAGNET, 1 to SynergySymbol.CRYSTAL, 4 to SynergySymbol.MAGNET))
        val magnet = play.beats.single { it.kind == SynergyBeatKind.MAGNET }
        assertEquals(0, magnet.cell)
        assertEquals(listOf(1), magnet.links)
        assertEquals(6L, magnet.gain)
    }
}
