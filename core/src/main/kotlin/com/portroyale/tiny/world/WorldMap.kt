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
            if (currentIndex == goalIndex) return reconstruct(currentIndex, endWorld)
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
                val tentative = currentG + if (dx != 0 && dy != 0) DIAGONAL_COST else 1f
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

    private fun reconstruct(goalIndex: Int, endWorld: Vector2): List<Vector2> {
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
        return result
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
        /** Cells a single search may expand before giving up. Generous enough for any route that
         *  exists across the whole map, tight enough that an unreachable goal costs a moment
         *  rather than a survey of every square of water in the Caribbean. */
        const val MAX_EXPANSIONS = 60_000
    }
}
