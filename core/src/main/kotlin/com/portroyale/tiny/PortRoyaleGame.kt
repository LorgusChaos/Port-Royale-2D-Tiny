package com.portroyale.tiny

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Buttons
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.portroyale.tiny.world.MapProjection
import com.portroyale.tiny.world.Ports
import com.portroyale.tiny.world.WorldMap
import kotlin.math.cos
import kotlin.math.sin

private const val SHIP_SPEED = 260f
private const val ARRIVE_THRESHOLD = 14f
private const val PORT_CLICK_RADIUS = 24f

private enum class NavMode { MANUAL, AUTOPILOT }

private class ResolvedPort(val port: Ports.Port, val worldPos: Vector2, val approachPoint: Vector2)

/**
 * Background is the real 1732 Herman Moll "West-Indies" map; collision/pathfinding grid is
 * derived from real coastline lat/lon (invisible, aligned to the map via MapProjection).
 * Clicking a port OR open water sets an autopilot course (A* around islands); any manual
 * input (WASD/arrows) cancels autopilot and takes back direct control immediately.
 */
class PortRoyaleGame : ApplicationAdapter() {

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var mapTexture: Texture
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var worldMap: WorldMap
    private lateinit var resolvedPorts: List<ResolvedPort>

    private val shipPosition = Vector2()
    private var shipHeadingDeg = 0f

    private var navMode = NavMode.MANUAL
    private var path: List<Vector2> = emptyList()
    private var pathIndex = 0

    private val touchPoint = Vector2()

    override fun create() {
        shapeRenderer = ShapeRenderer()
        batch = SpriteBatch()

        val mapPixmap = Pixmap(Gdx.files.internal("maps/caribbean_relief.png"))
        mapTexture = Texture(mapPixmap)

        camera = OrthographicCamera()
        viewport = FitViewport(MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, camera)
        camera.position.set(MapProjection.MAP_WIDTH / 2f, MapProjection.MAP_HEIGHT / 2f, 0f)

        worldMap = WorldMap(mapPixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
        mapPixmap.dispose()

        resolvedPorts = Ports.ALL.map { port ->
            val pos = MapProjection.project(port.lat, port.lon)
            val approach = worldMap.findNearestWater(pos.x, pos.y) ?: pos
            ResolvedPort(port, pos, approach)
        }

        val start = resolvedPorts.firstOrNull { it.port.name == "Port Royal" }?.approachPoint
            ?: Vector2(MapProjection.MAP_WIDTH / 2f, MapProjection.MAP_HEIGHT / 2f)
        shipPosition.set(start)
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
    }

    override fun render() {
        handleInput()
        updateAutopilot(Gdx.graphics.deltaTime)

        Gdx.gl.glClearColor(0.08f, 0.07f, 0.06f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()

        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.draw(mapTexture, 0f, 0f, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        batch.end()

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        drawPorts()
        drawShipPlaceholder()
        shapeRenderer.end()

        if (navMode == NavMode.AUTOPILOT && path.isNotEmpty()) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            drawPath()
            shapeRenderer.end()
        }
    }

    private fun handleInput() {
        var moveX = 0f
        var moveY = 0f
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) moveY += 1f
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) moveY -= 1f
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) moveX -= 1f
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) moveX += 1f

        if (moveX != 0f || moveY != 0f) {
            navMode = NavMode.MANUAL
            path = emptyList()
            val len = Vector2(moveX, moveY).len()
            moveX /= len
            moveY /= len
            val delta = Gdx.graphics.deltaTime
            tryMove(moveX * SHIP_SPEED * delta, moveY * SHIP_SPEED * delta)
            shipHeadingDeg = MathUtils.atan2(moveY, moveX) * MathUtils.radDeg
        }

        if (Gdx.input.isButtonJustPressed(Buttons.LEFT)) {
            touchPoint.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            viewport.unproject(touchPoint)

            val nearestPort = resolvedPorts.minByOrNull { it.worldPos.dst(touchPoint) }
            val destination: Vector2? = when {
                nearestPort != null && nearestPort.worldPos.dst(touchPoint) <= PORT_CLICK_RADIUS ->
                    nearestPort.approachPoint
                worldMap.isWater(touchPoint.x, touchPoint.y) -> touchPoint.cpy()
                else -> null
            }

            if (destination != null) {
                val newPath = worldMap.findPath(shipPosition, destination)
                if (!newPath.isNullOrEmpty()) {
                    path = newPath
                    pathIndex = 0
                    navMode = NavMode.AUTOPILOT
                }
            }
        }
    }

    private fun updateAutopilot(delta: Float) {
        if (navMode != NavMode.AUTOPILOT || path.isEmpty()) return

        val target = path[pathIndex]
        val dx = target.x - shipPosition.x
        val dy = target.y - shipPosition.y
        val dist = Vector2(dx, dy).len()

        if (dist < ARRIVE_THRESHOLD) {
            pathIndex++
            if (pathIndex >= path.size) {
                navMode = NavMode.MANUAL
                path = emptyList()
            }
            return
        }

        val nx = dx / dist
        val ny = dy / dist
        tryMove(nx * SHIP_SPEED * delta, ny * SHIP_SPEED * delta)
        shipHeadingDeg = MathUtils.atan2(ny, nx) * MathUtils.radDeg
    }

    private fun tryMove(dx: Float, dy: Float) {
        val newX = shipPosition.x + dx
        val newY = shipPosition.y + dy
        when {
            worldMap.isWater(newX, newY) -> shipPosition.set(newX, newY)
            worldMap.isWater(newX, shipPosition.y) -> shipPosition.x = newX
            worldMap.isWater(shipPosition.x, newY) -> shipPosition.y = newY
        }
    }

    private fun drawPorts() {
        shapeRenderer.color = Color.valueOf("A61C1C")
        for (resolved in resolvedPorts) {
            shapeRenderer.circle(resolved.worldPos.x, resolved.worldPos.y, 7f, 12)
        }
    }

    private fun drawPath() {
        shapeRenderer.color = Color.valueOf("2B2B2B")
        var prevX = shipPosition.x
        var prevY = shipPosition.y
        for (point in path) {
            shapeRenderer.line(prevX, prevY, point.x, point.y)
            prevX = point.x
            prevY = point.y
        }
    }

    private fun drawShipPlaceholder() {
        val angleRad = shipHeadingDeg * MathUtils.degRad
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        fun rotated(localX: Float, localY: Float): Vector2 {
            val rx = localX * cosA - localY * sinA
            val ry = localX * sinA + localY * cosA
            return Vector2(shipPosition.x + rx, shipPosition.y + ry)
        }

        val nose = rotated(22f, 0f)
        val sternLeft = rotated(-14f, 10f)
        val sternRight = rotated(-14f, -10f)

        shapeRenderer.color = Color.valueOf("E8A33D")
        shapeRenderer.triangle(nose.x, nose.y, sternLeft.x, sternLeft.y, sternRight.x, sternRight.y)
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        mapTexture.dispose()
    }
}
