package com.portroyale.tiny

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Buttons
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.combat.CombatState
import com.portroyale.tiny.combat.Enemies
import com.portroyale.tiny.combat.ShipCombatant
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.ui.CombatUI
import com.portroyale.tiny.ui.PortMarketUI
import com.portroyale.tiny.world.MapProjection
import com.portroyale.tiny.world.Ports
import com.portroyale.tiny.world.WorldMap
import kotlin.random.Random

private const val ARRIVE_THRESHOLD = 14f
private const val PORT_CLICK_RADIUS = 45f
private const val BASE_SHIP_SPRITE_WIDTH = 26f
private const val ENCOUNTER_CHECK_INTERVAL = 4f
private const val ENCOUNTER_CHANCE = 0.15f
private const val DEFEAT_GOLD_PENALTY = 0.7f
private const val REJECTED_CLICK_MARKER_DURATION = 0.8f
private const val REJECTED_CLICK_MARKER_SIZE = 10f
private const val MIN_ZOOM = 0.15f
private const val MAX_ZOOM = 1f
private const val ZOOM_STEP = 0.1f
private const val EDGE_PAN_MARGIN = 40f
private const val PAN_SPEED = 900f
private const val MINIMAP_WIDTH = 240f
private const val MINIMAP_HEIGHT = 161f
private const val MINIMAP_MARGIN = 16f

private enum class NavMode { MANUAL, AUTOPILOT }
private enum class GameMode { SAILING, IN_PORT, COMBAT }

private class ResolvedPort(val port: Ports.Port, val worldPos: Vector2, val approachPoint: Vector2)

/**
 * Background is a real-world relief map; collision/pathfinding grid is sampled directly from the
 * map image's own pixels (aligned via MapProjection). Clicking a port OR open water sets an
 * autopilot course (A* around islands); any manual input (WASD/arrows) cancels autopilot and
 * takes back direct control immediately. Arriving at a port via autopilot opens a market/shipyard
 * screen. Ship speed/cargo/combat stats all come from the player's currently owned ShipType.
 * Clicking land is rejected silently except for a brief red X marker at the click point. Mouse
 * wheel zooms; the camera pans when the cursor nears a screen edge while sailing, clamped to the
 * map bounds; a minimap in the top-right corner shows the current view as a rectangle.
 */
class PortRoyaleGame : ApplicationAdapter() {

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var portLabelFont: BitmapFont
    private lateinit var mapTexture: Texture
    private lateinit var shipTextures: Map<String, Texture>
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var minimapCamera: OrthographicCamera
    private lateinit var worldMap: WorldMap
    private lateinit var resolvedPorts: List<ResolvedPort>
    private lateinit var portMarketUI: PortMarketUI
    private lateinit var combatUI: CombatUI

    private val player = Player()
    private var gameMode = GameMode.SAILING
    private var encounterTimer = 0f

    private val shipPosition = Vector2()
    private var shipHeadingDeg = 0f

    private var navMode = NavMode.MANUAL
    private var path: List<Vector2> = emptyList()
    private var pathIndex = 0
    private var destinationPort: ResolvedPort? = null

    private val touchPoint = Vector2()
    private val rejectedClickPoint = Vector2()
    private var rejectedClickTimer = 0f

    private val sailingInputProcessor = object : InputAdapter() {
        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            zoomBy(amountY)
            return true
        }
    }

    override fun create() {
        shapeRenderer = ShapeRenderer()
        batch = SpriteBatch()
        portLabelFont = BitmapFont()
        portLabelFont.color = Color.WHITE
        portLabelFont.data.setScale(1.8f)

        val mapPixmap = Pixmap(Gdx.files.internal("maps/caribbean_relief.png"))
        mapTexture = Texture(mapPixmap)
        shipTextures = ShipTypes.ALL.map { it.spritePath }.distinct()
            .associateWith { Texture(Gdx.files.internal(it)) }

        camera = OrthographicCamera()
        viewport = FitViewport(MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, camera)
        camera.position.set(MapProjection.MAP_WIDTH / 2f, MapProjection.MAP_HEIGHT / 2f, 0f)

        minimapCamera = OrthographicCamera()
        minimapCamera.setToOrtho(false, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        minimapCamera.update()

        worldMap = WorldMap(mapPixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
        mapPixmap.dispose()

        resolvedPorts = Ports.ALL.map { port ->
            val pos = MapProjection.project(port.lat, port.lon)
            val approach = worldMap.findNearestWater(pos.x, pos.y) ?: pos
            ResolvedPort(port, pos, approach)
        }

        portMarketUI = PortMarketUI(player, onSetSail = ::leavePort)
        combatUI = CombatUI(onCombatEnd = ::finishCombat)

        val start = resolvedPorts.firstOrNull { it.port.name == "Port Royal" }?.approachPoint
            ?: Vector2(MapProjection.MAP_WIDTH / 2f, MapProjection.MAP_HEIGHT / 2f)
        shipPosition.set(start)

        Gdx.input.inputProcessor = sailingInputProcessor
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        portMarketUI.resize(width, height)
        combatUI.resize(width, height)
    }

    override fun render() {
        // Re-apply every frame: the GL viewport rectangle (letterbox bars) otherwise doesn't
        // reliably stick between frames, which silently desyncs rendering from click unprojection.
        viewport.apply()

        if (gameMode == GameMode.SAILING) {
            handleInput()
            updateAutopilot(Gdx.graphics.deltaTime)
            updateEncounters(Gdx.graphics.deltaTime)
            updateCameraPan(Gdx.graphics.deltaTime)
        }
        if (rejectedClickTimer > 0f) {
            rejectedClickTimer -= Gdx.graphics.deltaTime
        }

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
        shapeRenderer.end()

        batch.begin()
        drawShip()
        drawPortLabels()
        batch.end()

        val showPath = gameMode == GameMode.SAILING && navMode == NavMode.AUTOPILOT && path.isNotEmpty()
        val showRejectedMarker = rejectedClickTimer > 0f
        if (showPath || showRejectedMarker) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            if (showPath) drawPath()
            if (showRejectedMarker) drawRejectedClickMarker()
            shapeRenderer.end()
        }

        drawMinimap()

        if (gameMode == GameMode.IN_PORT) {
            portMarketUI.render(Gdx.graphics.deltaTime)
        }

        if (gameMode == GameMode.COMBAT) {
            combatUI.render(Gdx.graphics.deltaTime)
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
            destinationPort = null
            val len = Vector2(moveX, moveY).len()
            moveX /= len
            moveY /= len
            val delta = Gdx.graphics.deltaTime
            val speed = player.ship.speed
            tryMove(moveX * speed * delta, moveY * speed * delta)
            shipHeadingDeg = MathUtils.atan2(moveY, moveX) * MathUtils.radDeg
        }

        if (Gdx.input.isButtonJustPressed(Buttons.LEFT)) {
            touchPoint.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            viewport.unproject(touchPoint)
            val isWaterHere = worldMap.isWater(touchPoint.x, touchPoint.y)

            val nearestPort = resolvedPorts.minByOrNull { it.worldPos.dst(touchPoint) }
            val targetPort = if (nearestPort != null && nearestPort.worldPos.dst(touchPoint) <= PORT_CLICK_RADIUS) {
                nearestPort
            } else {
                null
            }
            val destination: Vector2? = when {
                targetPort != null -> targetPort.approachPoint
                isWaterHere -> touchPoint.cpy()
                else -> null
            }

            if (destination != null) {
                val newPath = worldMap.findPath(shipPosition, destination)
                if (!newPath.isNullOrEmpty()) {
                    path = newPath
                    pathIndex = 0
                    navMode = NavMode.AUTOPILOT
                    destinationPort = targetPort
                } else {
                    markRejectedClick()
                }
            } else {
                markRejectedClick()
            }
        }
    }

    private fun markRejectedClick() {
        rejectedClickPoint.set(touchPoint)
        rejectedClickTimer = REJECTED_CLICK_MARKER_DURATION
    }

    private fun zoomBy(amount: Float) {
        camera.zoom = (camera.zoom + amount * ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)
        clampCamera()
    }

    private fun updateCameraPan(delta: Float) {
        val mouseX = Gdx.input.x
        val mouseY = Gdx.input.y
        val w = Gdx.graphics.width
        val h = Gdx.graphics.height

        var panX = 0f
        var panY = 0f
        if (mouseX < EDGE_PAN_MARGIN) panX -= 1f
        if (mouseX > w - EDGE_PAN_MARGIN) panX += 1f
        if (mouseY < EDGE_PAN_MARGIN) panY += 1f
        if (mouseY > h - EDGE_PAN_MARGIN) panY -= 1f

        if (panX == 0f && panY == 0f) return

        camera.position.x += panX * PAN_SPEED * camera.zoom * delta
        camera.position.y += panY * PAN_SPEED * camera.zoom * delta
        clampCamera()
    }

    private fun clampCamera() {
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f

        camera.position.x = if (halfW * 2f <= MapProjection.MAP_WIDTH) {
            camera.position.x.coerceIn(halfW, MapProjection.MAP_WIDTH - halfW)
        } else {
            MapProjection.MAP_WIDTH / 2f
        }
        camera.position.y = if (halfH * 2f <= MapProjection.MAP_HEIGHT) {
            camera.position.y.coerceIn(halfH, MapProjection.MAP_HEIGHT - halfH)
        } else {
            MapProjection.MAP_HEIGHT / 2f
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
                destinationPort?.let { arriveAtPort(it) }
                destinationPort = null
            }
            return
        }

        val nx = dx / dist
        val ny = dy / dist
        val speed = player.ship.speed
        tryMove(nx * speed * delta, ny * speed * delta)
        shipHeadingDeg = MathUtils.atan2(ny, nx) * MathUtils.radDeg
    }

    private fun arriveAtPort(port: ResolvedPort) {
        gameMode = GameMode.IN_PORT
        portMarketUI.open(port.port.name)
        Gdx.input.inputProcessor = portMarketUI.stage
    }

    private fun leavePort() {
        gameMode = GameMode.SAILING
        Gdx.input.inputProcessor = sailingInputProcessor
    }

    private fun updateEncounters(delta: Float) {
        encounterTimer += delta
        if (encounterTimer < ENCOUNTER_CHECK_INTERVAL) return
        encounterTimer = 0f
        if (Random.nextFloat() < ENCOUNTER_CHANCE) {
            startCombat()
        }
    }

    private fun startCombat() {
        gameMode = GameMode.COMBAT
        val playerStats = ShipCombatant(
            name = player.ship.name,
            maxHull = player.ship.maxHull,
            minDamage = player.ship.minDamage,
            maxDamage = player.ship.maxDamage
        )
        combatUI.open(CombatState(player, playerStats, Enemies.PIRATE_SLOOP))
        Gdx.input.inputProcessor = combatUI.stage
    }

    private fun finishCombat(outcome: CombatOutcome) {
        if (outcome == CombatOutcome.DEFEAT) {
            player.gold = (player.gold * DEFEAT_GOLD_PENALTY).toInt()
            for (good in Good.entries) {
                player.cargo[good] = (player.cargo[good] ?: 0) / 2
            }
        }
        gameMode = GameMode.SAILING
        Gdx.input.inputProcessor = sailingInputProcessor
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
            shapeRenderer.circle(resolved.worldPos.x, resolved.worldPos.y, 9f, 16)
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

    private fun drawPortLabels() {
        for (resolved in resolvedPorts) {
            portLabelFont.draw(batch, resolved.port.name, resolved.worldPos.x + 14f, resolved.worldPos.y + 8f)
        }
    }

    private fun drawRejectedClickMarker() {
        shapeRenderer.color = Color.RED
        val x = rejectedClickPoint.x
        val y = rejectedClickPoint.y
        val s = REJECTED_CLICK_MARKER_SIZE
        shapeRenderer.line(x - s, y - s, x + s, y + s)
        shapeRenderer.line(x - s, y + s, x + s, y - s)
    }

    private fun drawShip() {
        val shipType = player.ship
        val texture = shipTextures.getValue(shipType.spritePath)
        val width = BASE_SHIP_SPRITE_WIDTH * shipType.spriteScale
        val height = width * (texture.height.toFloat() / texture.width.toFloat())

        batch.color = shipType.tint
        batch.draw(
            texture,
            shipPosition.x - width / 2f, shipPosition.y - height / 2f,
            width / 2f, height / 2f,
            width, height,
            1f, 1f,
            shipHeadingDeg - 90f,
            0, 0, texture.width, texture.height,
            false, false
        )
        batch.color = Color.WHITE
    }

    private fun drawMinimap() {
        val mmX = (Gdx.graphics.width - MINIMAP_WIDTH - MINIMAP_MARGIN).toInt()
        val mmY = (Gdx.graphics.height - MINIMAP_HEIGHT - MINIMAP_MARGIN).toInt()

        Gdx.gl.glViewport(mmX, mmY, MINIMAP_WIDTH.toInt(), MINIMAP_HEIGHT.toInt())

        batch.projectionMatrix = minimapCamera.combined
        batch.begin()
        batch.draw(mapTexture, 0f, 0f, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        batch.end()

        shapeRenderer.projectionMatrix = minimapCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.valueOf("E8A33D")
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        shapeRenderer.rect(camera.position.x - halfW, camera.position.y - halfH, halfW * 2f, halfH * 2f)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(0f, 0f, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        shapeRenderer.end()

        viewport.apply()
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        portLabelFont.dispose()
        mapTexture.dispose()
        shipTextures.values.forEach { it.dispose() }
        portMarketUI.dispose()
        combatUI.dispose()
    }
}
