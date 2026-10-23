package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2

data class GridPos(val gx: Int, val gy: Int)

/**
 * Where a ship may sail, read from a dedicated **mask** - white is sea, anything else is land.
 *
 * This used to be sampled from the artwork itself, on the rule "blue channel at least as large as
 * green". The rule worked for a relief map, and it quietly cost a great deal: rivers on that map
 * are drawn in blue, so the game held the Mississippi and the Orinoco to be open sea and would
 * send ships a hundred miles up them. Sampling the picture also inherited every speck of the
 * artist's texture as a reef, and left 277 pockets of water with no way out - places a ship could
 * be put and could never leave.
 *
 * Separating the two fixes that at the root and frees the artwork besides: the painted map can be
 * anything at all, because nothing in the game asks it where the water is. The mask is generated
 * once, verified (every port reachable, no landlocked pockets) and shipped alongside.
 *
 * World units need not equal mask pixels 1:1 - pixelScaleX/Y convert a world coordinate to the
 * right sample either way.
 */
class WorldMap(
    pixmap: Pixmap,
    private val worldWidth: Float,
    private val worldHeight: Float,
    private val cellSize: Float
) {
    private val gridW = (worldWidth / cellSize).toInt()
    private val gridH = (worldHeight / cellSize).toInt()
    private val walkable = BooleanArray(gridW * gridH)

    /**
     * Cells of open sea between each cell and the nearest land, capped at
     * [MAX_TRACKED_CLEARANCE]. Land is 0; the map's own edge counts as a shore, since a hull
     * cannot cross it either.
     *
     * This exists because a route is sailed by a ship with a turning circle, not by a point. A*
     * on plain distance hugs every coastline it rounds - that is the shortest way past a
     * headland - and a hull whose turning circle is wider than the berth the route left it
     * cannot follow that line at all: it overshoots into the shallows, slides along the beach,
     * and the watchdog reports it as stuck. Keeping a berth is cheaper than steering out of one.
     */
    private val clearanceCells = ByteArray(gridW * gridH)

    // --- pathfinding working set, reused between calls (see findPathInternal) ---
    private val gScore = FloatArray(gridW * gridH)
    private val cameFrom = IntArray(gridW * gridH)
    private val seenStamp = IntArray(gridW * gridH)
    private val closedStamp = IntArray(gridW * gridH)
    /** Which search each stamp belongs to. Bumping this is how the arrays are "cleared" without
     *  touching a hundred thousand entries per route. */
    private var searchStamp = 0
    private var heap = LongArray(1024)
    private var heapSize = 0

    init {
        val pixelScaleX = pixmap.width / worldWidth
        val pixelScaleY = pixmap.height / worldHeight
        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                val worldX = (gx + 0.5f) * cellSize
                val worldY = (gy + 0.5f) * cellSize
                val px = (worldX * pixelScaleX).toInt().coerceIn(0, pixmap.width - 1)
                val py = (pixmap.height - worldY * pixelScaleY).toInt().coerceIn(0, pixmap.height - 1)
                walkable[gy * gridW + gx] = isWaterColor(pixmap.getPixel(px, py))
            }
        }
        computeClearance()
    }

    /**
     * Distance-to-land for every cell, in one sweep outward from every shore at once.
     *
     * A breadth-first flood from all land cells simultaneously: each ring of sea it reaches is
     * one cell further out than the last, so a single pass over the grid labels the whole map.
     * Costs one visit per cell at load time and nothing afterwards, which is the point - the
     * pathfinder asks this question for every cell it expands.
     */
    private fun computeClearance() {
        val queue = IntArray(gridW * gridH)
        var head = 0
        var tail = 0
        for (cell in walkable.indices) {
            if (walkable[cell]) {
                clearanceCells[cell] = UNVISITED_CLEARANCE
            } else {
                clearanceCells[cell] = 0
                queue[tail++] = cell
            }
        }
        // The edge of the map is a shore like any other: isWater refuses anything outside the
        // bounds, so a hull can no more cross it than it can cross Cuba. Seeded after the land so
        // the queue stays in non-decreasing order, which is what makes a plain FIFO correct here.
        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                if (gx != 0 && gy != 0 && gx != gridW - 1 && gy != gridH - 1) continue
                val cell = gy * gridW + gx
                if (clearanceCells[cell] != UNVISITED_CLEARANCE) continue
                clearanceCells[cell] = 1
                queue[tail++] = cell
            }
        }
        while (head < tail) {
            val cell = queue[head++]
            val next = clearanceCells[cell] + 1
            if (next > MAX_TRACKED_CLEARANCE) continue
            val cx = cell % gridW
            val cy = cell / gridW
            for (direction in 0 until 8) {
                val nx = cx + DIR_X[direction]
                val ny = cy + DIR_Y[direction]
                if (nx < 0 || ny < 0 || nx >= gridW || ny >= gridH) continue
                val neighbour = ny * gridW + nx
                if (clearanceCells[neighbour] != UNVISITED_CLEARANCE) continue
                clearanceCells[neighbour] = next.toByte()
                queue[tail++] = neighbour
            }
        }
        // Water the sweep never reached is further out than we care to count - open ocean.
        for (cell in clearanceCells.indices) {
            if (clearanceCells[cell] == UNVISITED_CLEARANCE) {
                clearanceCells[cell] = MAX_TRACKED_CLEARANCE.toByte()
            }
        }
    }

    /** Cells of sea between ([gx], [gy]) and the nearest land. Callers must have checked the cell
     *  is on the grid. */
    private fun clearanceAt(gx: Int, gy: Int): Int = clearanceCells[gy * gridW + gx].toInt()

    /** Cells of sea between a world point and the nearest land - 0 on land or off the map. */
    fun clearanceCellsAt(x: Float, y: Float): Int {
        if (x < 0f || y < 0f || x > worldWidth || y > worldHeight) return 0
        val gx = (x / cellSize).toInt().coerceIn(0, gridW - 1)
        val gy = (y / cellSize).toInt().coerceIn(0, gridH - 1)
        return clearanceAt(gx, gy)
    }

    /**
     * What sailing through [cell] costs over and above the distance covered.
     *
     * A penalty rather than a wall, deliberately: the Caribbean has channels a hull must take
     * because there is no other way through, and making shallow water impassable would strand
     * every port whose approach is - as approaches are - close inshore. At a quarter of a cell
     * per cell of missing berth, a route will go half again as far to get out of the shallows and
     * no further, which is the difference between preferring open water and refusing a strait.
     */
    private fun coastPenalty(cell: Int): Float {
        val clearance = clearanceCells[cell].toInt()
        if (clearance >= BERTH_CELLS) return 0f
        return COAST_PENALTY_PER_CELL * (BERTH_CELLS - clearance)
    }

    /** White is sea. Deliberately not a heuristic over an artist's palette - see the class note. */
    private fun isWaterColor(rgba8888: Int): Boolean {
        val r = (rgba8888 ushr 24) and 0xFF
        val g = (rgba8888 ushr 16) and 0xFF
        val b = (rgba8888 ushr 8) and 0xFF
        return r + g + b > 382   // brighter than mid grey
    }

    fun isWater(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f || x > worldWidth || y > worldHeight) return false
        val gx = (x / cellSize).toInt().coerceIn(0, gridW - 1)
        val gy = (y / cellSize).toInt().coerceIn(0, gridH - 1)
        return walkable[gy * gridW + gx]
    }

    /** Ring-search outward from (x,y) for the nearest walkable cell - used to give ports a
     *  valid sailing destination even though the port marker itself sits on/near the coast. */
    fun findNearestWater(x: Float, y: Float, maxRadiusCells: Int = 30): Vector2? {
        val center = worldToGrid(x, y)
        if (isWalkableCell(center.gx, center.gy)) return gridToWorld(center)
        for (radius in 1..maxRadiusCells) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != radius) continue
                    val gx = center.gx + dx
                    val gy = center.gy + dy
                    if (isWalkableCell(gx, gy)) return gridToWorld(GridPos(gx, gy))
                }
            }
        }
        return null
    }

    /** [avoid]/[avoidRadiusWorld] treat a circle of world-space as temporarily impassable for
     *  this one call only (used by a fleet captain steering around a nearby threat, see
     *  PortRoyaleGame's evasion logic) - the grid itself is never modified, so every other caller
     *  (autopilot, AI patrols, a captain's normal trade route) is unaffected. If avoiding the
     *  circle leaves no route at all (it blocks the only channel through), falls back to the
     *  unavoidant path rather than returning null - better to sail past danger than freeze. */
    fun findPath(
        startWorld: Vector2,
        endWorld: Vector2,
        avoid: Vector2? = null,
        avoidRadiusWorld: Float = 0f
    ): List<Vector2>? {
        if (avoid == null || avoidRadiusWorld <= 0f) return findPathInternal(startWorld, endWorld, null, 0f)
        return findPathInternal(startWorld, endWorld, avoid, avoidRadiusWorld)
            ?: findPathInternal(startWorld, endWorld, null, 0f)
    }

    /**
     * A* over the sampled grid.
     *
     * Written against flat arrays rather than the obvious `HashMap<GridPos, Float>` because of
     * what it costs when the world actually leans on it. The first version allocated a `GridPos`,
     * a `Node` and several hash entries for every cell it touched, on a grid of four hundred by
     * two hundred and sixty-nine. That was invisible while ships asked for a route once every
     * half-minute; the moment chases, flights and formation-keeping all began re-laying courses
     * every second or two, the whole game dropped to a visible stutter - every ship on the map
     * moving as though it were being drawn at three frames a second.
     *
     * Nothing here allocates per cell. The working arrays are kept between calls and marked with
     * a search number instead of being cleared, so the cost of a route is the search itself and
     * nothing else.
     */
    private fun findPathInternal(
        startWorld: Vector2,
        endWorld: Vector2,
        avoid: Vector2?,
        avoidRadiusWorld: Float
    ): List<Vector2>? {
        val start = worldToGrid(startWorld.x, startWorld.y)
        val goal = worldToGrid(endWorld.x, endWorld.y)
        val avoidGrid = avoid?.let { worldToGrid(it.x, it.y) }
        val avoidRadiusCells = avoidRadiusWorld / cellSize
        val avoidRadiusSq = avoidRadiusCells * avoidRadiusCells

        fun passable(gx: Int, gy: Int): Boolean {
            if (!isWalkableCell(gx, gy)) return false
            if (avoidGrid == null) return true
            val dx = (gx - avoidGrid.gx).toFloat()
            val dy = (gy - avoidGrid.gy).toFloat()
            return dx * dx + dy * dy > avoidRadiusSq
        }

        if (!passable(goal.gx, goal.gy) || !passable(start.gx, start.gy)) return null
        if (start == goal) return listOf(endWorld.cpy())

        searchStamp++
        heapSize = 0
        val startIndex = start.gy * gridW + start.gx
        val goalIndex = goal.gy * gridW + goal.gx
        gScore[startIndex] = 0f
        cameFrom[startIndex] = -1
        seenStamp[startIndex] = searchStamp
        heapPush(heuristic(start.gx, start.gy, goal.gx, goal.gy), startIndex)

        var expanded = 0
        while (heapSize > 0) {
            val currentIndex = (heapPop() and 0xFFFFFFFFL).toInt()
            if (closedStamp[currentIndex] == searchStamp) continue
            closedStamp[currentIndex] = searchStamp
            if (currentIndex == goalIndex) {
                return reconstruct(currentIndex, endWorld, avoid, avoidRadiusWorld)
            }
            // A goal behind a closed coast would otherwise mean walking every square of open
            // water in the Caribbean before admitting defeat.
            if (++expanded > MAX_EXPANSIONS) return null

            val cx = currentIndex % gridW
            val cy = currentIndex / gridW
            val currentG = gScore[currentIndex]
            for (direction in 0 until 8) {
                val dx = DIR_X[direction]
                val dy = DIR_Y[direction]
                val nx = cx + dx
                val ny = cy + dy
                if (!passable(nx, ny)) continue
                // No cutting the corner of a headland diagonally.
                if (dx != 0 && dy != 0 && (!passable(cx + dx, cy) || !passable(cx, cy + dy))) continue
                val neighbour = ny * gridW + nx
                if (closedStamp[neighbour] == searchStamp) continue
                val tentative = currentG +
                    (if (dx != 0 && dy != 0) DIAGONAL_COST else 1f) +
                    coastPenalty(neighbour)
                if (seenStamp[neighbour] != searchStamp || tentative < gScore[neighbour]) {
                    seenStamp[neighbour] = searchStamp
                    gScore[neighbour] = tentative
                    cameFrom[neighbour] = currentIndex
                    heapPush(tentative + heuristic(nx, ny, goal.gx, goal.gy), neighbour)
                }
            }
        }
        return null
    }

    private fun reconstruct(
        goalIndex: Int,
        endWorld: Vector2,
        avoid: Vector2?,
        avoidRadiusWorld: Float
    ): List<Vector2> {
        var length = 0
        var cell = goalIndex
        while (cell != -1) {
            length++
            cell = cameFrom[cell]
        }
        val path = arrayOfNulls<Vector2>(length)
        cell = goalIndex
        var at = length - 1
        while (cell != -1) {
            path[at--] = Vector2((cell % gridW + 0.5f) * cellSize, (cell / gridW + 0.5f) * cellSize)
            cell = cameFrom[cell]
        }
        @Suppress("UNCHECKED_CAST")
        val result = (path as Array<Vector2>).toMutableList()
        result[result.size - 1] = endWorld.cpy()
        return stringPull(result, avoid, avoidRadiusWorld)
    }

    /**
     * Throws away every waypoint a hull does not actually need, leaving the long legs and the
     * genuine corners.
     *
     * A* works in cells, so it returns a staircase: a point every ten units, each a few degrees
     * off the last. No ship can sail that. Every hull in the game turns inside a circle of
     * twenty-two to twenty-seven units - wider than the distance between two of those waypoints -
     * so it overshoots each one, comes round, overshoots the next, and crawls along at a fraction
     * of its own speed while never being anywhere near land. Sixty per cent of the diagnostics
     * log was this.
     *
     * The fix is geometric, not a tuned number: keep a waypoint only where the straight line past
     * it would cross something. What comes back is a handful of legs hundreds of units long, and
     * a turn only where the hull really must come round a headland.
     */
    private fun stringPull(
        path: List<Vector2>,
        avoid: Vector2?,
        avoidRadiusWorld: Float
    ): List<Vector2> {
        if (path.size <= 2) return path
        val kept = ArrayList<Vector2>(16)
        kept.add(path[0])
        var anchor = 0
        var index = 1
        while (index < path.lastIndex) {
            if (!clearRun(path[anchor], path[index + 1], avoid, avoidRadiusWorld)) {
                kept.add(path[index])
                anchor = index
            }
            index++
        }
        kept.add(path[path.lastIndex])
        return kept
    }

    /**
     * True when a hull can sail the straight line from [from] to [to] and keep a berth off the
     * shore the whole way.
     *
     * The berth is the point. Testing only for water would let a shortcut shave the tip of a
     * headland, passing through sea a cell wide - which is water on the grid and a stranding in
     * practice, since a hull displaced a few units by crowding then has the beach under its lee.
     * Close inshore the test simply fails and the staircase is kept, which is the right answer
     * there: a ship threading a harbour approach wants every waypoint it was given.
     */
    private fun clearRun(
        from: Vector2,
        to: Vector2,
        avoid: Vector2?,
        avoidRadiusWorld: Float
    ): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length <= 0f) return true
        // Half a cell, so the walk cannot step over a cell corner-to-corner and miss it.
        val steps = (length / (cellSize * 0.5f)).toInt() + 1
        for (step in 0..steps) {
            val along = step.toFloat() / steps
            val gx = ((from.x + dx * along) / cellSize).toInt()
            val gy = ((from.y + dy * along) / cellSize).toInt()
            if (!isWalkableCell(gx, gy)) return false
            if (clearanceAt(gx, gy) < SHORTCUT_BERTH_CELLS) return false
            // The search was told to treat a circle of sea as impassable - a captain steering
            // round a pirate. A shortcut that ignores it would straighten the detour back
            // through the very danger it was laid to avoid, which is worse than no detour at
            // all: the captain would believe they had evaded.
            if (avoid != null && avoidRadiusWorld > 0f) {
                val x = from.x + dx * along
                val y = from.y + dy * along
                if (avoid.dst(x, y) <= avoidRadiusWorld) return false
            }
        }
        return true
    }

    /**
     * The open set, as a binary heap of longs: the f-score in the high word, the cell in the low
     * one. Float bits of a non-negative float order exactly as the float does, so comparing the
     * packed longs sorts by cost without unpacking anything - and without boxing a single value.
     */
    private fun heapPush(f: Float, cell: Int) {
        if (heapSize == heap.size) heap = heap.copyOf(heap.size * 2)
        val packed = (java.lang.Float.floatToIntBits(f).toLong() shl 32) or cell.toLong()
        var index = heapSize++
        heap[index] = packed
        while (index > 0) {
            val parent = (index - 1) / 2
            if (heap[parent] <= heap[index]) break
            val swap = heap[parent]
            heap[parent] = heap[index]
            heap[index] = swap
            index = parent
        }
    }

    private fun heapPop(): Long {
        val top = heap[0]
        heapSize--
        if (heapSize > 0) {
            heap[0] = heap[heapSize]
            var index = 0
            while (true) {
                val left = index * 2 + 1
                val right = left + 1
                var smallest = index
                if (left < heapSize && heap[left] < heap[smallest]) smallest = left
                if (right < heapSize && heap[right] < heap[smallest]) smallest = right
                if (smallest == index) break
                val swap = heap[smallest]
                heap[smallest] = heap[index]
                heap[index] = swap
                index = smallest
            }
        }
        return top
    }

    private fun heuristic(ax: Int, ay: Int, bx: Int, by: Int): Float {
        val dx = (ax - bx).toFloat()
        val dy = (ay - by).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun isWalkableCell(gx: Int, gy: Int): Boolean {
        if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH) return false
        return walkable[gy * gridW + gx]
    }

    private fun worldToGrid(x: Float, y: Float) = GridPos(
        (x / cellSize).toInt().coerceIn(0, gridW - 1),
        (y / cellSize).toInt().coerceIn(0, gridH - 1)
    )

    private fun gridToWorld(pos: GridPos) = Vector2((pos.gx + 0.5f) * cellSize, (pos.gy + 0.5f) * cellSize)

    private companion object {
        val DIR_X = intArrayOf(0, 1, 0, -1, 1, 1, -1, -1)
        val DIR_Y = intArrayOf(1, 0, -1, 0, 1, -1, 1, -1)
        const val DIAGONAL_COST = 1.41421f

        /**
         * The berth a route keeps off the shore where it can, in cells.
         *
         * Three cells is thirty units - one typical hull length (WorldScale), and a little more
         * than the widest turning circle on the map. That is the measure that matters: a route
         * laid closer than a hull's own turning circle to the beach is one no hull can follow.
         */
        const val BERTH_CELLS = 3

        /** What a cell of missing berth adds to the cost of a step. A quarter means a route will
         *  go up to half again as far to get out of the shallows, and no further - enough to
         *  prefer open water, not enough to refuse a strait that is the only way through. */
        const val COAST_PENALTY_PER_CELL = 0.25f

        /** Berth a straight-line shortcut must keep for the whole of its length - see
         *  [clearRun]. Lower than [BERTH_CELLS] on purpose: the route is already laid to prefer
         *  open water, and this only has to be enough that cutting between two of its waypoints
         *  cannot shave a headland. */
        const val SHORTCUT_BERTH_CELLS = 2

        /** Cells of clearance worth counting. Beyond this it is simply open ocean, and the sweep
         *  can stop rather than label the whole Caribbean. */
        const val MAX_TRACKED_CLEARANCE = 8

        /** Marks a cell the clearance sweep has not reached yet. */
        const val UNVISITED_CLEARANCE: Byte = -1
        /** Cells a single search may expand before giving up. Generous enough for any route that
         *  exists across the whole map, tight enough that an unreachable goal costs a moment
         *  rather than a survey of every square of water in the Caribbean. */
        const val MAX_EXPANSIONS = 60_000
    }
}
