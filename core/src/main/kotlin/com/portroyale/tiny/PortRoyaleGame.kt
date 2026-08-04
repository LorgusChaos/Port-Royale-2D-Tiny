package com.portroyale.tiny

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Buttons
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
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
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.combat.CombatState
import com.portroyale.tiny.combat.ShipCombatant
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.REPUTATION_HOSTILE_THRESHOLD
import com.portroyale.tiny.factions.REPUTATION_MIN
import com.portroyale.tiny.save.AUTOSAVE_NAME
import com.portroyale.tiny.save.SaveManager
import com.portroyale.tiny.save.SettingsManager
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.world.AiShip
import com.portroyale.tiny.world.AiShipFactory
import com.portroyale.tiny.world.AiShipRole
import com.portroyale.tiny.ui.CombatUI
import com.portroyale.tiny.ui.EventToastUI
import com.portroyale.tiny.ui.FortressUI
import com.portroyale.tiny.ui.HudOverlayUI
import com.portroyale.tiny.ui.IconFactory
import com.portroyale.tiny.ui.LoadGameUI
import com.portroyale.tiny.ui.MainMenuUI
import com.portroyale.tiny.ui.MarketUI
import com.portroyale.tiny.ui.NewGameUI
import com.portroyale.tiny.ui.PortHubUI
import com.portroyale.tiny.ui.PropertyUI
import com.portroyale.tiny.ui.SaveGameUI
import com.portroyale.tiny.ui.SettingsUI
import com.portroyale.tiny.ui.ShipStatusUI
import com.portroyale.tiny.ui.ShipyardUI
import com.portroyale.tiny.ui.UiSkinFactory
import com.portroyale.tiny.ui.WarehouseUI
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.MapProjection
import com.portroyale.tiny.world.Ports
import com.portroyale.tiny.world.SeaEventPoint
import com.portroyale.tiny.world.SeaEventSlot
import com.portroyale.tiny.world.SeaEventType
import com.portroyale.tiny.world.WorldMap
import kotlin.math.sin

// Arrive-thresholds track WorldMap's pathfinding cellSize (10f, unrelated to MAP_WIDTH/HEIGHT),
// not the map's overall scale - too generous relative to cellSize and a ship "arrives" at a
// waypoint from far enough away to cut the next corner straight into land. Everything else below
// (click/engage/safe radii, zoom range) scales with the map, since those are about screen-space
// interaction feel rather than the pathfinding grid.
// These are target ON-SCREEN pixel sizes, not world units - drawMarketIndicators()/
// drawPortLabels() convert them to world units fresh every frame based on the current
// camera.zoom, so labels/icons read at the same physical size on screen no matter how far the
// camera is zoomed in or out (earlier attempts that scaled a *world-unit* constant by zoom kept
// the size merely consistent, not actually reasonable - it came out either illegibly tiny or
// screen-filling huge depending which zoom level the base constant happened to be tuned at).
private const val PORT_LABEL_TARGET_PX = 29.25f
private const val MARKET_ICON_TARGET_PX = PORT_LABEL_TARGET_PX * 1.3f
private const val MARKET_ICON_GAP_TARGET_PX = MARKET_ICON_TARGET_PX * 0.15f
private const val MARKET_ICON_OVERLAP_TARGET_PX = MARKET_ICON_TARGET_PX * 0.45f
private const val TOOLTIP_GAP_TARGET_PX = 6f
// The market-icon row is anchored to the port NAME label (not the port icon), positioned this
// many target-pixels below its bottom edge, starting at the label's own left edge.
private const val MARKET_ICON_LABEL_GAP_TARGET_PX = 5f
private const val PORT_LABEL_OFFSET_X = 18f
private const val PORT_LABEL_OFFSET_Y = 8f
private const val MINIMAP_BORDER_WIDTH = 4f
private const val MARKET_ICON_DIM_ALPHA = 0.35f
private const val DOCKED_GLOW_MIN_SIZE = 50f
private const val DOCKED_GLOW_MAX_SIZE = 90f
private const val DOCKED_GLOW_MIN_ALPHA = 0.5f
private const val DOCKED_GLOW_MAX_ALPHA = 1f
private const val DOCKED_GLOW_PULSE_SPEED = 2.4f // radians/sec - a full pulse cycle takes ~2.6s
private const val ARRIVE_THRESHOLD = 14f
private const val PORT_CLICK_RADIUS = 45f
// Deliberately much smaller than PORT_CLICK_RADIUS - a click has to land almost exactly on a
// ship to start a chase, so plotting a normal course near a passing ship doesn't accidentally
// hijack the click into hunt mode (which chases in a straight line with no obstacle avoidance).
private const val SHIP_HUNT_CLICK_RADIUS = 3f
private const val BASE_SHIP_SPRITE_WIDTH = 13f
private const val PORT_ICON_SIZE = 26f
private const val AI_SHIP_ENGAGE_RADIUS = 52f
private const val AI_SHIP_ARRIVE_THRESHOLD = 16f
// Deliberately larger than AI_SHIP_ENGAGE_RADIUS - an outmatched pirate needs to notice and start
// running before the player gets close enough to trigger combat, not exactly at the wire.
private const val PIRATE_FLEE_AWARENESS_RADIUS = 140f
private const val PIRATE_FLEE_SPEED_MULTIPLIER = 1.3f
private const val PORT_SAFE_RADIUS = 160f
private const val TRADE_SHIPS_PER_FACTION = 2
private const val MILITARY_SHIPS_PER_FACTION = 1
private const val PIRATE_SHIP_COUNT = 4
private const val REP_LOSS_TRADE = -8
private const val REP_LOSS_MILITARY = -15
private const val AUTOSAVE_INTERVAL_SECONDS = 600f
private const val SEA_EVENT_POOL_SIZE = 30
private const val SEA_EVENT_TRIGGER_RADIUS = 90f
private const val SEA_EVENT_RESPAWN_MIN = 45f
private const val SEA_EVENT_RESPAWN_MAX = 120f
private const val SEA_EVENT_SPAWN_ATTEMPTS = 200
private const val CHEST_GOLD_MIN = 200
private const val CHEST_GOLD_MAX = 800
private const val CHEST_GOODS_MIN_QTY = 1
private const val CHEST_GOODS_MAX_QTY = 3
private const val CHEST_FALLBACK_GOLD = 100 // paid instead of goods when the hold is full
private const val CASTAWAY_REWARD_MIN = 300
private const val CASTAWAY_REWARD_MAX = 700

private val BOTTLE_MESSAGES = listOf(
    "You find a bottle bobbing in the waves. A note inside reads: 'Fair winds to whoever finds this. - A forgotten sailor'",
    "A bottle of rum drifts past your hull. You drink it. Nothing else happens.",
    "An old bottle, corked and sealed with wax. Inside: a faded, illegible scrap of a map.",
    "You fish a bottle out of the water. The note inside has dissolved to an unreadable smear."
)
private const val REJECTED_CLICK_MARKER_DURATION = 0.8f
private const val REJECTED_CLICK_MARKER_SIZE = 20f
private const val MIN_ZOOM = 0.075f
private const val MAX_ZOOM = 1f
private const val ZOOM_STEP = 0.1f
private const val EDGE_PAN_MARGIN = 40f
private const val PAN_SPEED = 1800f
private const val MINIMAP_WIDTH = 240f
private const val MINIMAP_HEIGHT = 161f
private const val MINIMAP_MARGIN = 16f

private enum class NavMode { MANUAL, AUTOPILOT }
private enum class GameMode { SAILING, IN_PORT, COMBAT }
private enum class PortScreen { HUB, MARKET, SHIPYARD, SHIP_STATUS, FORTRESS, WAREHOUSE }
private enum class AppState { MENU, PLAYING }
private enum class MenuScreen { MAIN, NEW_GAME, LOAD, SAVE, SETTINGS }

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
    private var portLabelBaseLineHeight = 0f
    private lateinit var mapTexture: Texture
    private lateinit var shipTextures: Map<String, Texture>
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var minimapCamera: OrthographicCamera
    private lateinit var worldMap: WorldMap
    private lateinit var resolvedPorts: List<ResolvedPort>
    private lateinit var portHubUI: PortHubUI
    private lateinit var marketUI: MarketUI
    private lateinit var shipyardUI: ShipyardUI
    private lateinit var shipStatusUI: ShipStatusUI
    private lateinit var fortressUI: FortressUI
    private lateinit var warehouseUI: WarehouseUI
    private lateinit var combatUI: CombatUI
    private lateinit var mainMenuUI: MainMenuUI
    private lateinit var newGameUI: NewGameUI
    private lateinit var loadGameUI: LoadGameUI
    private lateinit var saveGameUI: SaveGameUI
    private lateinit var settingsUI: SettingsUI
    private lateinit var eventToastUI: EventToastUI
    private lateinit var hudOverlayUI: HudOverlayUI
    private lateinit var propertyUI: PropertyUI
    private var propertyOpen = false

    private val player = Player()
    private var gameMode = GameMode.SAILING
    private var portScreen = PortScreen.HUB
    private var currentPortName = ""

    private val aiShips = mutableListOf<AiShip>()
    private var huntedShip: AiShip? = null
    private var currentAiTarget: AiShip? = null

    // Fixed-size pool of invisible random-event locations scattered across the water; each slot
    // independently holds a live point or counts down to respawning one somewhere else (see
    // updateSeaEvents()). A castaway event sets passengerDestination until delivered.
    private val seaEventSlots = List(SEA_EVENT_POOL_SIZE) { SeaEventSlot() }
    private var passengerDestination: ResolvedPort? = null

    private var appState = AppState.MENU
    private var menuScreen = MenuScreen.MAIN
    private var gameActive = false
    private var captainName = ""
    private var portPulseTime = 0f
    private var autosaveTimer = 0f
    private var hoveredGood: Good? = null
    private val hoveredGoodPos = Vector2()
    // False from the moment the player docks until they actually set off again (manual input or
    // a course click) - "Set Sail" alone doesn't reveal the ship, it's still tied up at the
    // dock until the player does something that actually moves it.
    private var shipVisible = true

    private val shipPosition = Vector2()
    private var shipHeadingDeg = 0f

    private var navMode = NavMode.MANUAL
    private var path: List<Vector2> = emptyList()
    private var pathIndex = 0
    private var destinationPort: ResolvedPort? = null

    private val touchPoint = Vector2()
    private val rejectedClickPoint = Vector2()
    private var rejectedClickTimer = 0f

    private var rightDragActive = false
    private val lastDragScreenPos = Vector2()

    private val sailingInputProcessor = object : InputAdapter() {
        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            zoomBy(amountY)
            return true
        }
    }
    // The toast's close button needs Stage input handling too, but sailing itself is driven by
    // the raw InputAdapter above rather than a Stage - a multiplexer lets both listen at once
    // (the toast gets first crack, so its close button doesn't leak through as a ship click).
    private lateinit var sailingWithToastInputProcessor: InputMultiplexer

    override fun create() {
        shapeRenderer = ShapeRenderer()
        batch = SpriteBatch()
        // Same TTF as the rest of the game's UI (see UiSkinFactory), but its OWN independent
        // BitmapFont instance - map labels rescale every frame based on camera.zoom, and sharing
        // UiSkinFactory's font object would drag every Scene2D screen's text along with it.
        portLabelFont = UiSkinFactory.generateFont(42)
        portLabelFont.color = Color.BLACK
        portLabelBaseLineHeight = portLabelFont.data.lineHeight

        val mapPixmap = Pixmap(Gdx.files.internal("maps/caribbean_relief.png"))
        mapTexture = Texture(mapPixmap)
        shipTextures = (ShipTypes.ALL.map { it.spritePath } + AiShipFactory.ALL_SPRITES).distinct()
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
        spawnAiShips()
        seaEventSlots.forEach { spawnSeaEvent(it) }

        portHubUI = PortHubUI(
            player,
            onOpenMarket = ::openMarket,
            onOpenShipyard = ::openShipStatus,
            onOpenFortress = ::openFortress,
            onOpenWarehouse = ::openWarehouse,
            onSetSail = ::leavePort
        )
        marketUI = MarketUI(player, onBack = ::openHub)
        shipyardUI = ShipyardUI(player, onBack = ::openShipStatus)
        shipStatusUI = ShipStatusUI(player, onBack = ::openHub, onOpenShipyard = ::openShipyard)
        fortressUI = FortressUI(player, onBack = ::openHub)
        warehouseUI = WarehouseUI(player, onBack = ::openHub)
        combatUI = CombatUI(onCombatEnd = ::finishCombat)

        mainMenuUI = MainMenuUI(
            onContinue = ::resumeGame,
            onNewGame = ::openNewGameScreen,
            onLoad = ::openLoadScreen,
            onSave = ::openSaveScreen,
            onSettings = ::openSettingsScreen,
            onExit = { Gdx.app.exit() }
        )
        newGameUI = NewGameUI(onBegin = ::startNewGame, onBack = ::showMainMenu)
        loadGameUI = LoadGameUI(onLoad = ::loadGame, onBack = ::showMainMenu)
        saveGameUI = SaveGameUI(onSave = ::saveGame, onBack = ::showMainMenu)
        settingsUI = SettingsUI(onBack = ::showMainMenu)
        eventToastUI = EventToastUI()
        propertyUI = PropertyUI(player, onBack = ::closeProperty)
        hudOverlayUI = HudOverlayUI(onOpenProperty = ::openProperty)
        sailingWithToastInputProcessor = InputMultiplexer(eventToastUI.stage, hudOverlayUI.stage, sailingInputProcessor)

        val settings = SettingsManager.load()
        AudioManager.setMasterVolume(settings.masterVolume)
        AudioManager.setMuted(settings.muted)
        // Windowed mode is always left exactly as the user sized it - only a saved fullscreen
        // preference ever changes the window on startup.
        if (settings.fullscreen) {
            val mode = Gdx.graphics.displayModes
                .filter { it.width == settings.windowWidth && it.height == settings.windowHeight }
                .maxByOrNull { it.refreshRate }
            if (mode != null) Gdx.graphics.setFullscreenMode(mode)
        }

        shipPosition.set(defaultStartPosition())

        showMainMenu()
    }

    private fun defaultStartPosition(): Vector2 =
        resolvedPorts.firstOrNull { it.port.name == "Port Royal" }?.approachPoint
            ?: Vector2(MapProjection.MAP_WIDTH / 2f, MapProjection.MAP_HEIGHT / 2f)

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        portHubUI.resize(width, height)
        marketUI.resize(width, height)
        shipyardUI.resize(width, height)
        shipStatusUI.resize(width, height)
        fortressUI.resize(width, height)
        warehouseUI.resize(width, height)
        combatUI.resize(width, height)
        mainMenuUI.resize(width, height)
        newGameUI.resize(width, height)
        loadGameUI.resize(width, height)
        saveGameUI.resize(width, height)
        settingsUI.resize(width, height)
        eventToastUI.resize(width, height)
        hudOverlayUI.resize(width, height)
        propertyUI.resize(width, height)
    }

    override fun render() {
        // Re-apply every frame: the GL viewport rectangle (letterbox bars) otherwise doesn't
        // reliably stick between frames, which silently desyncs rendering from click unprojection.
        viewport.apply()

        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            handleEscape()
        }

        if (appState == AppState.PLAYING) {
            MarketState.update(Gdx.graphics.deltaTime)
            GameClock.update(Gdx.graphics.deltaTime)
            if (!shipVisible) {
                portPulseTime += Gdx.graphics.deltaTime
            }
            autosaveTimer += Gdx.graphics.deltaTime
            if (autosaveTimer >= AUTOSAVE_INTERVAL_SECONDS) {
                autosaveTimer = 0f
                saveGame(AUTOSAVE_NAME)
            }
            checkLoanOverdue()
            updateDebtHud()
        }

        // A sea-event toast freezes sailing entirely while it's up - ship movement, AI ships,
        // encounters, and further events all wait, so nothing can happen unseen behind it and a
        // click meant for its close button can't also be read as a course-setting click.
        if (appState == AppState.PLAYING && gameMode == GameMode.SAILING && !eventToastUI.isShowing && !propertyOpen) {
            handleInput()
            if (huntedShip != null) updateShipHunt(Gdx.graphics.deltaTime) else updateAutopilot(Gdx.graphics.deltaTime)
            updateAiShips(Gdx.graphics.deltaTime)
            checkAiEncounters()
            updateSeaEvents(Gdx.graphics.deltaTime)
            if (!updateDragPan()) updateCameraPan(Gdx.graphics.deltaTime)
            player.condition.accumulateFouling(Gdx.graphics.deltaTime)
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

        batch.begin()
        if (gameActive && !shipVisible) drawDockedGlow()
        drawPorts()
        if (gameActive) {
            drawAiShips()
            if (shipVisible) drawShip()
            drawMarketIndicators()
        }
        drawPortLabels()
        if (gameActive) drawHoveredGoodTooltip()
        batch.end()

        // The autopilot route line is intentionally not drawn - path itself still drives
        // movement in updateAutopilot(), this only skips the visual.
        val showRejectedMarker = rejectedClickTimer > 0f
        if (showRejectedMarker) {
            // drawMinimap() (later this frame, and every frame before this one) points
            // shapeRenderer at minimapCamera's projection - it has to be pointed back at the
            // main camera here or these draw in the wrong coordinate space entirely.
            shapeRenderer.projectionMatrix = camera.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            drawRejectedClickMarker()
            shapeRenderer.end()
        }

        if (gameActive) {
            drawMinimap()
            hudOverlayUI.render(Gdx.graphics.deltaTime)
        }
        // Not gated to GameMode.SAILING: a delivery toast can be triggered by arriveAtPort()
        // switching to GameMode.IN_PORT earlier in this same frame, before this draw pass runs.
        if (appState == AppState.PLAYING) eventToastUI.render(Gdx.graphics.deltaTime)
        if (propertyOpen) propertyUI.render(Gdx.graphics.deltaTime)

        if (appState == AppState.PLAYING) {
            when (gameMode) {
                GameMode.IN_PORT -> when (portScreen) {
                    PortScreen.HUB -> portHubUI.render(Gdx.graphics.deltaTime)
                    PortScreen.MARKET -> marketUI.render(Gdx.graphics.deltaTime)
                    PortScreen.SHIPYARD -> shipyardUI.render(Gdx.graphics.deltaTime)
                    PortScreen.SHIP_STATUS -> shipStatusUI.render(Gdx.graphics.deltaTime)
                    PortScreen.FORTRESS -> fortressUI.render(Gdx.graphics.deltaTime)
                    PortScreen.WAREHOUSE -> warehouseUI.render(Gdx.graphics.deltaTime)
                }
                GameMode.COMBAT -> combatUI.render(Gdx.graphics.deltaTime)
                GameMode.SAILING -> {}
            }
        } else {
            when (menuScreen) {
                MenuScreen.MAIN -> mainMenuUI.render(Gdx.graphics.deltaTime)
                MenuScreen.NEW_GAME -> newGameUI.render(Gdx.graphics.deltaTime)
                MenuScreen.LOAD -> loadGameUI.render(Gdx.graphics.deltaTime)
                MenuScreen.SAVE -> saveGameUI.render(Gdx.graphics.deltaTime)
                MenuScreen.SETTINGS -> settingsUI.render(Gdx.graphics.deltaTime)
            }
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
            huntedShip = null
            shipVisible = true
            val len = Vector2(moveX, moveY).len()
            moveX /= len
            moveY /= len
            val delta = Gdx.graphics.deltaTime
            val speed = player.effectiveSpeed
            tryMove(moveX * speed * delta, moveY * speed * delta)
            shipHeadingDeg = MathUtils.atan2(moveY, moveX) * MathUtils.radDeg
        }

        if (Gdx.input.isButtonJustPressed(Buttons.LEFT)) {
            touchPoint.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            viewport.unproject(touchPoint)

            val nearestShip = aiShips.minByOrNull { it.position.dst(touchPoint) }
            if (nearestShip != null && nearestShip.position.dst(touchPoint) <= SHIP_HUNT_CLICK_RADIUS) {
                huntedShip = nearestShip
                navMode = NavMode.MANUAL
                path = emptyList()
                destinationPort = null
                shipVisible = true
                return
            }

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
                    huntedShip = null
                    shipVisible = true
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

    /** Holding the right mouse button and dragging pans the camera 1:1 with the cursor - the
     *  point under the cursor stays under the cursor, regardless of zoom. Returns true while a
     *  drag is in progress, so the caller can skip edge-panning for that frame instead of
     *  fighting it. */
    private fun updateDragPan(): Boolean {
        if (Gdx.input.isButtonJustPressed(Buttons.RIGHT)) {
            rightDragActive = true
            lastDragScreenPos.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        }
        if (!Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            rightDragActive = false
            return false
        }
        if (!rightDragActive) return false

        val currX = Gdx.input.x.toFloat()
        val currY = Gdx.input.y.toFloat()
        val dx = currX - lastDragScreenPos.x
        val dy = currY - lastDragScreenPos.y
        if (dx != 0f || dy != 0f) {
            val worldPerPixelX = camera.viewportWidth * camera.zoom / viewport.screenWidth
            val worldPerPixelY = camera.viewportHeight * camera.zoom / viewport.screenHeight
            camera.position.x -= dx * worldPerPixelX
            camera.position.y += dy * worldPerPixelY
            clampCamera()
        }
        lastDragScreenPos.set(currX, currY)
        return true
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
        val speed = player.effectiveSpeed
        tryMove(nx * speed * delta, ny * speed * delta)
        shipHeadingDeg = MathUtils.atan2(ny, nx) * MathUtils.radDeg
    }

    /** Chasing a ship the player deliberately clicked on (see [handleInput]): a direct-line
     *  pursuit rather than an A* path, since re-pathing every frame toward a moving target would
     *  be expensive. Manual WASD input cancels the hunt exactly like it cancels autopilot. */
    private fun updateShipHunt(delta: Float) {
        val target = huntedShip
        if (target == null || !aiShips.contains(target)) {
            huntedShip = null
            return
        }
        val dx = target.position.x - shipPosition.x
        val dy = target.position.y - shipPosition.y
        val dist = Vector2(dx, dy).len()
        if (dist < AI_SHIP_ENGAGE_RADIUS) return // checkAiEncounters() starts combat this frame

        val nx = dx / dist
        val ny = dy / dist
        val speed = player.effectiveSpeed
        tryMove(nx * speed * delta, ny * speed * delta)
        shipHeadingDeg = MathUtils.atan2(ny, nx) * MathUtils.radDeg
    }

    private fun arriveAtPort(port: ResolvedPort) {
        if (port === passengerDestination) {
            val reward = MathUtils.random(CASTAWAY_REWARD_MIN, CASTAWAY_REWARD_MAX)
            player.gold += reward
            passengerDestination = null
            eventToastUI.show(
                "You deliver the castaway to ${port.port.name}. Grateful, they press ${reward}g into your hand before disappearing into the crowd.",
                IconFactory.coin
            )
        }
        gameMode = GameMode.IN_PORT
        currentPortName = port.port.name
        portPulseTime = 0f
        shipVisible = false
        AudioManager.pauseSailingMusic()
        AudioManager.playPortArrival()
        openHub()
    }

    private fun openHub() {
        portScreen = PortScreen.HUB
        portHubUI.open(currentPortName)
        Gdx.input.inputProcessor = portHubUI.stage
    }

    private fun openMarket() {
        portScreen = PortScreen.MARKET
        marketUI.open(currentPortName)
        Gdx.input.inputProcessor = marketUI.stage
    }

    private fun openShipyard() {
        portScreen = PortScreen.SHIPYARD
        shipyardUI.open()
        Gdx.input.inputProcessor = shipyardUI.stage
    }

    private fun openShipStatus() {
        portScreen = PortScreen.SHIP_STATUS
        shipStatusUI.open()
        Gdx.input.inputProcessor = shipStatusUI.stage
    }

    private fun openFortress() {
        portScreen = PortScreen.FORTRESS
        fortressUI.open(currentPortName)
        Gdx.input.inputProcessor = fortressUI.stage
    }

    private fun openWarehouse() {
        portScreen = PortScreen.WAREHOUSE
        warehouseUI.open(currentPortName)
        Gdx.input.inputProcessor = warehouseUI.stage
    }

    private fun openProperty() {
        propertyOpen = true
        propertyUI.open()
        Gdx.input.inputProcessor = propertyUI.stage
    }

    private fun closeProperty() {
        propertyOpen = false
        Gdx.input.inputProcessor = sailingWithToastInputProcessor
    }

    private fun leavePort() {
        gameMode = GameMode.SAILING
        Gdx.input.inputProcessor = sailingWithToastInputProcessor
        AudioManager.playSailingMusic()
    }

    /** Esc backs out one level of the paused menu, or resumes gameplay from the top-level menu,
     *  or pauses gameplay to open the top-level menu - whichever applies to the current state. */
    private fun handleEscape() {
        when {
            appState == AppState.PLAYING -> pauseGame()
            menuScreen != MenuScreen.MAIN -> showMainMenu()
            gameActive -> resumeGame()
            else -> {} // title screen, no game yet - Esc has nothing to do
        }
    }

    private fun pauseGame() {
        appState = AppState.MENU
        showMainMenu()
    }

    private fun showMainMenu() {
        menuScreen = MenuScreen.MAIN
        mainMenuUI.open(gameActive)
        Gdx.input.inputProcessor = mainMenuUI.stage
    }

    private fun resumeGame() {
        appState = AppState.PLAYING
        Gdx.input.inputProcessor = when (gameMode) {
            GameMode.SAILING -> sailingWithToastInputProcessor
            GameMode.IN_PORT -> when (portScreen) {
                PortScreen.HUB -> portHubUI.stage
                PortScreen.MARKET -> marketUI.stage
                PortScreen.SHIPYARD -> shipyardUI.stage
                PortScreen.SHIP_STATUS -> shipStatusUI.stage
                PortScreen.FORTRESS -> fortressUI.stage
                PortScreen.WAREHOUSE -> warehouseUI.stage
            }
            GameMode.COMBAT -> combatUI.stage
        }
    }

    private fun openNewGameScreen() {
        menuScreen = MenuScreen.NEW_GAME
        newGameUI.open()
        Gdx.input.inputProcessor = newGameUI.stage
    }

    private fun openLoadScreen() {
        menuScreen = MenuScreen.LOAD
        loadGameUI.open()
        Gdx.input.inputProcessor = loadGameUI.stage
    }

    private fun openSaveScreen() {
        menuScreen = MenuScreen.SAVE
        saveGameUI.open()
        Gdx.input.inputProcessor = saveGameUI.stage
    }

    private fun openSettingsScreen() {
        menuScreen = MenuScreen.SETTINGS
        settingsUI.open()
        Gdx.input.inputProcessor = settingsUI.stage
    }

    private fun beginPlaying() {
        shipHeadingDeg = 0f
        navMode = NavMode.MANUAL
        path = emptyList()
        destinationPort = null
        shipVisible = true
        gameActive = true
        appState = AppState.PLAYING
        gameMode = GameMode.SAILING
        autosaveTimer = 0f
        Gdx.input.inputProcessor = sailingWithToastInputProcessor
        AudioManager.playSailingMusic()
    }

    private fun startNewGame(name: String) {
        captainName = name.ifBlank { "Captain" }
        player.reset()
        MarketState.reset()
        GameClock.reset()
        shipPosition.set(defaultStartPosition())
        beginPlaying()
    }

    private fun loadGame(saveName: String) {
        val data = SaveManager.load(saveName) ?: return
        captainName = data.captainName
        val shipType = SaveManager.resolveShipType(data.shipTypeName)
        val cargo = SaveManager.resolveCargo(data.cargo)
        player.loadState(data.gold, shipType, cargo)
        player.condition.currentHull = data.currentHull
        player.condition.fouling = data.fouling
        player.condition.cannonLevel = data.cannonLevel
        player.condition.oarLevel = data.oarLevel
        player.condition.sailLevel = data.sailLevel
        SaveManager.resolveReputation(data.reputation).forEach { (faction, value) -> player.reputation[faction] = value }
        MarketState.restore(data.marketState)
        GameClock.restore(data.gameClockMinutes)
        player.warehouses.clear()
        player.warehouses.putAll(SaveManager.resolveWarehouses(data))
        player.loan = SaveManager.resolveLoan(data)
        shipPosition.set(data.shipX, data.shipY)
        beginPlaying()
    }

    private fun saveGame(saveName: String) {
        SaveManager.save(saveName, captainName, player, shipPosition)
    }

    /** One trade + one military ship per faction patrolling that faction's own ports, plus a
     *  handful of pirates wandering between any port - a rough population, not simulated economy. */
    private fun spawnAiShips() {
        for (faction in Faction.entries) {
            val homePorts = resolvedPorts.filter { it.port.faction == faction }
            if (homePorts.isEmpty()) continue
            repeat(TRADE_SHIPS_PER_FACTION) {
                aiShips.add(AiShipFactory.trade(faction, homePorts.random().approachPoint.cpy()))
            }
            repeat(MILITARY_SHIPS_PER_FACTION) {
                aiShips.add(AiShipFactory.military(faction, homePorts.random().approachPoint.cpy()))
            }
        }
        repeat(PIRATE_SHIP_COUNT) {
            aiShips.add(AiShipFactory.pirate(resolvedPorts.random().approachPoint.cpy()))
        }
    }

    private fun updateAiShips(delta: Float) {
        val myTier = playerShipTier()
        for (ship in aiShips) {
            if (ship.role == AiShipRole.PIRATE && ship.tier < myTier &&
                shipPosition.dst(ship.position) < PIRATE_FLEE_AWARENESS_RADIUS
            ) {
                if (fleeFromPlayer(ship, delta)) continue
            }
            if (ship.pathIndex >= ship.path.size) {
                retargetAiShip(ship)
                continue
            }
            val target = ship.path[ship.pathIndex]
            val dx = target.x - ship.position.x
            val dy = target.y - ship.position.y
            val dist = Vector2(dx, dy).len()
            if (dist < AI_SHIP_ARRIVE_THRESHOLD) {
                ship.pathIndex++
                continue
            }
            val nx = dx / dist
            val ny = dy / dist
            ship.position.x += nx * ship.speed * delta
            ship.position.y += ny * ship.speed * delta
            ship.headingDeg = MathUtils.atan2(ny, nx) * MathUtils.radDeg
        }
    }

    /** An outmatched pirate's panic move: sail directly away from the player, ignoring its
     *  current patrol path entirely (resumed automatically once it's out of
     *  [PIRATE_FLEE_AWARENESS_RADIUS], since neither pathIndex nor path was touched). No
     *  pathfinding involved, so it bails out (returns false, letting normal path movement run
     *  instead for this frame) rather than risk sailing straight onto land. */
    private fun fleeFromPlayer(ship: AiShip, delta: Float): Boolean {
        val dx = ship.position.x - shipPosition.x
        val dy = ship.position.y - shipPosition.y
        val dist = Vector2(dx, dy).len().coerceAtLeast(0.01f)
        val nx = dx / dist
        val ny = dy / dist
        val speed = ship.speed * PIRATE_FLEE_SPEED_MULTIPLIER
        val newX = ship.position.x + nx * speed * delta
        val newY = ship.position.y + ny * speed * delta
        if (!worldMap.isWater(newX, newY)) return false
        ship.position.set(newX, newY)
        ship.headingDeg = MathUtils.atan2(ny, nx) * MathUtils.radDeg
        return true
    }

    /** Single-port factions (France, Netherlands) would otherwise have nowhere else to "patrol"
     *  to and just sit stuck re-targeting their own home port forever - fall back to any port,
     *  and always exclude wherever the ship already is, so it actually goes somewhere new. */
    private fun retargetAiShip(ship: AiShip) {
        val sameFaction = if (ship.faction != null) resolvedPorts.filter { it.port.faction == ship.faction } else emptyList()
        val pool = if (sameFaction.size > 1) sameFaction else resolvedPorts
        val candidates = pool.filter { it.approachPoint.dst(ship.position) > AI_SHIP_ARRIVE_THRESHOLD * 2 }
            .ifEmpty { resolvedPorts }
        val destination = candidates.random().approachPoint
        val newPath = worldMap.findPath(ship.position, destination)
        if (!newPath.isNullOrEmpty()) {
            ship.path = newPath
            ship.pathIndex = 0
        }
    }

    /** A pirate only picks a fight it can plausibly win: it engages when its own tier is at
     *  least the player's current ship tier, and otherwise flees on sight instead (see the flee
     *  movement branch in updateAiShips()) - a Brigantine-tier pirate ducks a Galleon-tier
     *  player, and a Galleon-tier pirate has no reason to duck a Brigantine-tier player. A
     *  faction's military only turns hostile once the player's reputation with that faction has
     *  soured (or a loan with them has gone overdue - see checkLoanOverdue). Trade ships are
     *  never auto-hostile - the player has to deliberately click one to go after it (see
     *  [handleInput] / [huntedShip]). */
    private fun isHostile(ship: AiShip): Boolean {
        if (ship.role == AiShipRole.PIRATE) return ship.tier >= playerShipTier()
        if (ship.role != AiShipRole.MILITARY) return false
        val faction = ship.faction ?: return false
        return (player.reputation[faction] ?: 0) <= REPUTATION_HOSTILE_THRESHOLD
    }

    /** 1 (Sloop) through 5 (Galleon) matching ShipTypes.ALL's own order; a Wreck counts as the
     *  weakest possible tier (irrelevant in practice - checkAiEncounters already exempts it from
     *  every encounter, hostile or not). */
    private fun playerShipTier(): Int = (ShipTypes.ALL.indexOf(player.ship) + 1).coerceAtLeast(1)

    /** Ports (and the water right around them) are a safe harbor - no encounter can trigger
     *  there, so leaving a port never dumps the player straight into an ambush from whatever
     *  ship happened to be passing that waypoint at the time. */
    private fun isNearAnyPort(pos: Vector2): Boolean =
        resolvedPorts.any { it.worldPos.dst(pos) < PORT_SAFE_RADIUS }

    /** Once past its deadline, a loan only gets flagged overdue once: the debt doubles and that
     *  faction's reputation crashes to hostile (reusing the existing hostile-military-engagement
     *  check in checkAiEncounters/isHostile - no separate "at war" flag needed). */
    private fun checkLoanOverdue() {
        val loan = player.loan ?: return
        if (loan.overdue) return
        if (GameClock.totalGameMinutes < loan.deadlineGameMinutes) return
        loan.overdue = true
        loan.repayAmount *= 2
        player.reputation[loan.faction] = REPUTATION_MIN
        eventToastUI.show(
            "Your debt to the ${loan.faction.displayName} commandant is overdue! It has doubled " +
                "to ${loan.repayAmount}g, and their ships will now attack you on sight.",
            IconFactory.debt
        )
    }

    private fun updateDebtHud() {
        val loan = player.loan
        if (loan == null) {
            hudOverlayUI.setDebt(visible = false, fraction = 0f, countdownText = "")
            return
        }
        val totalTerm = loan.deadlineGameMinutes - loan.takenAtGameMinutes
        val elapsed = GameClock.totalGameMinutes - loan.takenAtGameMinutes
        val fraction = if (totalTerm > 0f) elapsed / totalTerm else 1f
        val countdownText = if (loan.overdue) {
            "OVERDUE"
        } else {
            val remainingGameMinutes = (loan.deadlineGameMinutes - GameClock.totalGameMinutes).coerceAtLeast(0f)
            val remainingRealMinutes = GameClock.gameMinutesToRealMinutes(remainingGameMinutes).toInt()
            val hours = remainingRealMinutes / 60
            val minutes = remainingRealMinutes % 60
            "${hours}h ${minutes}m"
        }
        hudOverlayUI.setDebt(visible = true, fraction = fraction, countdownText = countdownText)
    }

    private fun checkAiEncounters() {
        // A wrecked ship (see ShipTypes.WRECK) can't fight - 0 damage would just be a stalemate
        // or a guaranteed loss - so nothing ever engages it; it only exists to limp to a port.
        if (player.ship == ShipTypes.WRECK) return
        if (isNearAnyPort(shipPosition)) return
        for (ship in aiShips) {
            val engaging = isHostile(ship) || ship === huntedShip
            if (!engaging) continue
            if (shipPosition.dst(ship.position) < AI_SHIP_ENGAGE_RADIUS) {
                startAiCombat(ship)
                return
            }
        }
    }

    /** One tick of the fixed-size sea-event pool: live points check for contact with the player,
     *  spent points count down to respawning somewhere new. Deliberately not distance-culled to
     *  "nearby" points only - the pool is small (SEA_EVENT_POOL_SIZE), so checking all of it every
     *  frame is cheap. */
    private fun updateSeaEvents(delta: Float) {
        for (slot in seaEventSlots) {
            val point = slot.point
            if (point != null) {
                if (shipPosition.dst(point.position) < SEA_EVENT_TRIGGER_RADIUS) {
                    triggerSeaEvent(slot, point)
                }
            } else {
                slot.respawnTimer -= delta
                if (slot.respawnTimer <= 0f) spawnSeaEvent(slot)
            }
        }
    }

    private fun spawnSeaEvent(slot: SeaEventSlot) {
        var candidate: Vector2? = null
        var attempt = 0
        while (candidate == null && attempt < SEA_EVENT_SPAWN_ATTEMPTS) {
            val x = MathUtils.random(0f, MapProjection.MAP_WIDTH)
            val y = MathUtils.random(0f, MapProjection.MAP_HEIGHT)
            if (worldMap.isWater(x, y) && !isNearAnyPort(Vector2(x, y))) {
                candidate = Vector2(x, y)
            }
            attempt++
        }
        val position = candidate ?: run {
            // Water-starved random draw (rare) - retry on the next tick instead of this frame.
            slot.respawnTimer = 1f
            return
        }
        // A castaway is only offered while the player isn't already carrying one, so there's
        // never ambiguity about which port a "deliver the castaway" arrival refers to.
        val availableTypes = if (passengerDestination == null) {
            SeaEventType.entries
        } else {
            SeaEventType.entries.filter { it != SeaEventType.CASTAWAY }
        }
        slot.point = SeaEventPoint(position, availableTypes.random())
    }

    private fun triggerSeaEvent(slot: SeaEventSlot, point: SeaEventPoint) {
        when (point.type) {
            SeaEventType.BOTTLE -> eventToastUI.show(BOTTLE_MESSAGES.random(), IconFactory.bottle)
            SeaEventType.CHEST_GOLD -> {
                val amount = MathUtils.random(CHEST_GOLD_MIN, CHEST_GOLD_MAX)
                player.gold += amount
                eventToastUI.show("You spot a floating chest and haul it aboard - ${amount}g inside!", IconFactory.coin)
            }
            SeaEventType.CHEST_GOODS -> {
                if (player.cargoUsed >= player.cargoCapacity) {
                    player.gold += CHEST_FALLBACK_GOLD
                    eventToastUI.show(
                        "You spot a floating chest, but your hold is full - you salvage only ${CHEST_FALLBACK_GOLD}g worth of loose coin from it.",
                        IconFactory.coin
                    )
                } else {
                    val good = Good.entries.random()
                    val qty = MathUtils.random(CHEST_GOODS_MIN_QTY, CHEST_GOODS_MAX_QTY)
                    player.cargo[good] = (player.cargo[good] ?: 0) + qty
                    eventToastUI.show("A half-sunken crate yields $qty ${good.displayName}!", IconFactory.iconFor(good))
                }
            }
            SeaEventType.CASTAWAY -> {
                val destination = resolvedPorts.random()
                passengerDestination = destination
                eventToastUI.show(
                    "You pull a half-drowned sailor from the water. They beg you to take them to ${destination.port.name}.",
                    IconFactory.castaway
                )
            }
        }
        slot.point = null
        slot.respawnTimer = MathUtils.random(SEA_EVENT_RESPAWN_MIN, SEA_EVENT_RESPAWN_MAX)
    }

    private fun startAiCombat(ship: AiShip) {
        gameMode = GameMode.COMBAT
        currentAiTarget = ship
        huntedShip = null
        shipVisible = true
        AudioManager.playCombatMusic()
        val playerStats = ShipCombatant(
            name = player.ship.name,
            maxHull = player.effectiveMaxHull,
            minDamage = player.effectiveMinDamage,
            maxDamage = player.effectiveMaxDamage,
            spritePath = player.ship.spritePath
        )
        val enemyStats = ShipCombatant(
            name = ship.displayName,
            maxHull = ship.maxHull,
            minDamage = ship.minDamage,
            maxDamage = ship.maxDamage,
            spritePath = ship.spritePath
        )
        combatUI.open(CombatState(player, playerStats, enemyStats))
        Gdx.input.inputProcessor = combatUI.stage
    }

    private fun finishCombat(outcome: CombatOutcome) {
        val target = currentAiTarget
        when (outcome) {
            CombatOutcome.VICTORY -> if (target != null) {
                player.gold += target.goldReward
                target.faction?.let { faction ->
                    val loss = if (target.role == AiShipRole.MILITARY) REP_LOSS_MILITARY else REP_LOSS_TRADE
                    player.adjustReputation(faction, loss)
                }
                eventToastUI.show(
                    "Victory! You defeated the ${target.displayName} and claimed ${target.goldReward}g.",
                    IconFactory.coin
                )
                aiShips.remove(target)
                respawnReplacement(target)
            }
            CombatOutcome.DEFEAT -> {
                // Losing a fight is total: every coin and every unit of cargo on the ship is
                // gone (anything already tucked away in a port warehouse is untouched), and the
                // ship itself is wrecked down to ShipTypes.WRECK - just enough to reach a port,
                // never a fight. Full hull on the wreck so that "reach a port" is always possible.
                player.wreckShip()
                eventToastUI.show(
                    "Defeated! Your ship is wrecked, your cargo and gold are lost. " +
                        "You limp away in a battered wreck - just enough to reach a port.",
                    IconFactory.castaway
                )
            }
            CombatOutcome.FLED, CombatOutcome.ONGOING -> {}
        }
        currentAiTarget = null
        gameMode = GameMode.SAILING
        Gdx.input.inputProcessor = sailingWithToastInputProcessor
        AudioManager.stopCombatMusic()
        AudioManager.playSailingMusic()
    }

    /** Keeps the ambient population roughly constant - a sunk ship is replaced by a fresh one of
     *  the same role/faction rather than slowly depleting the world. */
    private fun respawnReplacement(defeated: AiShip) {
        val spot = if (defeated.faction != null) {
            resolvedPorts.filter { it.port.faction == defeated.faction }.randomOrNull()?.approachPoint
        } else {
            resolvedPorts.randomOrNull()?.approachPoint
        } ?: return
        val replacement = when (defeated.role) {
            AiShipRole.TRADE -> AiShipFactory.trade(defeated.faction!!, spot.cpy())
            AiShipRole.MILITARY -> AiShipFactory.military(defeated.faction!!, spot.cpy())
            AiShipRole.PIRATE -> AiShipFactory.pirate(spot.cpy())
        }
        aiShips.add(replacement)
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
        for (resolved in resolvedPorts) {
            batch.draw(
                IconFactory.port,
                resolved.worldPos.x - PORT_ICON_SIZE / 2f, resolved.worldPos.y - PORT_ICON_SIZE / 2f,
                PORT_ICON_SIZE, PORT_ICON_SIZE
            )
        }
    }

    /** A small row of goods icons under each port that's currently notably cheap (green frame)
     *  or notably expensive (red frame) there - MarketState.notableGoods already caps how many
     *  show at once, so this never needs to worry about clutter itself. */
    /** Draws each port's surplus/shortage row, dims an icon if the player's ship sprite is
     *  currently over it (so the ship stays readable), and tracks whether the cursor is
     *  hovering one so render() can draw a name tooltip on top of everything else afterward. */
    private fun drawMarketIndicators() {
        hoveredGood = null
        val mouseWorld = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        viewport.unproject(mouseWorld)

        // Icon SIZE is derived fresh from a target ON-SCREEN pixel size every frame (see
        // worldUnitsPerScreenPixel()), so icons read the same physical size regardless of zoom.
        // The row is anchored to the port NAME LABEL (same left edge, a small gap below its
        // bottom) rather than to the port icon, so it visibly reads as "belonging" to the label.
        val upp = worldUnitsPerScreenPixel()
        val iconSize = MARKET_ICON_TARGET_PX * upp
        val iconGap = MARKET_ICON_GAP_TARGET_PX * upp
        val overlapRadius = MARKET_ICON_OVERLAP_TARGET_PX * upp
        val rowGap = MARKET_ICON_LABEL_GAP_TARGET_PX * upp
        val labelHeight = portLabelWorldHeight()

        for (resolved in resolvedPorts) {
            val notable = MarketState.notableGoods(resolved.port.name)
            if (notable.isEmpty()) continue

            var x = resolved.worldPos.x + PORT_LABEL_OFFSET_X
            val y = resolved.worldPos.y + PORT_LABEL_OFFSET_Y - labelHeight - rowGap - iconSize

            for ((good, isSurplus) in notable) {
                val centerX = x + iconSize / 2f
                val centerY = y + iconSize / 2f
                val overlapsShip = gameMode == GameMode.SAILING &&
                    shipPosition.dst(centerX, centerY) < overlapRadius
                val alpha = if (overlapsShip) MARKET_ICON_DIM_ALPHA else 1f

                batch.color = Color(1f, 1f, 1f, alpha)
                val frame = if (isSurplus) IconFactory.surplusFrame else IconFactory.shortageFrame
                batch.draw(frame, x, y, iconSize, iconSize)
                val inset = iconSize * 0.15f
                batch.draw(IconFactory.iconFor(good), x + inset, y + inset, iconSize - inset * 2f, iconSize - inset * 2f)
                batch.color = Color.WHITE

                if (mouseWorld.x in x..(x + iconSize) && mouseWorld.y in y..(y + iconSize)) {
                    hoveredGood = good
                    hoveredGoodPos.set(x, y)
                }

                x += iconSize + iconGap
            }
        }
    }

    private fun drawHoveredGoodTooltip() {
        val good = hoveredGood ?: return
        val upp = worldUnitsPerScreenPixel()
        val iconSize = MARKET_ICON_TARGET_PX * upp
        portLabelFont.draw(batch, good.displayName, hoveredGoodPos.x, hoveredGoodPos.y + iconSize + TOOLTIP_GAP_TARGET_PX * upp)
    }

    /** World units per screen pixel at the current zoom - multiplying a target ON-SCREEN pixel
     *  size by this gives the world-space size that will render at exactly that many pixels,
     *  regardless of zoom. Derived from the same relationship FitViewport itself uses internally
     *  (visible world width = camera.viewportWidth * zoom, mapped onto viewport.screenWidth
     *  pixels), so it stays correct across window resizes too. */
    private fun worldUnitsPerScreenPixel(): Float =
        camera.viewportWidth * camera.zoom / viewport.screenWidth.toFloat()

    /** World-space height a port label renders at right now, for drawMarketIndicators() to stack
     *  the goods row underneath it. */
    private fun portLabelWorldHeight(): Float = PORT_LABEL_TARGET_PX * worldUnitsPerScreenPixel()

    private fun drawPortLabels() {
        // Font SCALE is derived from a target ON-SCREEN pixel text height every frame (see
        // worldUnitsPerScreenPixel()); the position offset from the port is deliberately left
        // un-scaled so the label keeps pace with the (also un-scaled) port icon's own on-screen
        // growth as you zoom in, instead of collapsing onto it.
        portLabelFont.data.setScale(portLabelWorldHeight() / portLabelBaseLineHeight)
        for (resolved in resolvedPorts) {
            portLabelFont.draw(
                batch,
                resolved.port.name,
                resolved.worldPos.x + PORT_LABEL_OFFSET_X,
                resolved.worldPos.y + PORT_LABEL_OFFSET_Y
            )
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

    /** The player's ship isn't drawn while docked (see render()) - this soft, slowly pulsing
     *  glow at the port they're in stands in for it, so glancing at the map behind an open port
     *  menu still shows where the ship actually is. */
    private fun drawDockedGlow() {
        val port = resolvedPorts.firstOrNull { it.port.name == currentPortName } ?: return
        val pulse = (sin(portPulseTime * DOCKED_GLOW_PULSE_SPEED) + 1f) / 2f
        val size = DOCKED_GLOW_MIN_SIZE + (DOCKED_GLOW_MAX_SIZE - DOCKED_GLOW_MIN_SIZE) * pulse
        val alpha = DOCKED_GLOW_MIN_ALPHA + (DOCKED_GLOW_MAX_ALPHA - DOCKED_GLOW_MIN_ALPHA) * pulse

        batch.color = Color(1f, 1f, 1f, alpha)
        batch.draw(
            IconFactory.dockedGlow,
            port.worldPos.x - size / 2f, port.worldPos.y - size / 2f,
            size, size
        )
        batch.color = Color.WHITE
    }

    private fun drawAiShips() {
        for (ship in aiShips) {
            val texture = shipTextures.getValue(ship.spritePath)
            val width = BASE_SHIP_SPRITE_WIDTH
            val height = width * (texture.height.toFloat() / texture.width.toFloat())

            batch.color = ship.tint
            batch.draw(
                texture,
                ship.position.x - width / 2f, ship.position.y - height / 2f,
                width / 2f, height / 2f,
                width, height,
                1f, 1f,
                // The ship2_* art's bow points toward the TOP of the source image (unlike the
                // old Kenney set, which was bow-down) - heading 0 (facing +X/east) needs -90.
                ship.headingDeg - 90f,
                0, 0, texture.width, texture.height,
                false, false
            )
        }
        batch.color = Color.WHITE
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
        val border = MINIMAP_BORDER_WIDTH.toInt()

        // Border frame: fill a viewport slightly larger than the minimap with the frame color
        // first, then narrow back to the real minimap rect and draw the map on top - leaves an
        // even `border`-pixel ring of frame color showing all the way around.
        Gdx.gl.glViewport(mmX - border, mmY - border, MINIMAP_WIDTH.toInt() + border * 2, MINIMAP_HEIGHT.toInt() + border * 2)
        shapeRenderer.projectionMatrix = minimapCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color.valueOf("C9A24A")
        shapeRenderer.rect(0f, 0f, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        shapeRenderer.end()

        Gdx.gl.glViewport(mmX, mmY, MINIMAP_WIDTH.toInt(), MINIMAP_HEIGHT.toInt())

        batch.projectionMatrix = minimapCamera.combined
        batch.begin()
        batch.draw(mapTexture, 0f, 0f, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        batch.end()

        shapeRenderer.projectionMatrix = minimapCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.RED
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        shapeRenderer.rect(camera.position.x - halfW, camera.position.y - halfH, halfW * 2f, halfH * 2f)
        shapeRenderer.end()

        viewport.apply()
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        portLabelFont.dispose()
        mapTexture.dispose()
        shipTextures.values.forEach { it.dispose() }
        portHubUI.dispose()
        marketUI.dispose()
        shipyardUI.dispose()
        shipStatusUI.dispose()
        fortressUI.dispose()
        warehouseUI.dispose()
        combatUI.dispose()
        mainMenuUI.dispose()
        newGameUI.dispose()
        loadGameUI.dispose()
        saveGameUI.dispose()
        settingsUI.dispose()
        eventToastUI.dispose()
        hudOverlayUI.dispose()
        propertyUI.dispose()
        AudioManager.dispose()
        IconFactory.dispose()
        UiSkinFactory.disposeSharedFont()
    }
}
