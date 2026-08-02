package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import java.util.PriorityQueue

data class GridPos(val gx: Int, val gy: Int)

/**
 * Water/land is sampled directly from the map image's own pixels (blue channel >= green
 * channel reads as water for this relief map's palette), so collision can never drift out of
 * sync with what's actually drawn - no hand-authored coastline polygons to maintain. World units
 * don't have to equal image pixels 1:1 (e.g. MapProjection's world size can be a multiple of the
 * source image's actual resolution, to make distances feel bigger without re-sourcing art) -
 * pixelScaleX/Y convert a world coordinate to the right source pixel either way.
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

    private fun isWaterColor(rgba8888: Int): Boolean {
        val g = (rgba8888 ushr 16) and 0xFF
        val b = (rgba8888 ushr 8) and 0xFF
        return b >= g
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

    fun findPath(startWorld: Vector2, endWorld: Vector2): List<Vector2>? {
        val start = worldToGrid(startWorld.x, startWorld.y)
        val goal = worldToGrid(endWorld.x, endWorld.y)
        if (!isWalkableCell(goal.gx, goal.gy) || !isWalkableCell(start.gx, start.gy)) return null
        if (start == goal) return listOf(endWorld.cpy())

        val open = PriorityQueue<Node>(compareBy { it.f })
        val cameFrom = HashMap<GridPos, GridPos>()
        val gScore = HashMap<GridPos, Float>()
        val visited = HashSet<GridPos>()
        gScore[start] = 0f
        open.add(Node(start, 0f, heuristic(start, goal)))

        val dirs = arrayOf(
            0 to 1, 1 to 0, 0 to -1, -1 to 0,
            1 to 1, 1 to -1, -1 to 1, -1 to -1
        )

        while (open.isNotEmpty()) {
            val current = open.poll().pos
            if (current == goal) {
                val worldPath = reconstructPath(cameFrom, current).map { gridToWorld(it) }.toMutableList()
                if (worldPath.isNotEmpty()) {
                    worldPath[worldPath.size - 1] = endWorld.cpy()
                }
                return worldPath
            }
            if (!visited.add(current)) continue

            for ((dx, dy) in dirs) {
                val neighbor = GridPos(current.gx + dx, current.gy + dy)
                if (!isWalkableCell(neighbor.gx, neighbor.gy)) continue
                if (dx != 0 && dy != 0) {
                    if (!isWalkableCell(current.gx + dx, current.gy) || !isWalkableCell(current.gx, current.gy + dy)) continue
                }
                val stepCost = if (dx != 0 && dy != 0) 1.41421f else 1f
                val tentativeG = (gScore[current] ?: Float.MAX_VALUE) + stepCost
                if (tentativeG < (gScore[neighbor] ?: Float.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeG
                    open.add(Node(neighbor, tentativeG, tentativeG + heuristic(neighbor, goal)))
                }
            }
        }
        return null
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

    private fun heuristic(a: GridPos, b: GridPos): Float {
        val dx = (a.gx - b.gx).toFloat()
        val dy = (a.gy - b.gy).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun reconstructPath(cameFrom: Map<GridPos, GridPos>, end: GridPos): List<GridPos> {
        val path = mutableListOf(end)
        var current = end
        while (cameFrom.containsKey(current)) {
            current = cameFrom.getValue(current)
            path.add(current)
        }
        path.reverse()
        return path
    }

    private data class Node(val pos: GridPos, val g: Float, val f: Float)
}
