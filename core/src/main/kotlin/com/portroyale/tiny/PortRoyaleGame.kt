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
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.combat.CombatState
import com.portroyale.tiny.combat.ShipCombatant
import com.portroyale.tiny.diagnostics.DiagnosticsFiles
import com.portroyale.tiny.diagnostics.GameLog
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.diagnostics.ShotRequest
import com.portroyale.tiny.diagnostics.ShotScreen
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.ReputationTier
import com.portroyale.tiny.save.AUTOSAVE_NAME
import com.portroyale.tiny.save.LoadResult
import com.portroyale.tiny.save.SaveManager
import com.portroyale.tiny.save.WorldSave
import com.portroyale.tiny.save.SettingsManager
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.world.AiShip
import com.portroyale.tiny.world.AiShipFactory
import com.portroyale.tiny.world.Helm
import com.portroyale.tiny.ui.CaptainStatusUI
import com.portroyale.tiny.ui.CombatUI
import com.portroyale.tiny.ui.EventToastUI
import com.portroyale.tiny.ui.FleetDockUI
import com.portroyale.tiny.ui.FortressUI
import com.portroyale.tiny.ui.HudArt
import com.portroyale.tiny.ui.HudOverlayUI
import com.portroyale.tiny.ui.ShipStatus
import com.portroyale.tiny.ui.IconFactory
import com.portroyale.tiny.ui.LoadGameUI
import com.portroyale.tiny.ui.MainMenuUI
import com.portroyale.tiny.ui.MarketUI
import com.portroyale.tiny.ui.NewGameUI
import com.portroyale.tiny.ui.PortFrame
import com.portroyale.tiny.ui.PortHubUI
import com.portroyale.tiny.ui.PropertyUI
import com.portroyale.tiny.ui.SaveGameUI
import com.portroyale.tiny.ui.SettingsUI
import com.portroyale.tiny.ui.ShipArt
import com.portroyale.tiny.ui.ShipStatusUI
import com.portroyale.tiny.ui.ShipyardUI
import com.portroyale.tiny.ui.TavernUI
import com.portroyale.tiny.ui.UiSkinFactory
import com.portroyale.tiny.ui.WarehouseUI
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.GameSpeed
import com.portroyale.tiny.world.MapProjection
import com.portroyale.tiny.world.PirateSea
import com.portroyale.tiny.world.GameWorld
import com.portroyale.tiny.world.ResolvedPort
import com.portroyale.tiny.world.ToastIcon
import com.portroyale.tiny.world.WorldEvents
import com.portroyale.tiny.world.WorldMap
import com.portroyale.tiny.world.resolvePorts
import com.portroyale.tiny.world.BUSY_CORRIDOR_RADIUS
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
private const val MARKET_ICON_DIM_ALPHA = 0.35f
private const val DOCKED_GLOW_MIN_SIZE = 50f
private const val DOCKED_GLOW_MAX_SIZE = 90f
private const val DOCKED_GLOW_MIN_ALPHA = 0.5f
private const val DOCKED_GLOW_MAX_ALPHA = 1f
private const val DOCKED_GLOW_PULSE_SPEED = 2.4f // radians/sec - a full pulse cycle takes ~2.6s
private const val PORT_CLICK_RADIUS = 45f
// Deliberately much smaller than PORT_CLICK_RADIUS - a click has to land almost exactly on a
// ship to start a chase, so plotting a normal course near a passing ship doesn't accidentally
// hijack the click into hunt mode (which chases in a straight line with no obstacle avoidance).
private const val SHIP_HUNT_CLICK_RADIUS = 3f
// A fleet ship is the player's own property, a deliberate click target rather than something
// that needs protecting from accidental hijack the way SHIP_HUNT_CLICK_RADIUS does - larger than
// that on purpose.
private const val FLEET_SHIP_CLICK_RADIUS = 8f
private const val FLEET_SHIP_SPRITE_SCALE = 0.8f
private const val BASE_SHIP_SPRITE_WIDTH = 13f
/** How wide a harbour town is drawn on the map, in world units. Wider than the old marker's 26:
 *  a painted town needs the room to be recognisable, where a stone tower silhouette did not. */
private const val PORT_ICON_WIDTH = 44f
/** The town illustration's own proportions - drawing it square would squash it. */
private const val PORT_ICON_HEIGHT = PORT_ICON_WIDTH / 1.43f

/** Courses that may be laid in one drawn frame. At roughly half a millisecond each this is a few
 *  milliseconds of a sixteen-millisecond frame, whatever speed the world is running at. */
private const val ROUTES_PER_FRAME = 6
private val RAID_REGION_TINT = Color(0.45f, 0.10f, 0.10f, 0.22f)
private val RAID_LABEL_COLOR = Color(1f, 0.85f, 0.80f, 0.9f)
private const val RAID_LABEL_SCALE = 1.5f
private const val RADIAL_TEXTURE_SIZE = 128
/** Fraction of the radius given over to the soft edge. */
private const val RADIAL_EDGE_SOFTNESS = 0.28f
private const val SINK_MARK_SIZE = 90f
private val SINK_MARK_PIRATE = Color(1f, 0.85f, 0.45f, 0.55f)
private val SINK_MARK_NAVY = Color(1f, 0.45f, 0.40f, 0.55f)
private val PIRATE_SEA_FOG = Color(0.16f, 0.14f, 0.20f, 0.34f)
private val PIRATE_SEA_LABEL_COLOR = Color(0.90f, 0.87f, 0.80f, 0.85f)
private const val PIRATE_SEA_LABEL_SCALE = 2.2f
private const val AUTOSAVE_INTERVAL_SECONDS = 600f
/** Longest real frame the world is advanced by - see render(). */
private const val MAX_FRAME_SECONDS = 0.1f
private const val REJECTED_CLICK_MARKER_DURATION = 0.8f
private const val REJECTED_CLICK_MARKER_SIZE = 20f
/** One step further in than this used to exist; it put the camera closer than the art is drawn
 *  for. The ladder is now a whole number of ZOOM_STEPs, with no short final step onto a clamp. */
/** Ship markers on the chart, in minimap pixels. Big enough to find at a glance, small enough
 *  that a fleet of them does not cover the islands they are sailing between. */
private const val MINIMAP_DOT_RADIUS_PX = 3.2f
private val MINIMAP_DOT_RIM = Color.valueOf("1A120A")
private val MINIMAP_PLAYER_DOT: Color = Color.valueOf("E03A2F")
private val MINIMAP_FLEET_DOT: Color = Color.valueOf("E88A1E")

private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 1f
private const val ZOOM_STEP = 0.1f

/** Camera zoom snapped to whenever the player jumps to a fleet ship (see jumpToCaptain) - 90%
 *  of the way from fully zoomed out (MAX_ZOOM) toward fully zoomed in (MIN_ZOOM), a close zoom-in
 *  so the ship itself is the clear focus. Tune this one constant if 90% ends up too close/too
 *  far. */
private const val JUMP_TO_SHIP_ZOOM = MAX_ZOOM - 0.9f * (MAX_ZOOM - MIN_ZOOM)
private const val EDGE_PAN_MARGIN = 40f
private const val PAN_SPEED = 1800f

private enum class GameMode { SAILING, IN_PORT, COMBAT }
private enum class PortScreen { HUB, MARKET, SHIPYARD, SHIP_STATUS, FORTRESS, WAREHOUSE, TAVERN, DOCKS }
private enum class AppState { MENU, PLAYING }
private enum class MenuScreen { MAIN, NEW_GAME, LOAD, SAVE, SETTINGS }

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
class PortRoyaleGame(
    private val version: String = "dev",
    /** Set for a --shot launch: open one screen, save a screenshot, quit. See [ShotRequest]. */
    private val shot: ShotRequest? = null
) : ApplicationAdapter() {

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var fogTexture: Texture
    private lateinit var radialTexture: Texture
    private lateinit var portLabelFont: BitmapFont
    private var portLabelBaseLineHeight = 0f
    private lateinit var mapTexture: Texture
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
    private lateinit var tavernUI: TavernUI
    private lateinit var fleetDockUI: FleetDockUI
    private lateinit var combatUI: CombatUI
    private lateinit var mainMenuUI: MainMenuUI
    private lateinit var newGameUI: NewGameUI
    private lateinit var loadGameUI: LoadGameUI
    private lateinit var saveGameUI: SaveGameUI
    private lateinit var settingsUI: SettingsUI
    private lateinit var eventToastUI: EventToastUI
    private lateinit var hudOverlayUI: HudOverlayUI
    private val minimapMapRect = Rectangle()
    private lateinit var propertyUI: PropertyUI
    private var propertyOpen = false
    private lateinit var captainStatusUI: CaptainStatusUI
    private var captainStatusOpen = false

    private val player = Player()
    private var gameMode = GameMode.SAILING
    private var portScreen = PortScreen.HUB
    private var currentPortName = ""

    /** The world at sea - see [GameWorld]. Built in create(), once the map and ports exist. */
    private lateinit var world: GameWorld
    /** How the world speaks to the player: it names the icon, the game owns the picture and the
     *  window. Synchronous, so a toast appears exactly where it always did. */
    private val worldEvents = object : WorldEvents {
        override fun toast(message: String, icon: ToastIcon) = eventToastUI.show(message, IconFactory.iconFor(icon))
        override fun offerEncounter(ship: AiShip) = showEncounterChoice(ship)
        override fun startCombat(ship: AiShip) = startAiCombat(ship)
        override fun arrivedAtPort(port: ResolvedPort) = enterPort(port)
    }

    private var debugOverlayVisible = false

    /** Last reputation tier the player was told about, per faction - see
     *  [announceReputationTierChanges]. Deliberately not saved: on load it refills from the
     *  restored standing on the first frame, which is exactly the "no news" state wanted. */
    private val lastAnnouncedTier = mutableMapOf<Faction, ReputationTier>()

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

        /** Map clicks come in through the input chain rather than being polled off Gdx.input in
         *  the render loop. Polling cannot tell that a click was already spent on something else:
         *  this processor sits LAST in sailingWithToastInputProcessor, so a press the toast or
         *  the HUD has taken never reaches here. Pressing the game-speed button used to both
         *  change speed and lay in a new course, because the polled read saw the same click. */
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (button != Buttons.LEFT) return false
            // A click beside an open message is not an order: the world is frozen until it is
            // closed, and the click used to wait and lay a course the moment it was (REVIEW.md,
            // finding 35).
            if (eventToastUI.isShowing) return true
            pendingMapClick = Vector2(screenX.toFloat(), screenY.toFloat())
            return true
        }
    }

    /** A left click on open map, waiting to be acted on by the next handleInput - see
     *  [sailingInputProcessor]. Null whenever there is nothing to act on. */
    private var pendingMapClick: Vector2? = null
    // The toast's close button needs Stage input handling too, but sailing itself is driven by
    // the raw InputAdapter above rather than a Stage - a multiplexer lets both listen at once
    // (the toast gets first crack, so its close button doesn't leak through as a ship click).
    private lateinit var sailingWithToastInputProcessor: InputMultiplexer

    override fun create() {
        // Before the seed is logged and before anything spawns, so a shot's AI ships and sea
        // events land in the same places on every run with the same --shot-seed.
        shot?.seed?.let { GameRandom.reseed(it) }
        // Before anything that can fail: asset loading is the most likely thing to blow up on a
        // machine that is not this one, and a crash there is exactly the one worth a report.
        installDiagnostics()
        shapeRenderer = ShapeRenderer()
        batch = SpriteBatch()
        // Same TTF as the rest of the game's UI (see UiSkinFactory), but its OWN independent
        // BitmapFont instance - map labels rescale every frame based on camera.zoom, and sharing
        // UiSkinFactory's font object would drag every Scene2D screen's text along with it.
        // One white pixel, stretched and tinted wherever a flat translucent area is wanted.
        // Cheaper than a shape renderer and immune to its persistent projection-matrix state.
        val fogPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        fogPixmap.setColor(Color.WHITE)
        fogPixmap.fill()
        fogTexture = Texture(fogPixmap)
        fogPixmap.dispose()

        // A soft disc, for anything that is a *place* rather than a rectangle of map. Fading to
        // nothing at the rim matters as much as the shape: a hard circle would read as a drawn
        // boundary, and a raid is a stretch of water where trouble is, not a fenced zone.
        radialTexture = buildRadialTexture(RADIAL_TEXTURE_SIZE)

        portLabelFont = UiSkinFactory.generateFont(42)
        portLabelFont.color = Color.BLACK
        portLabelBaseLineHeight = portLabelFont.data.lineHeight

        val mapPixmap = Pixmap(Gdx.files.internal("maps/caribbean.jpg"))
        // Mipmapped and linearly filtered. libGDX defaults every texture to Nearest on both
        // axes, which is why the map broke into hard squares the moment anyone zoomed in - the
        // pixels were being magnified as pixels rather than blended. Linear magnification softens
        // that at no cost, and the mipmaps kill the shimmer at the other end, where a four-
        // thousand-pixel map is squeezed onto a minimap two hundred wide.
        mapTexture = Texture(mapPixmap, true)
        mapTexture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        // Loaded up front rather than on first sight, so the first time a new hull class sails
        // into view is not also the frame that uploads its texture. See ShipArt for the filter.
        (ShipTypes.ALL.map { it.spritePath } + ShipTypes.LONGBOAT.spritePath + AiShipFactory.ALL_SPRITES)
            .distinct()
            .forEach { ShipArt.texture(it) }

        camera = OrthographicCamera()
        viewport = FitViewport(MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, camera)
        camera.position.set(MapProjection.MAP_WIDTH / 2f, MapProjection.MAP_HEIGHT / 2f, 0f)

        minimapCamera = OrthographicCamera()
        minimapCamera.setToOrtho(false, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        minimapCamera.update()

        // Not the picture the player sees - a mask of its own. The artwork is free to be painted
        // however it likes; where a hull may go is a separate, verified file. See WorldMap.
        val maskPixmap = Pixmap(Gdx.files.internal("maps/caribbean_mask.png"))
        worldMap = WorldMap(maskPixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
        maskPixmap.dispose()
        mapPixmap.dispose()

        resolvedPorts = resolvePorts(worldMap)
        newWorld()

        portHubUI = PortHubUI(
            player,
            onOpenMarket = ::openMarket,
            onOpenShipyard = ::openShipStatus,
            onOpenFortress = ::openFortress,
            onOpenWarehouse = ::openWarehouse,
            onOpenTavern = ::openTavern,
            onSetSail = ::leavePort
        )
        marketUI = MarketUI(player, onBack = ::openHub)
        shipyardUI = ShipyardUI(player, onBack = ::openShipStatus)
        shipStatusUI = ShipStatusUI(player, onBack = ::openHub, onOpenShipyard = ::openShipyard, onOpenDocks = ::openDocks)
        fortressUI = FortressUI(player, onBack = ::openHub)
        tavernUI = TavernUI(player, distanceBetween = { from, to -> world.fleetSeaDistance(from, to) }, onBack = ::openHub)
        fleetDockUI = FleetDockUI(player, onBack = ::openShipStatus)
        warehouseUI = WarehouseUI(player, onBack = ::openHub, onFirstWarehousePurchased = ::showFirstWarehouseTip)
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
        captainStatusUI = CaptainStatusUI(player, onBack = ::closeCaptainStatus)
        hudOverlayUI = HudOverlayUI(
            onOpenProperty = ::openProperty,
            onCycleGameSpeed = GameSpeed::cycle,
            onTogglePause = { paused -> GameSpeed.paused = paused },
            onMinimapClick = ::jumpToMinimapPoint,
        )
        sailingWithToastInputProcessor = InputMultiplexer(eventToastUI.stage, hudOverlayUI.stage, sailingInputProcessor)

        val settings = SettingsManager.load()
        AudioManager.setMasterVolume(settings.masterVolume)
        AudioManager.setMuted(settings.muted)
        // Windowed mode is always left exactly as the user sized it - only a saved fullscreen
        // preference ever changes the window on startup.
        // A shot keeps the fixed window size it was launched with, so its pictures compare.
        if (settings.fullscreen && shot == null) {
            val mode = Gdx.graphics.displayModes
                .filter { it.width == settings.windowWidth && it.height == settings.windowHeight }
                .maxByOrNull { it.refreshRate }
            if (mode != null) Gdx.graphics.setFullscreenMode(mode)
        }

        world.shipPosition.set(defaultStartPosition())

        showMainMenu()
        shot?.let { stageShot(it) }
    }

    private var shotFramesRendered = 0
    private var shotTaken = false

    /** Puts the game on the screen a --shot launch asked for, through the same calls a player's
     *  clicks would make - a shot of a state no player can reach would prove nothing. */
    private fun stageShot(request: ShotRequest) {
        if (request.screen == ShotScreen.MENU) return
        startNewGame("Captain")
        if (request.screen == ShotScreen.SEA) {
            camera.position.set(world.shipPosition.x, world.shipPosition.y, 0f)
            camera.zoom = JUMP_TO_SHIP_ZOOM
            clampCamera()
            return
        }
        val port = resolvedPorts.firstOrNull { it.port.name.equals(request.port, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "--shot-port: no port named '${request.port}'. Ports: ${resolvedPorts.joinToString { it.port.name }}"
            )
        world.arriveAtPort(port)
        when (request.screen) {
            ShotScreen.MARKET -> openMarket()
            ShotScreen.SHIPYARD -> openShipyard()
            ShotScreen.STATUS -> openShipStatus()
            ShotScreen.FORTRESS -> openFortress()
            ShotScreen.WAREHOUSE -> openWarehouse()
            ShotScreen.TAVERN -> openTavern()
            ShotScreen.DOCKS -> openDocks()
            ShotScreen.HUB, ShotScreen.MENU, ShotScreen.SEA -> {}
        }
    }

    /** Called last in render(), after everything is drawn, so the grab is the finished frame. */
    private fun captureShotIfDue(request: ShotRequest) {
        if (shotTaken) return
        shotFramesRendered++
        if (shotFramesRendered < request.frames) return
        shotTaken = true
        val path = DiagnosticsFiles.writeScreenshotTo(request.outPath)
        // One line, greppable from a background run's output: SHOT <absolute path> or SHOT FAILED.
        println("SHOT ${path ?: "FAILED"}")
        Gdx.app.exit()
    }

    private fun defaultStartPosition(): Vector2 =
        resolvedPorts.firstOrNull { it.port.name == "Port Royal" }?.approachPoint
            ?: Vector2(MapProjection.MAP_WIDTH / 2f, MapProjection.MAP_HEIGHT / 2f)

    override fun resize(width: Int, height: Int) {
        // A minimised window reports 0x0 on Windows, and letting that reach the viewport is not
        // a harmless no-op: worldUnitsPerScreenPixel() then divides by a zero screen width, and
        // the resulting Infinity is fed to portLabelFont.data.setScale(). That call is RELATIVE
        // to the previous scale, so a single bad frame corrupts the font's metrics permanently -
        // the port labels vanished on minimise and never came back, whatever the window did
        // afterwards. Ignoring a degenerate size costs nothing: a real resize always follows.
        if (width <= 0 || height <= 0) return
        // Fill the window by width rather than fitting the whole map into it. The world keeps the
        // map's full width and takes its height from the window's own aspect, so a FitViewport has
        // nothing left to letterbox - at the cost of the map's top and bottom falling outside a
        // wide window when fully zoomed out. Black bars down both sides cost more than the few
        // rows of sea this hides, and clampCamera keeps working because camera.viewportHeight is
        // still the truth about what is visible.
        viewport.setWorldSize(MapProjection.MAP_WIDTH, MapProjection.MAP_WIDTH * height / width.toFloat())
        viewport.update(width, height)
        portHubUI.resize(width, height)
        marketUI.resize(width, height)
        shipyardUI.resize(width, height)
        shipStatusUI.resize(width, height)
        fortressUI.resize(width, height)
        warehouseUI.resize(width, height)
        tavernUI.resize(width, height)
        fleetDockUI.resize(width, height)
        combatUI.resize(width, height)
        mainMenuUI.resize(width, height)
        newGameUI.resize(width, height)
        loadGameUI.resize(width, height)
        saveGameUI.resize(width, height)
        settingsUI.resize(width, height)
        eventToastUI.resize(width, height)
        hudOverlayUI.resize(width, height)
        propertyUI.resize(width, height)
        captainStatusUI.resize(width, height)
    }

    override fun render() {
        // Re-apply every frame: the GL viewport rectangle (letterbox bars) otherwise doesn't
        // reliably stick between frames, which silently desyncs rendering from click unprojection.
        viewport.apply()

        // Diagnostics keys work in every state, including menus - a visual bug is just as likely
        // on a menu screen, and F12 is worth nothing if it only works while sailing.
        if (Gdx.input.isKeyJustPressed(Keys.F3)) {
            debugOverlayVisible = !debugOverlayVisible
        }
        if (Gdx.input.isKeyJustPressed(Keys.F12)) {
            val path = DiagnosticsFiles.writeScreenshot("screenshot")
            GameLog.log(GameLog.Category.SYSTEM, "screenshot: ${path ?: "failed"}")
            if (path != null) eventToastUI.show("Screenshot saved to the diagnostics folder.", IconFactory.property)
        }

        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            handleEscape()
        }

        // The world-sim clock: real deltaTime scaled by the player's chosen game speed (see
        // GameSpeed/HudOverlayUI's clock control), used everywhere something is meant to speed
        // up/slow down with it - ship movement, GameClock, fouling, market drift, sea-event
        // timers. Deliberately NOT used for UI/meta things below (Scene2D stage.act, camera pan,
        // the autosave interval, the rejected-click flash) - those should feel the same at any
        // game speed, only the simulated world should visibly accelerate.
        // Capped before the speed multiplier: a long frame (the window dragged, a save written)
        // at 20x used to hand the world one enormous step (REVIEW.md, finding 15).
        val worldDelta = kotlin.math.min(Gdx.graphics.deltaTime, MAX_FRAME_SECONDS) * GameSpeed.worldMultiplier

        // Refilled before anything can spend it. Every cooldown in the AI is counted in game
        // time, so at 20x the world wants twenty times as many courses per real second as it does
        // at 1x - while the cost of laying them is paid in real seconds, out of the frame the
        // player is watching. That is why the stutter only ever showed at speed.
        world.routeBudget = ROUTES_PER_FRAME

        if (appState == AppState.PLAYING) {
            MarketState.update(worldDelta)
            GameClock.update(worldDelta)
            if (!shipVisible) {
                portPulseTime += Gdx.graphics.deltaTime
            }
            autosaveTimer += Gdx.graphics.deltaTime
            // Never in the middle of a fight (REVIEW.md, finding 14): a save taken there holds a
            // hull mid-battle and no battle, or - in the defeat window - the ship, hold and purse
            // the defeat is about to take. The timer keeps running and fires as the fight ends.
            if (autosaveTimer >= AUTOSAVE_INTERVAL_SECONDS && gameMode != GameMode.COMBAT) {
                autosaveTimer = 0f
                // A failed autosave is worth a log line, not the session.
                try {
                    saveGame(AUTOSAVE_NAME)
                } catch (e: Exception) {
                    GameLog.log(GameLog.Category.SAVE, "autosave failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            checkLoanOverdue()
            updateDebtHud()
            world.updateAlways()
        }

        // A sea-event toast freezes sailing entirely while it's up - ship movement, AI ships,
        // encounters, and further events all wait, so nothing can happen unseen behind it and a
        // click meant for its close button can't also be read as a course-setting click.
        if (appState == AppState.PLAYING && gameMode == GameMode.SAILING && !eventToastUI.isShowing && !propertyOpen && !captainStatusOpen) {
            handleInput(worldDelta)
            world.updateSailing(worldDelta, Gdx.graphics.deltaTime)
            if (!updateDragPan()) updateCameraPan(Gdx.graphics.deltaTime)
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
        drawPirateSea()
        drawRaidRegion()
        batch.end()

        batch.begin()
        if (gameActive && !shipVisible) drawDockedGlow()
        drawPorts()
        if (gameActive) {
            drawAiShips()
            drawFleetShips()
            if (shipVisible) drawShip()
            drawMarketIndicators()
        }
        drawPirateSeaLabel()
        drawRaidLabel()
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
            // The HUD is the sailing screen's own furniture: a port window is a place, and the
            // painted shopfront is the whole of it. Hiding the chrome rather than letting the two
            // overlap is what settles the question (see HudOverlayUI.setVisible).
            val hudVisible = gameMode == GameMode.SAILING
            hudOverlayUI.setVisible(hudVisible)
            if (hudVisible) {
                hudOverlayUI.setDate(GameClock.calendarLabel)
                hudOverlayUI.setGold(player.gold)
                hudOverlayUI.setGameSpeed(GameSpeed.multiplier)
                hudOverlayUI.setPaused(GameSpeed.paused)
                hudOverlayUI.setShips(buildShipStatuses())
                hudOverlayUI.setReputation(player.reputation)
                hudOverlayUI.setDebugText(if (debugOverlayVisible) buildDebugText() else null)
                // Drawn into the hole in the chart frame, so it has to know where that hole is -
                // and the frame has to be laid out before anything is drawn into it.
                drawMinimap()
                hudOverlayUI.render(Gdx.graphics.deltaTime)
            }
            announceReputationTierChanges()
        }

        if (appState == AppState.PLAYING) {
            when (gameMode) {
                GameMode.IN_PORT -> when (portScreen) {
                    PortScreen.HUB -> portHubUI.render(Gdx.graphics.deltaTime)
                    PortScreen.MARKET -> marketUI.render(Gdx.graphics.deltaTime)
                    PortScreen.SHIPYARD -> shipyardUI.render(Gdx.graphics.deltaTime)
                    PortScreen.SHIP_STATUS -> shipStatusUI.render(Gdx.graphics.deltaTime)
                    PortScreen.FORTRESS -> fortressUI.render(Gdx.graphics.deltaTime)
                    PortScreen.WAREHOUSE -> warehouseUI.render(Gdx.graphics.deltaTime)
                    PortScreen.TAVERN -> tavernUI.render(Gdx.graphics.deltaTime)
                    PortScreen.DOCKS -> fleetDockUI.render(Gdx.graphics.deltaTime)
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

        if (propertyOpen) propertyUI.render(Gdx.graphics.deltaTime)
        if (captainStatusOpen) captainStatusUI.render(Gdx.graphics.deltaTime)
        // Always last, regardless of screen: a toast (sea event, combat outcome, loan overdue,
        // warehouse first-purchase tip, etc.) must draw on top of whatever it interrupted, not
        // get painted over by that screen's own render call above. Not gated to GameMode.SAILING
        // either - e.g. a delivery toast can be triggered by arriveAtPort() switching to
        // GameMode.IN_PORT earlier in this same frame, before this draw pass runs.
        if (appState == AppState.PLAYING) eventToastUI.render(Gdx.graphics.deltaTime)

        shot?.let { captureShotIfDue(it) }
    }

    private fun handleInput(delta: Float) {
        var moveX = 0f
        var moveY = 0f
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) moveY += 1f
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) moveY -= 1f
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) moveX -= 1f
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) moveX += 1f

        if (moveX != 0f || moveY != 0f) {
            shipVisible = true
            world.sailByHand(moveX, moveY, delta)
        }

        val mapClick = pendingMapClick
        pendingMapClick = null
        if (mapClick != null) {
            touchPoint.set(mapClick)
            viewport.unproject(touchPoint)

            val activeCaptains = player.captains.filter { !it.isIdle }
            val nearestCaptain = activeCaptains.minByOrNull { world.fleetShipTransform(it)?.first?.dst(touchPoint) ?: Float.MAX_VALUE }
            val nearestCaptainPos = nearestCaptain?.let { world.fleetShipTransform(it)?.first }
            if (nearestCaptain != null && nearestCaptainPos != null && nearestCaptainPos.dst(touchPoint) <= FLEET_SHIP_CLICK_RADIUS) {
                openCaptainStatus(nearestCaptain)
                return
            }

            val nearestShip = world.aiShips.minByOrNull { it.position.dst(touchPoint) }
            if (nearestShip != null && nearestShip.position.dst(touchPoint) <= SHIP_HUNT_CLICK_RADIUS) {
                world.huntShip(nearestShip)
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
                if (world.setCourse(destination, targetPort)) {
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

    /** Snaps the camera to a fleet captain's ship - from the HUD's captain-icon row (see
     *  HudOverlayUI.setCaptains), not the on-map click-to-open-status icon, so this has to find
     *  the ship without the player having spotted it on screen first. Prefers the live sailing
     *  position (see fleetShipTransform); falls back to wherever the ship is docked only if that
     *  ever comes back null (defensive - every non-idle captain currently always resolves to a
     *  sailing position, see Captain's class doc on the always-sailing abstraction). */
    /** The fleet column in the HUD: the player's own ship always first - and not clickable, the
     *  camera is already on it - then one line per captain at sea, each a shortcut to that ship.
     *  A captain's hold is whatever they bought on this leg (see FleetSimulation.attemptBuy), so
     *  "cargo" means different things on the two kinds of line only in that the player's is a
     *  running total and a captain's is one cargo at a time. */
    private fun buildShipStatuses(): List<ShipStatus> {
        val condition = player.condition
        val own = ShipStatus(
            shipName = player.ownedShips.firstOrNull { it.id == player.activeShipId }?.name ?: player.ship.name,
            captainName = "You",
            hull = fraction(condition.currentHull.toFloat(), player.effectiveMaxHull.toFloat()),
            sails = 1f - condition.fouling,
            cargo = fraction(player.cargoUsed.toFloat(), player.cargoCapacity.toFloat()),
            onClick = ::jumpToPlayerShip,
        )
        val fleet = player.captains.filter { !it.isIdle }.map { captain ->
            val ship = player.ownedShips.find { it.id == captain.shipId }
            ShipStatus(
                shipName = ship?.name ?: "-",
                captainName = captain.name,
                hull = if (ship == null) 0f else fraction(ship.condition.currentHull.toFloat(), ship.shipType.maxHull.toFloat()),
                sails = if (ship == null) 0f else 1f - ship.condition.fouling,
                cargo = if (ship == null) 0f else fraction(captain.cargoUnits.toFloat(), ship.shipType.cargoCapacity.toFloat()),
                onClick = { jumpToCaptain(captain) },
            )
        }
        return listOf(own) + fleet
    }

    private fun fraction(value: Float, of: Float): Float = if (of <= 0f) 0f else (value / of).coerceIn(0f, 1f)

    /** Tapping the chart moves the view there, keeping whatever zoom the player is already at -
     *  the ordinary way to cross a big map without dragging it. Only the camera moves; the ship
     *  keeps its course, and [jumpToPlayerShip] brings the view back. */
    private fun jumpToMinimapPoint(screenX: Float, screenY: Float) {
        if (!gameActive || gameMode != GameMode.SAILING) return
        if (minimapMapRect.width <= 0f || !minimapMapRect.contains(screenX, screenY)) return
        camera.position.set(
            (screenX - minimapMapRect.x) / minimapMapRect.width * MapProjection.MAP_WIDTH,
            (screenY - minimapMapRect.y) / minimapMapRect.height * MapProjection.MAP_HEIGHT,
            0f,
        )
        clampCamera()
    }

    /** The player's own line in the fleet column does the same thing a captain's does: snap the
     *  camera back to that ship. Worth having even though the camera usually follows the player -
     *  it does not while the map is being panned around. */
    private fun jumpToPlayerShip() {
        camera.position.set(world.shipPosition.x, world.shipPosition.y, 0f)
        camera.zoom = JUMP_TO_SHIP_ZOOM
        clampCamera()
    }

    private fun jumpToCaptain(captain: Captain) {
        val target = world.fleetShipTransform(captain)?.first
            ?: player.ownedShips.find { it.id == captain.shipId }?.let { world.portApproachPos(it.dockedAt) }
            ?: return
        camera.position.set(target.x, target.y, 0f)
        camera.zoom = JUMP_TO_SHIP_ZOOM
        clampCamera()
    }

    /** The game's half of making port (see [GameWorld.arriveAtPort], which has already paid off
     *  any passenger bound here): the port screens, the music, the docked ship. */
    private fun enterPort(port: ResolvedPort) {
        gameMode = GameMode.IN_PORT
        currentPortName = port.port.name
        portPulseTime = 0f
        shipVisible = false
        AudioManager.pauseSailingMusic()
        AudioManager.playPortArrival()
        openHub()
    }

    /** Every screen reachable while appState == PLAYING needs eventToastUI.stage in its input
     *  chain, not just the sailing view - a toast (e.g. the warehouse first-purchase tip, or a
     *  loan going overdue) can be triggered while the player is on any of these screens, and
     *  without this its close button would be undismissable until the player backs out. See also
     *  the render() ordering note on why eventToastUI draws last. */
    private fun withToastInput(stage: Stage): InputMultiplexer = InputMultiplexer(eventToastUI.stage, stage)

    private fun openHub() {
        portScreen = PortScreen.HUB
        portHubUI.open(currentPortName)
        Gdx.input.inputProcessor = withToastInput(portHubUI.stage)
    }

    private fun openMarket() {
        portScreen = PortScreen.MARKET
        marketUI.open(currentPortName)
        Gdx.input.inputProcessor = withToastInput(marketUI.stage)
    }

    private fun openShipyard() {
        portScreen = PortScreen.SHIPYARD
        shipyardUI.open(currentPortName)
        Gdx.input.inputProcessor = withToastInput(shipyardUI.stage)
    }

    private fun openShipStatus() {
        portScreen = PortScreen.SHIP_STATUS
        shipStatusUI.open()
        Gdx.input.inputProcessor = withToastInput(shipStatusUI.stage)
    }

    private fun openFortress() {
        portScreen = PortScreen.FORTRESS
        fortressUI.open(currentPortName)
        Gdx.input.inputProcessor = withToastInput(fortressUI.stage)
    }

    private fun openWarehouse() {
        portScreen = PortScreen.WAREHOUSE
        warehouseUI.open(currentPortName)
        Gdx.input.inputProcessor = withToastInput(warehouseUI.stage)
    }

    private fun openTavern() {
        portScreen = PortScreen.TAVERN
        tavernUI.open(currentPortName)
        Gdx.input.inputProcessor = withToastInput(tavernUI.stage)
    }

    private fun openDocks() {
        portScreen = PortScreen.DOCKS
        fleetDockUI.open(currentPortName)
        Gdx.input.inputProcessor = withToastInput(fleetDockUI.stage)
    }

    private fun showFirstWarehouseTip() {
        eventToastUI.show(
            "Prices drift back toward normal over about 10 in-game minutes. Storing goods here " +
                "instead of selling right away - and waiting out a low price - can be worth more " +
                "than an instant sale.",
            IconFactory.warehouse
        )
    }

    private fun openProperty() {
        propertyOpen = true
        propertyUI.open()
        Gdx.input.inputProcessor = withToastInput(propertyUI.stage)
    }

    private fun openCaptainStatus(captain: Captain) {
        captainStatusOpen = true
        captainStatusUI.open(captain)
        Gdx.input.inputProcessor = withToastInput(captainStatusUI.stage)
    }

    private fun closeCaptainStatus() {
        captainStatusOpen = false
        Gdx.input.inputProcessor = sailingWithToastInputProcessor
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
     *  or pauses gameplay to open the top-level menu - whichever applies to the current state.
     *  An open captain or property window counts as a level of its own: Esc closes it first.
     *  Pausing over it used to leave the window drawn but deaf to clicks and the world stopped,
     *  with no way back short of a restart. */
    private fun handleEscape() {
        when {
            appState == AppState.PLAYING && captainStatusOpen -> closeCaptainStatus()
            appState == AppState.PLAYING && propertyOpen -> closeProperty()
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
        // Saving from the pause menu is closed during a fight for the same reason (finding 14).
        mainMenuUI.open(gameActive, canSave = gameActive && gameMode != GameMode.COMBAT)
        Gdx.input.inputProcessor = mainMenuUI.stage
    }

    private fun resumeGame() {
        appState = AppState.PLAYING
        Gdx.input.inputProcessor = when (gameMode) {
            // A window still flagged open gets the input back - otherwise it is drawn and the
            // world waits on it, but nothing can reach its Back button.
            GameMode.SAILING -> when {
                captainStatusOpen -> withToastInput(captainStatusUI.stage)
                propertyOpen -> withToastInput(propertyUI.stage)
                else -> sailingWithToastInputProcessor
            }
            GameMode.IN_PORT -> withToastInput(
                when (portScreen) {
                    PortScreen.HUB -> portHubUI.stage
                    PortScreen.MARKET -> marketUI.stage
                    PortScreen.SHIPYARD -> shipyardUI.stage
                    PortScreen.SHIP_STATUS -> shipStatusUI.stage
                    PortScreen.FORTRESS -> fortressUI.stage
                    PortScreen.WAREHOUSE -> warehouseUI.stage
                    PortScreen.TAVERN -> tavernUI.stage
                    PortScreen.DOCKS -> fleetDockUI.stage
                }
            )
            GameMode.COMBAT -> withToastInput(combatUI.stage)
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

    /**
     * A fresh world: its own AI ships, sea events, raid clock, watchdog, hunt, passenger and
     * captains' drawn routes (review batch 2, decision 10; REVIEW.md, finding 12). Built once in
     * [create] and again for every new game and every load, so nothing from one game - a raid in
     * progress, a castaway aboard, a ship being hunted - sails on into the next.
     *
     * A load builds it [populated] = false and fills it from the save instead (WorldSave), so the
     * game carries on from the moment it was saved. The player's position is written into the new
     * world by the caller, after this (decision 18).
     */
    private fun newWorld(populated: Boolean = true) {
        world = GameWorld(worldMap, resolvedPorts, player, worldEvents)
        if (populated) populateWorld()
    }

    private fun populateWorld() {
        world.spawnAiShips()
        world.seaEventSlots.forEach { world.spawnSeaEvent(it) }
    }

    private fun beginPlaying() {
        GameSpeed.reset()
        // Nothing from the last game is still waiting to be read (REVIEW.md, finding 20).
        eventToastUI.clear()
        shipVisible = true
        propertyOpen = false
        captainStatusOpen = false
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
        newWorld()
        world.shipPosition.set(defaultStartPosition())
        beginPlaying()
    }

    /** Loads [saveName]; answers why it could not - for the load screen to show - or null. */
    private fun loadGame(saveName: String): String? {
        val loaded = when (val result = SaveManager.load(saveName)) {
            is LoadResult.Failed -> {
                // A load used to fail in silence: the button simply did nothing (finding 19).
                GameLog.log(GameLog.Category.SAVE, "load of \"$saveName\" failed: ${result.reason}")
                return "\"$saveName\" could not be read - the file is damaged."
            }
            is LoadResult.Loaded -> result
        }
        val data = loaded.data
        captainName = data.captainName
        val shipType = SaveManager.resolveShipType(data.shipTypeName)
        val cargo = SaveManager.resolveCargo(data.cargo)
        player.loadState(data.gold, shipType, cargo)
        SaveManager.resolveReputation(data.reputation).forEach { (faction, value) -> player.reputation[faction] = value }
        player.reputationRemainder.clear()
        player.reputationRemainder.putAll(SaveManager.resolveReputationRemainder(data.reputationRemainder))
        MarketState.restore(data.marketState)
        GameClock.restore(data.gameClockMinutes)
        player.warehouses.clear()
        player.warehouses.putAll(SaveManager.resolveWarehouses(data))
        player.loan = SaveManager.resolveLoan(data)
        val fleet = SaveManager.resolveOwnedShips(data)
        player.loadFleet(fleet, SaveManager.resolveActiveShipId(data, fleet))
        // After loadFleet, not before: player.condition resolves to the ACTIVE hull's own
        // condition, which does not exist until the roster is restored. The top-level save
        // fields are the active hull's truth in every format written so far - in saves from
        // before this became one object, the per-ship entry for the active hull holds a stale
        // copy, so these have to win.
        player.condition.fouling = data.fouling
        player.condition.cannonLevel = data.cannonLevel
        player.condition.oarLevel = data.oarLevel
        player.condition.sailLevel = data.sailLevel
        // After the levels, which set the bound; never 0 - see resolveActiveHull (finding 14).
        player.condition.currentHull = SaveManager.resolveActiveHull(data.currentHull, player.effectiveMaxHull)
        player.captains.clear()
        player.captains.addAll(SaveManager.resolveCaptains(data))
        newWorld(populated = false)
        world.shipPosition.set(data.shipX, data.shipY)
        SaveManager.resolvePassenger(data.passengerPort, resolvedPorts.map { it.port.name })?.let { name ->
            world.passengerDestination = resolvedPorts.first { it.port.name == name }
        }
        // The same sea it was saved in; a save from before the world was saved (or of a world
        // version this build cannot read) gets a fresh one, and the player is told.
        val seaRestored = data.world?.let { WorldSave.restore(it, world, player) } ?: false
        if (!seaRestored) populateWorld()
        beginPlaying()
        if (!seaRestored) {
            eventToastUI.show("This save is from an earlier version - the sea around you is new.", IconFactory.property)
        }
        if (loaded.fromBackup) {
            GameLog.log(GameLog.Category.SAVE, "\"$saveName\" was damaged; loaded the copy kept before its last write")
            eventToastUI.show("\"$saveName\" was damaged - the copy saved before it was loaded instead.", IconFactory.property)
        }
        return null
    }

    private fun saveGame(saveName: String) {
        SaveManager.save(
            saveName, captainName, player, world.shipPosition, world.passengerDestination?.port?.name,
            world = WorldSave.capture(world, player)
        )
    }

    /**
     * Opens the session log and arranges for an unhandled exception to leave behind something a
     * person can act on. Nothing here is allowed to be the reason the game fails to start, hence
     * the guard around the whole thing.
     *
     * The handler is the JVM's default rather than a try/catch around render(): an exception in
     * the render loop propagates out of libGDX's main loop uncaught, and this way a crash on any
     * other thread (audio, asset I/O) is captured too.
     */
    private fun installDiagnostics() {
        try {
            // A shot opens no session log: the folder keeps only the last few sessions, and a
            // run of screenshots would rotate a real play session's log out of it.
            val logFile = if (shot == null) DiagnosticsFiles.beginSession(version) else null
            GameLog.start(
                environment = describeEnvironment(),
                sink = { line -> DiagnosticsFiles.append(line) },
                contextProvider = ::describeWorldState
            )
            if (logFile != null) GameLog.log(GameLog.Category.SYSTEM, "logging to ${logFile.file().absolutePath}")
            // The seed goes in first, before anything can go wrong: it is the one line that makes
            // a reported fight or spawn replayable (see GameRandom).
            GameLog.log(GameLog.Category.SYSTEM, "random seed: ${GameRandom.seed}")

            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                val report = try {
                    GameLog.buildCrashReport(throwable)
                } catch (secondary: Throwable) {
                    "crash report could not be built: $secondary\n\n${GameLog.stackTraceOf(throwable)}"
                }
                // Attempted, not assumed: by the time some crashes reach here the GL context is
                // already gone, and a failed screenshot must not swallow the report itself.
                DiagnosticsFiles.writeScreenshot("crash")
                val path = DiagnosticsFiles.writeCrashReport(report)
                System.err.println(report)
                if (path != null) System.err.println("Crash report written to: $path")
                // Still hand it on, so the exit code and console output stay what they were.
                previous?.uncaughtException(thread, throwable)
            }
        } catch (e: Throwable) {
            println("Diagnostics failed to start (${e.message}) - continuing without logging")
        }
    }

    /**
     * The F3 overlay. Its job is to turn "the pirate is flickering" into something a report can
     * quote - the nearest AI ship's actual state, the seed the session is running on, what the
     * simulation thinks is happening. Cheap to read at a glance, and never on by default.
     */
    private fun buildDebugText(): String {
        val nearest = world.aiShips.minByOrNull { it.position.dst(world.shipPosition) }
        val nearestLine = if (nearest == null) "none" else buildString {
            append(nearest.displayName)
            append(" @").append(nearest.position.dst(world.shipPosition).toInt())
            append(if (nearest.isPursuing) " PURSUING" else " patrol")
            if (nearest.pursuitBlockedFor > 0f) append(" blocked=%.1fs".format(nearest.pursuitBlockedFor))
            if (nearest.giveUpCooldown > 0f) append(" cooldown=%.1fs".format(nearest.giveUpCooldown))
            append(" path=").append(nearest.pathIndex).append('/').append(nearest.path.size)
            // Without the anchor there is no way to tell home waters are working short of
            // watching one ship for half an hour.
            append(" anchor=").append(nearest.anchor.x.toInt()).append(',').append(nearest.anchor.y.toInt())
            append(if (nearest.outbound) " outbound" else " homeward")
        }
        return buildString {
            appendLine("F3 debug   seed=${GameRandom.seed}")
            appendLine("fps ${Gdx.graphics.framesPerSecond}   mode $gameMode   speed ${GameSpeed.multiplier}x")
            appendLine("player ${world.shipPosition.x.toInt()}, ${world.shipPosition.y.toInt()}   nav ${world.navMode}")
            appendLine("ai ships ${world.aiShips.size} (${world.aiShips.count { it.isPursuing }} pursuing)   ports ${resolvedPorts.size}")
            appendLine(
                world.activeRaid?.let {
                    "raid ${it.label}: ${it.pirates(world.aiShips).size} pirates, ${it.warships(world.aiShips).size} navy, " +
                        "${it.secondsRemaining.toInt()}s left"
                } ?: "no raid (next in ${world.secondsUntilNextRaid.toInt()}s)"
            )
            // The fleet column's own state: a captain who is hired but does not appear on the
            // HUD is either idle, shipless, or past the number of panels the window has room for,
            // and from the outside those look identical.
            appendLine(
                "fleet: ${player.captains.size} captains (${player.captains.count { !it.isIdle }} at sea)" +
                    "   hud panels ${buildShipStatuses().size}"
            )
            appendLine("nearest: $nearestLine")
            append("F12 screenshot   diagnostics: ${DiagnosticsFiles.directoryPath ?: "unavailable"}")
        }
    }

    private fun describeEnvironment(): List<String> {
        fun gl(name: Int) = try {
            Gdx.gl.glGetString(name) ?: "?"
        } catch (e: Throwable) {
            "unavailable"
        }
        return listOf(
            "game version: $version",
            "os: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
            "jvm: ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
            "gl renderer: ${gl(GL20.GL_RENDERER)}",
            "gl vendor: ${gl(GL20.GL_VENDOR)}",
            "gl version: ${gl(GL20.GL_VERSION)}",
            "window: ${Gdx.graphics.width}x${Gdx.graphics.height}"
        )
    }

    /**
     * A snapshot of what the world was doing, gathered only when a crash happens. Every read is
     * defensive: this runs while things are already going wrong, and a `lateinit` that was never
     * initialised must not turn a useful report into a second exception.
     */
    private fun describeWorldState(): List<String> = buildList {
        fun safe(label: String, read: () -> Any?) {
            add("$label: " + try { read().toString() } catch (e: Throwable) { "<unreadable: ${e::class.java.simpleName}>" })
        }
        safe("app state") { appState }
        safe("game mode") { gameMode }
        safe("port screen") { portScreen }
        safe("current port") { currentPortName }
        safe("game clock") { "${GameClock.calendarLabel} (${GameClock.totalGameMinutes} min)" }
        safe("world speed") { "${GameSpeed.multiplier}x" }
        safe("player gold") { player.gold }
        safe("player ship") { "${player.ship.name} hull ${player.condition.currentHull}/${player.effectiveMaxHull}" }
        safe("player position") { "${world.shipPosition.x}, ${world.shipPosition.y}" }
        safe("nav mode") { world.navMode }
        safe("cargo") { "${player.cargoUsed}/${player.cargoCapacity}" }
        safe("reputation") { Faction.entries.joinToString { "${it.name}=${player.reputation[it]}" } }
        safe("owned ships") { player.ownedShips.size }
        safe("captains") { player.captains.count { !it.isIdle } }
        safe("ai ships") { world.aiShips.size }
        safe("ports") { resolvedPorts.size }
        safe("pursuing ships") { world.aiShips.count { it.isPursuing } }
    }

    /** Once past its deadline, a loan only gets flagged overdue once: the debt doubles and that
     *  faction's reputation crashes to hostile (reusing the existing hostile-military-engagement
     *  check in checkAiEncounters/isHostile - no separate "at war" flag needed). */
    private fun checkLoanOverdue() {
        val loan = player.checkLoanDeadline(GameClock.totalGameMinutes) ?: return
        eventToastUI.show(
            "Your debt to the ${loan.faction.displayName} commandant is overdue! It has doubled " +
                "to ${loan.repayAmount}g, and their ships will now attack you on sight.",
            IconFactory.debt
        )
    }

    /** Announces a crossed reputation tier once, in either direction. Polled from the render loop
     *  rather than raised at each call site because standing now moves from four different places
     *  (the player's own sale, a captain's sale, sinking a ship, an overdue loan) - and a sale
     *  moves all four factions at once. Without this the player watches a bar creep and never
     *  learns that the notch it just passed is what changed prices, or what put a navy on them. */
    private fun announceReputationTierChanges() {
        for (faction in Faction.entries) {
            val tier = player.reputationTier(faction)
            val previous = lastAnnouncedTier[faction]
            lastAnnouncedTier[faction] = tier
            if (previous == null || previous == tier) continue
            val improved = tier.ordinal > previous.ordinal
            GameLog.log(
                GameLog.Category.ECONOMY,
                "${faction.displayName} standing ${previous.label} -> ${tier.label} " +
                    "(${player.reputation[faction]})"
            )
            eventToastUI.show(
                "${faction.displayName}: ${previous.label} -> ${tier.label}",
                if (improved) IconFactory.coin else IconFactory.debt
            )
        }
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

    /** The "Fight / Sail On" popup for a passive encounter (see GameWorld.checkAiEncounters) - Sail On
     *  costs nothing but the same give-up cooldown a failed catch would (see GameWorld.declineEncounter);
     *  declining isn't free information either way, the ship just moves on like it would after
     *  any other miss. Reuses EventToastUI.showChoice rather than a bespoke screen. */
    private fun showEncounterChoice(ship: AiShip) {
        eventToastUI.showChoice(
            "A ${ship.displayName} closes in! Will you fight, or try to sail on?",
            IconFactory.debt,
            "Fight",
            { world.startAiCombat(ship) },
            "Sail On",
            { world.declineEncounter(ship) }
        )
    }

    /** The game's half of a fight with the player - see GameWorld.startAiCombat for the world's. */
    private fun startAiCombat(ship: AiShip) {
        gameMode = GameMode.COMBAT
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
            spritePath = ship.spritePath,
            goldReward = ship.goldReward,
            currentHull = ship.currentHull
        )
        combatUI.open(CombatState(player, playerStats, enemyStats, onEnemyHullChanged = { ship.currentHull = it }))
        Gdx.input.inputProcessor = withToastInput(combatUI.stage)
    }

    /** The game's half of the end of a fight - see GameWorld.finishCombat for the world's. */
    private fun finishCombat(outcome: CombatOutcome) {
        world.finishCombat(outcome)
        gameMode = GameMode.SAILING
        Gdx.input.inputProcessor = sailingWithToastInputProcessor
        AudioManager.stopCombatMusic()
        AudioManager.playSailingMusic()
    }

    private fun drawPorts() {
        for (resolved in resolvedPorts) {
            // Seated rather than centred: the buildings rise from the harbour, so only a
            // quarter of the town hangs below the port's own point and the rest stands above it.
            batch.draw(
                IconFactory.port,
                resolved.worldPos.x - PORT_ICON_WIDTH / 2f,
                resolved.worldPos.y - PORT_ICON_HEIGHT * 0.25f,
                PORT_ICON_WIDTH, PORT_ICON_HEIGHT
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
                    world.shipPosition.dst(centerX, centerY) < overlapRadius
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
    private fun worldUnitsPerScreenPixel(): Float {
        // Belt and braces alongside the 0x0 guard in resize(): anything that ever hands this a
        // zero screen width would otherwise return Infinity, and Infinity reaching
        // BitmapFontData.setScale() ruins the font's metrics for the rest of the session, not
        // just for that frame.
        val screenWidth = viewport.screenWidth.toFloat()
        if (screenWidth <= 0f) return 1f
        return camera.viewportWidth * camera.zoom / screenWidth
    }

    /** World-space height a port label renders at right now, for drawMarketIndicators() to stack
     *  the goods row underneath it. */
    private fun portLabelWorldHeight(): Float = PORT_LABEL_TARGET_PX * worldUnitsPerScreenPixel()

    /**
     * The fog over the Sea of Pirates, drawn straight after the map and under everything else.
     *
     * A flat translucent rectangle through the SpriteBatch, deliberately - not ShapeRenderer,
     * whose projection matrix is persistent mutable state that has leaked between draw calls in
     * this file before (see CLAUDE.md). A proper ragged-edged fog is artwork, and the map is being
     * redrawn in M16 anyway; painting it twice would be the only thing worse than a rectangle.
     */
    private fun drawPirateSea() {
        val origin = PirateSea.fogOrigin
        batch.color = PIRATE_SEA_FOG
        batch.draw(fogTexture, origin.x, origin.y, PirateSea.fogWidth, PirateSea.fogHeight)
        batch.color = Color.WHITE
    }

    /** The Sea's name, drawn with the port-label machinery so it holds a constant on-screen size
     *  at any zoom, and larger than a port so it reads as a region rather than a place. */
    /** The raid's stain on the map, drawn with the same stretched-pixel trick as the Sea of
     *  Pirates - a square around the circle the raid actually uses, which reads fine as a smear
     *  of trouble and costs nothing. Sink marks stay until the raid ends, so arriving late still
     *  shows that something happened here. */
    private fun buildRadialTexture(size: Int): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val centre = size / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x + 0.5f - centre
                val dy = y + 0.5f - centre
                val distance = kotlin.math.sqrt(dx * dx + dy * dy) / centre
                // Full strength out to most of the radius, fading only over the last quarter.
                // Squaring the falloff, as this did, concentrated everything at the centre and
                // left the edges invisible - the region read as a smudge rather than an area.
                val falloff = ((1f - distance) / RADIAL_EDGE_SOFTNESS).coerceIn(0f, 1f)
                pixmap.setColor(1f, 1f, 1f, falloff)
                pixmap.drawPixel(x, y)
            }
        }
        val texture = Texture(pixmap)
        pixmap.dispose()
        return texture
    }

    private fun drawRaidRegion() {
        val raid = world.activeRaid ?: return
        batch.color = RAID_REGION_TINT
        batch.draw(
            radialTexture,
            raid.centre.x - raid.radius, raid.centre.y - raid.radius,
            raid.radius * 2f, raid.radius * 2f
        )
        batch.color = Color.WHITE
        for (mark in raid.marks) {
            batch.color = if (mark.wasPirate) SINK_MARK_PIRATE else SINK_MARK_NAVY
            batch.draw(
                IconFactory.dockedGlow,
                mark.position.x - SINK_MARK_SIZE / 2f, mark.position.y - SINK_MARK_SIZE / 2f,
                SINK_MARK_SIZE, SINK_MARK_SIZE
            )
        }
        batch.color = Color.WHITE
    }

    private fun drawRaidLabel() {
        val raid = world.activeRaid ?: return
        val scale = portLabelWorldHeight() * RAID_LABEL_SCALE / portLabelBaseLineHeight
        portLabelFont.data.setScale(scale)
        portLabelFont.color = RAID_LABEL_COLOR
        val text = "Pirate activity"
        val layout = GlyphLayout(portLabelFont, text)
        portLabelFont.draw(
            batch, text,
            raid.centre.x - layout.width / 2f,
            raid.centre.y + raid.radius - layout.height
        )
        portLabelFont.color = Color.BLACK
    }

    private fun drawPirateSeaLabel() {
        val origin = PirateSea.fogOrigin
        val scale = portLabelWorldHeight() * PIRATE_SEA_LABEL_SCALE / portLabelBaseLineHeight
        portLabelFont.data.setScale(scale)
        portLabelFont.color = PIRATE_SEA_LABEL_COLOR
        val layout = GlyphLayout(portLabelFont, PirateSea.NAME)
        portLabelFont.draw(
            batch,
            PirateSea.NAME,
            origin.x + PirateSea.fogWidth / 2f - layout.width / 2f,
            origin.y + PirateSea.fogHeight / 2f + layout.height / 2f
        )
        portLabelFont.color = Color.BLACK
    }

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

    /** Every active captain's ship, drawn along its actual sailable route (see
     *  [fleetShipTransform]) with a small portrait badge so it reads as crewed-by-someone rather
     *  than just another AI ship. */
    private fun drawFleetShips() {
        for (captain in player.captains) {
            if (captain.isIdle) continue
            val ownedShip = player.ownedShips.find { it.id == captain.shipId } ?: continue
            val (pos, heading) = world.fleetShipTransform(captain) ?: continue

            val texture = ShipArt.texture(ownedShip.shipType.spritePath)
            val width = BASE_SHIP_SPRITE_WIDTH * ownedShip.shipType.spriteScale * FLEET_SHIP_SPRITE_SCALE
            val height = width * (texture.height.toFloat() / texture.width.toFloat())

            batch.draw(
                texture,
                pos.x - width / 2f, pos.y - height / 2f,
                width / 2f, height / 2f,
                width, height,
                1f, 1f,
                heading - 90f + ownedShip.shipType.spriteRotationDeg,
                0, 0, texture.width, texture.height,
                false, false
            )

            val badgeSize = FLEET_SHIP_CLICK_RADIUS
            batch.draw(
                IconFactory.portraitFor(captain.name.hashCode()),
                pos.x + width * 0.2f, pos.y + height * 0.2f,
                badgeSize, badgeSize
            )
        }
    }

    private fun drawAiShips() {
        for (ship in world.aiShips) {
            val texture = ShipArt.texture(ship.spritePath)
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
        val texture = ShipArt.texture(shipType.spritePath)
        val width = BASE_SHIP_SPRITE_WIDTH * shipType.spriteScale
        val height = width * (texture.height.toFloat() / texture.width.toFloat())

        batch.color = shipType.tint
        batch.draw(
            texture,
            world.shipPosition.x - width / 2f, world.shipPosition.y - height / 2f,
            width / 2f, height / 2f,
            width, height,
            1f, 1f,
            world.shipHeadingDeg - 90f + shipType.spriteRotationDeg,
            0, 0, texture.width, texture.height,
            false, false
        )
        batch.color = Color.WHITE
    }

    private fun drawMinimap() {
        // The frame around the minimap is painted art now (HudOverlayUI's chart), so there is no
        // drawn border here any more - the map is rendered straight into the hole in that picture,
        // and the hole tells us where. Its aspect is not exactly the map's, so the map is fitted
        // inside and centred rather than stretched to fill.
        val window = hudOverlayUI.minimapWindow()
        val mapAspect = MapProjection.MAP_WIDTH / MapProjection.MAP_HEIGHT
        var w = window.width
        var h = w / mapAspect
        if (h > window.height) {
            h = window.height
            w = h * mapAspect
        }
        val mmX = (window.x + (window.width - w) / 2f).toInt()
        val mmY = (window.y + (window.height - h) / 2f).toInt()
        // Remembered for jumpToMinimapPoint: this is the exact patch of screen the whole world is
        // drawn into, which is what turns a tap on the chart back into a place in the world.
        minimapMapRect.set(mmX.toFloat(), mmY.toFloat(), w, h)

        Gdx.gl.glViewport(mmX, mmY, w.toInt(), h.toInt())

        batch.projectionMatrix = minimapCamera.combined
        batch.begin()
        batch.draw(mapTexture, 0f, 0f, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT)
        batch.end()

        // Who is where: the player in red, each captain out on a route in orange. The radius is
        // given in minimap PIXELS and converted, not in world units - the chart is the whole
        // Caribbean squeezed into a couple of hundred pixels, so a world-unit radius that looked
        // right at one window size would be a speck or a blob at another.
        val worldPerMinimapPixel = MapProjection.MAP_WIDTH / minimapMapRect.width
        shapeRenderer.projectionMatrix = minimapCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (captain in player.captains) {
            if (captain.isIdle) continue
            val pos = world.fleetShipTransform(captain)?.first ?: continue
            minimapDot(pos.x, pos.y, MINIMAP_FLEET_DOT, worldPerMinimapPixel)
        }
        minimapDot(world.shipPosition.x, world.shipPosition.y, MINIMAP_PLAYER_DOT, worldPerMinimapPixel)
        shapeRenderer.end()

        shapeRenderer.projectionMatrix = minimapCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.RED
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        shapeRenderer.rect(camera.position.x - halfW, camera.position.y - halfH, halfW * 2f, halfH * 2f)
        shapeRenderer.end()

        viewport.apply()
    }

    /** One ship marker on the chart: a dark rim under a coloured core, so it stays legible over
     *  both the pale shallows and the dark deep water. */
    private fun minimapDot(x: Float, y: Float, colour: Color, worldPerPixel: Float) {
        shapeRenderer.color = MINIMAP_DOT_RIM
        shapeRenderer.circle(x, y, (MINIMAP_DOT_RADIUS_PX + 1.2f) * worldPerPixel, 12)
        shapeRenderer.color = colour
        shapeRenderer.circle(x, y, MINIMAP_DOT_RADIUS_PX * worldPerPixel, 12)
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        fogTexture.dispose()
        radialTexture.dispose()
        portLabelFont.dispose()
        mapTexture.dispose()
        ShipArt.dispose()
        PortFrame.dispose()
        portHubUI.dispose()
        marketUI.dispose()
        shipyardUI.dispose()
        shipStatusUI.dispose()
        fortressUI.dispose()
        warehouseUI.dispose()
        tavernUI.dispose()
        fleetDockUI.dispose()
        combatUI.dispose()
        mainMenuUI.dispose()
        newGameUI.dispose()
        loadGameUI.dispose()
        saveGameUI.dispose()
        settingsUI.dispose()
        eventToastUI.dispose()
        hudOverlayUI.dispose()
        HudArt.dispose()
        propertyUI.dispose()
        captainStatusUI.dispose()
        AudioManager.dispose()
        IconFactory.dispose()
        UiSkinFactory.disposeSharedFont()
    }
}
