/*
 * GravityFluxEngineTest.kt
 *
 * Pure-JVM JUnit4 tests for the engine. No android.* dependencies.
 *
 * IMPORTANT: the engine rotates gravity BEFORE sliding, so the very first
 * rotateAndSlide() always slides toward the LEFT (DOWN -> LEFT). Test chambers
 * below are designed with this in mind.
 *
 * Legend for chamber strings:
 *   '.' empty, '#' wall, 'o' node, 'X' exit, '*' hazard, 'S' start
 */

package com.iamconanpeter.gravityflux.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GravityFluxEngineTest {

    companion object {
        fun grid(rows: List<String>): List<List<Cell>> = rows.map { line ->
            line.map { ch ->
                when (ch) {
                    '#' -> Cell.WALL
                    'o' -> Cell.NODE
                    'X' -> Cell.EXIT
                    '*' -> Cell.HAZARD
                    'S' -> Cell.START
                    else -> Cell.EMPTY
                }
            }
        }

        fun findStart(rows: List<String>): Pair<Int, Int> {
            for (r in rows.indices) {
                val c = rows[r].indexOf('S')
                if (c >= 0) return r to c
            }
            throw IllegalArgumentException("chamber must contain a START cell ('S')")
        }

        fun chamber(rows: List<String>, par: Int): Chamber {
            val (sr, sc) = findStart(rows)
            return Chamber(grid(rows), sr, sc, par)
        }
    }

    // ---- slide-until-blocked stops at wall -----------------------------

    @Test
    fun slideStopsAtWall() {
        // S at (1,3). First move is LEFT: slides (1,2),(1,1), then wall at (1,0).
        val ch = chamber(
            listOf(
                "#####",
                "#..S#",
                "#...#",
                "#...#",
                "#####"
            ), 3
        )
        val e = GravityFluxEngine(ch)
        e.rotateAndSlide() // gravity becomes LEFT
        assertEquals(1, e.orbRow)
        assertEquals(1, e.orbCol)
    }

    // ---- out-of-bounds stop --------------------------------------------

    @Test
    fun slideStopsAtBoundary() {
        // S at (1,1). Three rotations -> RIGHT; slide to (1,3) (wall at (1,4)).
        val ch = chamber(
            listOf(
                "#####",
                "#S..#",
                "#...#",
                "#...#",
                "#####"
            ), 3
        )
        val e = GravityFluxEngine(ch)
        e.rotateAndSlide() // LEFT
        e.rotateAndSlide() // UP
        e.rotateAndSlide() // RIGHT -> stop at (1,3)
        assertEquals(1, e.orbRow)
        assertEquals(3, e.orbCol)
    }

    // ---- node collected along path -------------------------------------

    @Test
    fun nodeCollectedAlongPath() {
        // S at (2,3), node at (2,2). LEFT collects the node, stops at wall (2,0).
        val ch = chamber(
            listOf(
                "#####",
                "#...#",
                "#..oS",
                "#...#",
                "#####"
            ), 2
        )
        val e = GravityFluxEngine(ch)
        e.rotateAndSlide() // LEFT
        assertEquals(1, e.collectedNodes.size)
        assertTrue(e.collectedNodes.contains(2 to 3))
        assertEquals(2, e.orbRow)
        assertEquals(1, e.orbCol)
    }

    // ---- hazard causes FAILED ------------------------------------------

    @Test
    fun hazardCausesFailure() {
        // S at (1,1); hazard at (1,3) to the right. Three rotations -> RIGHT.
        val ch = chamber(
            listOf(
                "#####",
                "#S*#",
                "#...#",
                "#####"
            ), 2
        )
        val e = GravityFluxEngine(ch)
        e.rotateAndSlide() // LEFT (stays)
        e.rotateAndSlide() // UP (stays)
        e.rotateAndSlide() // RIGHT -> enter hazard at (1,3)
        assertEquals(GameState.FAILED, e.state)
    }

    // ---- win condition -------------------------------------------------

    @Test
    fun winWhenExitReachedWithAllNodes() {
        // S at (1,4); nodes (1,2),(1,3); exit at (1,1). LEFT collects both, hits exit.
        val ch = chamber(
            listOf(
                "#######",
                "#XooS.#",
                "#######"
            ), 1
        )
        val e = GravityFluxEngine(ch)
        e.rotateAndSlide() // LEFT -> collects (1,3),(1,2), reaches (1,1)=EXIT -> WON
        assertEquals(GameState.WON, e.state)
        assertEquals(1, e.moves)
        assertEquals(3, e.stars()) // par=1, moves=1, undo unused
    }

    @Test
    fun winOnlyWithAllNodes() {
        // Same chamber: requires BOTH nodes before exit counts as a win.
        val ch = chamber(
            listOf(
                "#######",
                "#XooS.#",
                "#######"
            ), 1
        )
        val e = GravityFluxEngine(ch)
        assertEquals(2, e.totalNodes())
        e.rotateAndSlide()
        assertEquals(GameState.WON, e.state)
        assertEquals(2, e.collectedNodes.size)
    }

    // ---- undo restores previous state ----------------------------------

    @Test
    fun undoRestoresState() {
        val ch = chamber(
            listOf(
                "#####",
                "#..S#",
                "#...#",
                "#...#",
                "#####"
            ), 3
        )
        val e = GravityFluxEngine(ch)
        val (r0, c0) = e.orbRow to e.orbCol
        val g0 = e.gravity
        e.rotateAndSlide()
        assertTrue(e.moves == 1)
        assertTrue(e.orbRow != r0 || e.orbCol != c0 || e.gravity != g0)
        e.undo()
        assertEquals(r0, e.orbRow)
        assertEquals(c0, e.orbCol)
        assertEquals(g0, e.gravity)
        assertEquals(0, e.moves)
        // Second undo is a no-op (token consumed).
        e.undo()
        assertEquals(0, e.moves)
    }

    // ---- reset restores initial state ----------------------------------

    @Test
    fun resetRestoresInitial() {
        val ch = chamber(
            listOf(
                "#####",
                "#..o#",
                "#..S#",
                "#...#",
                "#####"
            ), 2
        )
        val e = GravityFluxEngine(ch)
        repeat(3) { e.rotateAndSlide() }
        e.reset()
        assertEquals(ch.startRow, e.orbRow)
        assertEquals(ch.startCol, e.orbCol)
        assertEquals(Gravity.DOWN, e.gravity)
        assertEquals(0, e.moves)
        assertEquals(GameState.PLAYING, e.state)
        assertEquals(0, e.collectedNodes.size)
        assertTrue(e.undoAvailable)
    }

    // ---- dailyIndex determinism ----------------------------------------

    @Test
    fun dailyIndexDeterministicAndInRange() {
        val size = GravityFluxEngine.CAMPAIGN.size
        for (day in listOf(1, 42, 100, 200, 365, 9999)) {
            val a = GravityFluxEngine.dailyIndex(day)
            val b = GravityFluxEngine.dailyIndex(day)
            assertEquals(a, b)
            assertTrue(a in 0 until size)
        }
        // Negative day must be safe (non-negative, in range).
        val neg = GravityFluxEngine.dailyIndex(-5)
        assertTrue(neg in 0 until size)
    }

    // ---- star rating boundaries ----------------------------------------

    @Test
    fun starRatingBoundaries() {
        // Open chamber with no exit/hazard so moves accumulate without win/fail.
        val ch = chamber(
            listOf(
                "#####",
                "#S..#",
                "#...#",
                "#...#",
                "#####"
            ), 3
        )

        fun ratingForMoves(mv: Int): Int {
            val e = GravityFluxEngine(ch)
            repeat(mv) { e.rotateAndSlide() }
            return e.stars()
        }

        // 3 stars: moves <= par (and undo unused).
        assertEquals(3, ratingForMoves(1))
        assertEquals(3, ratingForMoves(3))
        // 2 stars: par < moves <= par+2.
        assertEquals(2, ratingForMoves(4)) // par+1
        assertEquals(2, ratingForMoves(5)) // par+2
        // 1 star: moves > par+2.
        assertEquals(1, ratingForMoves(6)) // par+3
    }

    @Test
    fun undoUsedDropsMaxStars() {
        // A perfect run that used undo can earn at most 2 stars.
        val ch = chamber(
            listOf(
                "#######",
                "#XooS.#",
                "#######"
            ), 3
        )
        val e = GravityFluxEngine(ch)
        e.rotateAndSlide() // win at move 1, undoAvailable = true
        assertEquals(3, e.stars())
        e.undo() // consumes the single undo token
        assertFalse(e.undoAvailable)
        assertEquals(0, e.moves)
        assertEquals(2, e.stars()) // undo used -> capped at 2
    }

    // ---- all campaign chambers solvable --------------------------------

    @Test
    fun allCampaignChambersSolvable() {
        for (ch in GravityFluxEngine.CAMPAIGN) {
            assertTrue(
                "Campaign chamber not solvable (rows=${ch.rows}, cols=${ch.cols})",
                GravityFluxEngine.isSolvable(ch)
            )
        }
    }

    // ---- gravity rotate order ------------------------------------------

    @Test
    fun gravityRotateClockwise() {
        assertEquals(Gravity.LEFT, Gravity.DOWN.rotate())
        assertEquals(Gravity.UP, Gravity.LEFT.rotate())
        assertEquals(Gravity.RIGHT, Gravity.UP.rotate())
        assertEquals(Gravity.DOWN, Gravity.RIGHT.rotate())
    }

    // ---- no moves after terminal state ---------------------------------

    @Test
    fun noMovesAfterWin() {
        val ch = chamber(
            listOf(
                "#####",
                "#XoS#",
                "#####"
            ), 1
        )
        val e = GravityFluxEngine(ch)
        e.rotateAndSlide() // LEFT -> collect node, reach exit -> WON
        assertEquals(GameState.WON, e.state)
        val movesAtWin = e.moves
        e.rotateAndSlide() // ignored after WON
        assertEquals(movesAtWin, e.moves)
    }
}
