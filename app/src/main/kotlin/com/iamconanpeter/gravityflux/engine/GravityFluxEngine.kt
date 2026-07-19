/*
 * GravityFluxEngine.kt
 *
 * Core gameplay engine for "Gravity Flux". This file is PURE Kotlin with ZERO
 * android.* imports so it can be unit-tested on the plain JVM with JUnit4.
 *
 * Game concept: a flux orb always "falls" toward the active gravity vector.
 * A single tap rotates gravity 90 degrees clockwise and the orb slides until
 * it is blocked. The player must route the orb through every NODE and into the
 * EXIT portal, avoiding HAZARDs.
 */

package com.iamconanpeter.gravityflux.engine

/**
 * The kind of content occupying a single grid cell.
 */
enum class Cell {
    EMPTY,   // open space the orb can travel through
    WALL,    // blocks the orb (stops the slide)
    NODE,    // a flux node the orb must pass over to collect
    EXIT,    // the portal; win only when all nodes are collected
    HAZARD,  // entering it fails the run
    START    // the orb's initial cell
}

/**
 * Direction of gravity. Rotating is always clockwise:
 * DOWN -> LEFT -> UP -> RIGHT -> DOWN.
 */
enum class Gravity {
    DOWN,
    LEFT,
    UP,
    RIGHT;

    /**
     * Rotate gravity 90 degrees clockwise.
     */
    fun rotate(): Gravity = when (this) {
        DOWN -> LEFT
        LEFT -> UP
        UP -> RIGHT
        RIGHT -> DOWN
    }
}

/**
 * A single puzzle chamber. Immutable description of the board.
 *
 * @param grid      Rows of cells. Row 0 is the top. Column 0 is the left.
 * @param startRow  Zero-based row of the START cell.
 * @param startCol  Zero-based column of the START cell.
 * @param par       Target number of moves for a 3-star (perfect) clear.
 */
data class Chamber(
    val grid: List<List<Cell>>,
    val startRow: Int,
    val startCol: Int,
    val par: Int
) {
    val rows: Int get() = grid.size
    val cols: Int get() = if (grid.isEmpty()) 0 else grid[0].size

    /** Returns the cell at (row, col), or null if out of bounds. */
    fun cellAt(row: Int, col: Int): Cell? {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return null
        return grid[row][col]
    }
}

/**
 * Overall run state.
 */
enum class GameState {
    PLAYING,
    WON,
    FAILED
}

/**
 * Mutable per-run snapshot used for the single undo token.
 */
private data class StateKey(
    val row: Int,
    val col: Int,
    val gravity: Gravity,
    val collected: Set<Pair<Int, Int>>
)

private data class Snapshot(
    val orbRow: Int,
    val orbCol: Int,
    val gravity: Gravity,
    val collected: Set<Pair<Int, Int>>,
    val moves: Int,
    val state: GameState
)

/**
 * Drives a single chamber: owns the orb position, gravity, collected nodes,
 * move count, undo snapshot, and win/lose state.
 */
class GravityFluxEngine(private val chamber: Chamber) {

    /** Read-only access to the chamber the engine is solving. */
    val chamberView: Chamber get() = chamber


    // ---- Live state -----------------------------------------------------
    var orbRow: Int = chamber.startRow
        private set
    var orbCol: Int = chamber.startCol
        private set
    var gravity: Gravity = Gravity.DOWN
        private set
    val collectedNodes: MutableSet<Pair<Int, Int>> = mutableSetOf()
    var moves: Int = 0
        private set
    var state: GameState = GameState.PLAYING
        private set
    var undoAvailable: Boolean = true
        private set

    /** Total number of NODEs in the chamber; cached at construction. */
    val totalNodesCount: Int

    private var snapshot: Snapshot? = null

    init {
        totalNodesCount = chamber.grid.sumOf { row -> row.count { it == Cell.NODE } }
    }

    // ---- Public actions -------------------------------------------------

    /**
     * Rotate gravity clockwise and slide the orb until blocked.
     *
     * Pushes an undo snapshot FIRST, then rotates, then slides, then increments
     * the move counter. Moves are not allowed after the run is WON or FAILED.
     */
    fun rotateAndSlide() {
        if (state != GameState.PLAYING) return

        snapshot = Snapshot(
            orbRow = orbRow,
            orbCol = orbCol,
            gravity = gravity,
            collected = collectedNodes.toSet(),
            moves = moves,
            state = state
        )
        undoAvailable = true

        gravity = gravity.rotate()
        slideUntilBlocked()
        moves++
    }

    /**
     * From the current orb cell, step one cell at a time in the active gravity
     * direction until the orb is blocked, fails, or wins.
     */
    private fun slideUntilBlocked() {
        while (true) {
            val (dr, dc) = directionDelta(gravity)
            val nextRow = orbRow + dr
            val nextCol = orbCol + dc

            val next: Cell? = chamber.cellAt(nextRow, nextCol)

            // Out of bounds or wall -> stop in current cell.
            if (next == null || next == Cell.WALL) {
                return
            }

            // Move into the next cell.
            orbRow = nextRow
            orbCol = nextCol

            when (next) {
                Cell.HAZARD -> {
                    state = GameState.FAILED
                    return
                }
                Cell.NODE -> {
                    collectedNodes.add(nextRow to nextCol)
                }
                Cell.EXIT -> {
                    if (collectedNodes.size == totalNodesCount) {
                        state = GameState.WON
                        return
                    }
                    // Exit without all nodes: pass through and keep sliding.
                }
                else -> {
                    // EMPTY or START: keep sliding.
                }
            }
        }
    }

    /** Count the NODE cells in the chamber (convenience). */
    fun totalNodes(): Int = totalNodesCount

    /**
     * Restore the most recent snapshot. Consumes the single undo token and
     * cannot be used again until the next slide pushes a fresh snapshot.
     */
    fun undo() {
        val snap = snapshot ?: return
        if (!undoAvailable) return
        orbRow = snap.orbRow
        orbCol = snap.orbCol
        gravity = snap.gravity
        collectedNodes.clear()
        collectedNodes.addAll(snap.collected)
        moves = snap.moves
        state = snap.state
        undoAvailable = false
    }

    /**
     * Restore the chamber to its initial state.
     */
    fun reset() {
        orbRow = chamber.startRow
        orbCol = chamber.startCol
        gravity = Gravity.DOWN
        collectedNodes.clear()
        moves = 0
        state = GameState.PLAYING
        undoAvailable = true
        snapshot = null
    }

    /**
     * Star rating for the current run.
     * 3 stars: moves <= par and undo was NOT used (undoAvailable still true).
     * 2 stars: moves <= par + 2.
     * 1 star: otherwise.
     */
    fun stars(): Int {
        return when {
            moves <= par && undoAvailable -> 3
            moves <= par + 2 -> 2
            else -> 1
        }
    }

    private val par: Int get() = chamber.par

    companion object {
        /** Row/col delta for each gravity direction. */
        private fun directionDelta(g: Gravity): Pair<Int, Int> = when (g) {
            Gravity.DOWN -> 1 to 0
            Gravity.LEFT -> 0 to -1
            Gravity.UP -> -1 to 0
            Gravity.RIGHT -> 0 to 1
        }

        /** The full campaign. Grids grow 5x5 -> 9x9 and every chamber is solvable. */
        val CAMPAIGN: List<Chamber> = buildCampaign()

        /**
         * Deterministic daily chamber index for a given day-of-year.
         */
        fun dailyIndex(dayOfYear: Int): Int =
            ((dayOfYear % CAMPAIGN.size) + CAMPAIGN.size) % CAMPAIGN.size

        /**
         * Returns true if the chamber can be won by some sequence of gravity
         * rotations. BFS over (orbRow, orbCol, gravity) states, trying all four
         * rotations from each state. Visited set bounds the search.
         */
        fun isSolvable(chamber: Chamber): Boolean {
            val start = chamber.startRow to chamber.startCol
            val totalNodes = chamber.grid.sumOf { r -> r.count { it == Cell.NODE } }

            val startState = StateKey(chamber.startRow, chamber.startCol, Gravity.DOWN, emptySet())
            val visited = mutableSetOf(startState)
            val queue: ArrayDeque<StateKey> = ArrayDeque()
            queue.add(startState)

            while (queue.isNotEmpty()) {
                val (row, col, grav, prevCollected) = queue.removeFirst()

                // Try all four distinct rotations from this state. Rotations are
                // chained (DOWN -> LEFT -> UP -> RIGHT) so each branch is a
                // single 90-degree bend from the previous one.
                var ng = grav
                repeat(4) {
                    ng = ng.rotate()

                    // Simulate a slide from (row, col) with gravity ng. Collected
                    // nodes accumulate across the whole run, so we start from the
                    // nodes already gathered on the path that reached this state.
                    var r = row
                    var c = col
                    val collected = prevCollected.toMutableSet()
                    var won = false
                    var failed = false

                    while (true) {
                        val (dr, dc) = directionDelta(ng)
                        val nr = r + dr
                        val nc = c + dc
                        val cell = chamber.cellAt(nr, nc)
                        if (cell == null || cell == Cell.WALL) break
                        r = nr
                        c = nc
                        when (cell) {
                            Cell.HAZARD -> { failed = true; break }
                            Cell.NODE -> collected.add(r to c)
                            Cell.EXIT -> {
                                if (collected.size == totalNodes) { won = true; break }
                            }
                            else -> { /* empty/start */ }
                        }
                    }

                    if (failed) return@repeat
                    if (won) return true

                    val key = StateKey(r, c, ng, collected)
                    if (visited.add(key)) {
                        queue.add(key)
                    }
                }
            }
            return false
        }

        /**
         * Helper to build a grid from an array of strings for readability.
         * Legend: '.' empty, '#' wall, 'o' node, 'X' exit, '*' hazard, 'S' start.
         */
        private fun grid(rows: List<String>): List<List<Cell>> =
            rows.map { line ->
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

        private fun findStart(rows: List<String>): Pair<Int, Int> {
            for (r in rows.indices) {
                val c = rows[r].indexOf('S')
                if (c >= 0) return r to c
            }
            throw IllegalArgumentException("Chamber must contain a START cell ('S')")
        }

        private fun buildCampaign(): List<Chamber> {
            // Grids grow 5x5 -> 9x9. Nodes sit in the orb's starting column so a
            // single DOWN collects them, then a RIGHT reaches the EXIT (which is
            // on the bottom interior row). Hazards sit off the solution path.
            // Every chamber is verified solvable by isSolvable() at load time.
            val defs = listOf(
                // ---- Chamber 0: 5x5 tutorial, one node, no hazard ----------
                """
                #####
                #S..#
                #o..#
                #..X#
                #####
                """.trimIndent() to 3,

                // ---- Chamber 1: 5x5, two nodes ----------------------------
                """
                #####
                #S..#
                #o..#
                #o..#
                #..X#
                #####
                """.trimIndent() to 4,

                // ---- Chamber 2: 7x7, hazard introduced --------------------
                """
                #######
                #S....#
                #o....#
                #o....#
                #....*#
                #.....#
                #....X#
                #######
                """.trimIndent() to 5,

                // ---- Chamber 3: 7x7, more nodes ---------------------------
                """
                #######
                #S....#
                #o....#
                #o....#
                #o....#
                #....*#
                #....X#
                #######
                """.trimIndent() to 6,

                // ---- Chamber 4: 9x9, hazards + 3 nodes --------------------
                """
                #########
                #S......#
                #o......#
                #o......#
                #o......#
                #......*#
                #.......#
                #.......#
                #.....X.#
                #########
                """.trimIndent() to 7,

                // ---- Chamber 5: 9x9, dense finale -------------------------
                """
                #########
                #S......#
                #o......#
                #o......#
                #o......#
                #.......#
                #......*#
                #.......#
                #.....X.#
                #########
                """.trimIndent() to 8
            )

            return defs.map { (ascii, par) ->
                val lines = ascii.lines().filter { it.isNotBlank() }
                val (sr, sc) = findStart(lines)
                Chamber(grid = grid(lines), startRow = sr, startCol = sc, par = par)
            }.also { chambers ->
                // Fail fast during development if a shipped chamber is unsolvable.
                for (c in chambers) {
                    require(isSolvable(c)) { "Campaign chamber is not solvable: $c" }
                }
            }
        }
    }
}
