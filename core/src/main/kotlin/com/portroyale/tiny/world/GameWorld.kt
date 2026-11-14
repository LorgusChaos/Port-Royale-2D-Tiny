package com.portroyale.tiny.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.combat.FleetCombatResolver
import com.portroyale.tiny.combat.FleetCombatResult
import com.portroyale.tiny.diagnostics.GameLog
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.diagnostics.WorldWatchdog
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.FleetSimulation
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.OwnedShip
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.MilitaryStance
import com.portroyale.tiny.factions.militaryStance
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

/** How often the invariant checks sweep the world - see WorldWatchdog. */
private const val WATCHDOG_INTERVAL_SECONDS = 5f
private const val FIRST_RAID_DELAY_SECONDS = 60f
private val PORT_SAFE_RADIUS = WorldScale.PORT_SAFE_RADIUS

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

// Shared AI helpers (review batch 2, step 2). The ones without `private` are read by the game
// as well, until the code that reads them moves here too.
const val AI_SHIP_ARRIVE_THRESHOLD = 16f
/** How fast a destination's appeal falls off with distance from a ship's anchor, in world units.
 *  Bigger means flatter, and flat is the old aimless wandering. */
private const val HOME_WATERS_SCALE = 500f
/** How often a ship ignores its own flag entirely when choosing where to go - what keeps home
 *  waters a bias rather than a border. */
private const val FOREIGN_PORT_CHANCE = 0.12f
/** Where a pirate loiters, as a multiple of a port's safe radius: outside it, but within sight. */
private const val PROWL_RADIUS_MULTIPLIER = 1.25f
/** How far along its opening route a ship may be placed at start-up. Not 0 or 1: a ship exactly
 *  at either end is a ship in harbour, which is the look this avoids. */
private const val SCATTER_MIN_PROGRESS = 0.08f
private const val SCATTER_MAX_PROGRESS = 0.92f
/** Tries at finding somewhere a ship can actually be given a route to - see retargetAiShip. */
private const val RETARGET_ATTEMPTS = 4
/** How wide a stretch of sea a cruising pirate calls its ground. */
const val CRUISING_GROUND_RADIUS = 320f
/** How long a pirate works its ground before going home. Long enough to be worth the passage
 *  out, short enough that the fleet keeps redistributing itself over a session. */
const val CRUISE_MIN_SECONDS = 4 * 60f
const val CRUISE_MAX_SECONDS = 8 * 60f
/** Tries at finding a sailable spot inside a raid before settling for its centre. */
private const val PROWL_PLACEMENT_ATTEMPTS = 8
/** Ports within this range of each other make for water someone actually sails. */
const val BUSY_CORRIDOR_RADIUS = 900f
// Scaled with the M14b port roster (12 -> 28 harbours) to hold roughly the pre-expansion density
// of ships per port - otherwise the original 16 hulls spread over more than twice the coastline
// and the sea reads as deserted. Rough by intent; real numbers come out of long play tests.
//
// Of the three, the military count is the one most likely to need trimming: M14 made a faction's
// navy react in graded steps, so a player who lets their standing slide now meets far more of
// these than the old single hostile threshold ever produced.
const val TRADE_SHIPS_PER_FACTION = 4
const val MILITARY_SHIPS_PER_FACTION = 3
const val PIRATE_SHIP_COUNT = 8

// Pirate raids (review batch 2, step 3).
/** How many pirates a raid pulls in. Enough to make a corridor genuinely risky, few enough that
 *  it stays passable - ten would simply close the route. */
private const val RAID_PIRATE_COUNT = 3
private const val NAVAL_WAVE_BASE = 2
private const val NAVAL_WAVE_MAX = 5
/** How far around the midpoint of the two ports counts as the raid, on top of half their
 *  separation. */
private const val RAID_RADIUS_MARGIN = 200f
private const val RAID_MAX_RADIUS = 450f
/** The second port is drawn from this many nearest neighbours of the first, so a raid sits on a
 *  stretch someone would plausibly sail rather than between two random dots. */
private const val RAID_NEIGHBOUR_POOL = 5
private const val RAID_MIN_SECONDS = 8 * 60f
private const val RAID_MAX_SECONDS = 12 * 60f
/** Long on purpose: the point of a raid is that a corridor is dangerous for a while - long enough
 *  for a captain's route to run into it - not that the player catches a skirmish. */
private const val RAID_RESPONSE_MIN_SECONDS = 90f
private const val RAID_RESPONSE_MAX_SECONDS = 180f
private const val RAID_GAP_MIN_SECONDS = 8 * 60f
private const val RAID_GAP_MAX_SECONDS = 15 * 60f
/** How close every member must be before a band counts as formed up and sets out. */
private const val RAID_RALLY_TOLERANCE = 260f
/** Wake points of separation between one ship in the line and the next. */
private const val FORMATION_TRAIL_WAYPOINTS = 2
/** How far the leader sails before dropping another breadcrumb, and how many it keeps. Spacing
 *  has to be short enough that steering from one point to the next never cuts a corner across
 *  land, which is the entire reason a wake is safer than a shared route. */
private const val WAKE_SPACING = 22f
private const val WAKE_MAX_POINTS = 96
/** How far off the line a straggler may be before it stops steering at its station and asks for
 *  a proper route back to it. */
private const val FORMATION_REJOIN_DISTANCE = 220f
private const val FORMATION_REJOIN_INTERVAL = 1f

// AI ship update, the watchdog and encounters with the player (review batch 2, step 4).
/** Where hulls touch and shooting starts - see Encounters.CONTACT_RADIUS for why this is now
 *  under a ship length rather than the two it used to be. */
private val AI_SHIP_ENGAGE_RADIUS = Encounters.CONTACT_RADIUS
// Every ship sees exactly as far as every other - see Encounters.SIGHT_RADIUS. Comfortably
// larger than the range at which hulls touch, so an outmatched pirate notices the player and
// starts running well before combat can trigger.
private val PIRATE_FLEE_AWARENESS_RADIUS = Encounters.SIGHT_RADIUS
/** How long a pursuer will keep pushing against land before writing the chase off - see
 *  updatePursuit. Long enough to round a small obstruction, short enough that a ship never looks
 *  parked. */
private const val PURSUIT_BLOCKED_GIVE_UP_SECONDS = 3f
/** How close two AI ships have to be before they start easing away from each other - a full
 *  Galleon length, see WorldScale.HULL_CLEARANCE. */
private val SHIP_SEPARATION_RADIUS = WorldScale.HULL_CLEARANCE
/** See WorldScale for both of these and why they are bounded by hull speed. */
private val SHIP_SEPARATION_STRENGTH = WorldScale.SEPARATION_DRIFT
private val SEPARATION_SPEED_FRACTION = WorldScale.SEPARATION_SPEED_FRACTION
/** How much of the arrival radius counts as "on final approach", where separation stops applying.
 *  Has to exceed SHIP_SEPARATION_RADIUS / AI_SHIP_ARRIVE_THRESHOLD, or crowding could still hold a
 *  ship just outside its own destination. */
private const val DOCKING_APPROACH_MULTIPLIER = 3f
// --- M11d: pirate raids ---
/** Warships fight boldly and are averagely handy - AiShip has no manoeuvre stat of its own, so
 *  these stand in for one until ship armament is reworked in M10c. */
private const val RAID_WARSHIP_MANEUVER = 55
private const val RAID_WARSHIP_CAUTION = 0.25f
/** Pirates press an attack on a merchantman harder than a navy presses a pirate - they are there
 *  for the cargo, not to keep order. */
private const val RAID_PIRATE_CAUTION = 0.2f
/** Seconds between a hunter looking around for a target. */
private const val HUNT_DECISION_INTERVAL = 2.5f
/** How long a ship that could not be given a route waits before asking again. */
private const val RETARGET_RETRY_SECONDS = 0.5f
/** Headings to try, in degrees off the line home, when working out of a pocket - see findWayOut. */
private val STRAND_ESCAPE_ANGLES = intArrayOf(0, 30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180)
private const val AI_REPAIR_SECONDS = 150f
private const val RAID_ATTACK_COOLDOWN_MIN = 20f
private const val RAID_ATTACK_COOLDOWN_MAX = 40f
/** How far a lookout sees a sail - the same distance for everyone, see Encounters.SIGHT_RADIUS. */
private val LOOKOUT_RADIUS = Encounters.SIGHT_RADIUS
/** How wide a berth a re-laid course gives the threat. */
private const val THREAT_AVOID_RADIUS = 140f
/** Seconds between lookout checks - pathfinding is not free, and a ship does not need to
 *  reconsider its whole route sixty times a second. */
private const val THREAT_CHECK_INTERVAL = 2f
/**
 * How little of the way a hull could have made in one sweep counts as "not moving".
 *
 * It used to be a flat two world units, which looked generous and was not: the sweep itself was
 * counted in *game* seconds, so at 20x speed it came round every quarter of a real second, and
 * any hull that paused for fifteen frames - coming about, waiting its turn for a course - was
 * filed as stuck. A hundred and ten of a hundred and thirty-seven lines in a session log were
 * this one check, which is the state a diagnostic is in when it has stopped being one.
 *
 * Measuring against the distance the hull *could* have covered makes the test mean the same
 * thing at every game speed, and at a tenth it answers the question actually worth asking: is
 * this ship getting anywhere at all?
 */
private const val WATCHDOG_STUCK_FRACTION = 0.1f
/** What the fleet should always add up to, since every loss is replaced - see runWatchdog. */
private const val EXPECTED_FLEET_SIZE =
    (TRADE_SHIPS_PER_FACTION + MILITARY_SHIPS_PER_FACTION) * 4 + PIRATE_SHIP_COUNT
private const val REP_LOSS_TRADE = -8
private const val REP_LOSS_MILITARY = -15
// M10b persistence (see PortRoyaleGame.giveUpCooldownFor): a ship that just failed to catch its
// target either peels off for a while (CAUTIOUS) or comes straight back (RELENTLESS) - military
// hostility reuses reputation, a pirate reuses the tier gap in lieu of a separate fame stat
// (Project design/03-combat-threat-model.md recommends skipping that extra metric for the MVP).
private const val GIVE_UP_COOLDOWN_CAUTIOUS = 120f
private const val GIVE_UP_COOLDOWN_RELENTLESS = 15f
private const val PIRATE_RELENTLESS_TIER_GAP = 2
// M11: once pursuing, only gives up on a target more than PIRATE_FLEE_AWARENESS_RADIUS times this
// away - a little hysteresis past the noticing radius itself so pursuit doesn't flicker on/off
// for a target sitting right at the boundary.
private const val PURSUIT_GIVE_UP_MULTIPLIER = 1.5f

// How wide a berth a captain's detour gives a threat it's steering around (see
// PortRoyaleGame.evadeIfNeeded) - bigger than AI_SHIP_ENGAGE_RADIUS on purpose, a route that
// only just skirts engage range isn't really avoiding anything.
private const val FLEET_DETOUR_AVOID_RADIUS = 100f

private const val ARRIVE_THRESHOLD = 14f
/** Game minutes a captain waits in a shelter port before sailing on regardless (REVIEW.md, finding 31). */
const val SHELTER_MAX_GAME_MINUTES = 60f
/** Longest single move of the player's ship before water is checked again: half a map cell. */
private const val PLAYER_MOVE_SLICE = 5f

private val BOTTLE_MESSAGES = listOf(
    "You find a bottle bobbing in the waves. A note inside reads: 'Fair winds to whoever finds this. - A forgotten sailor'",
    "A bottle of rum drifts past your hull. You drink it. Nothing else happens.",
    "An old bottle, corked and sealed with wax. Inside: a faded, illegible scrap of a map.",
    "You fish a bottle out of the water. The note inside has dissolved to an unreadable smear."
)

enum class NavMode { MANUAL, AUTOPILOT }

/** Caches the actual sailable route for a captain's CURRENT leg, so a fleet ship follows the
 *  coastline instead of cutting straight across land - recomputed only when the leg's origin/
 *  destination changes (a leg completing and flipping direction, or a redirected route), not
 *  every frame. [avoidedThreat], when set, marks this entry as a hand-pinned evasion path
 *  (see evadeIfNeeded) rather than a plain port-to-port lookup - lets updateFleetEncounters
 *  tell "already reacting to this exact threat" from "haven't reacted yet" without a second
 *  map. A shelter trip uses "" as [origin] (no named route port is its start - see
 *  evadeIfNeeded), which can never collide with a real port name. */
class FleetPathEntry(
    val origin: String,
    val dest: String,
    val path: List<Vector2>,
    val avoidedThreat: AiShip? = null
)

/**
 * The world at sea, apart from the screen that shows it: AI ships, raids, sea events, the fleet's
 * routes, the watchdog, and the player's own hull. Built from the map, the ports and the player;
 * talks to the player only through [events].
 *
 * Being moved out of PortRoyaleGame block by block (Project design/decisions/review-batch-2.md).
 * All of the state lives here already; the logic follows one block per step, and until it does the
 * game reads and writes these fields through forwarding properties under their old names.
 *
 * Nothing in this file may touch Gdx, Scene2D, sound, textures or the game class itself -
 * GameWorldImportsTest holds it to that. Vector2 and MathUtils are fine: they run without a window.
 */
class GameWorld(
    val worldMap: WorldMap,
    val ports: List<ResolvedPort>,
    val player: Player,
    private val events: WorldEvents
) {
    val aiShips = mutableListOf<AiShip>()
    var huntedShip: AiShip? = null

    val watchdog = WorldWatchdog(WATCHDOG_INTERVAL_SECONDS)
    /** Where each AI ship was at the previous watchdog sweep, for the "has not moved" check. */
    val lastWatchdogPositions = mutableMapOf<AiShip, Vector2>()
    /** Sailing time since the last sweep. The sweep comes round on real seconds - a tester's
     *  patience is real - while how far a hull should have got is a question about game time. */
    var watchdogWorldSeconds = 0f
    var activeRaid: PirateRaid? = null
    /** The first raid of a session comes early on purpose. A new game otherwise spends eight
     *  quiet minutes before the world does anything at all, which reads as nothing happening
     *  rather than as a lull - and makes the feature almost impossible to check. Later gaps use
     *  the real interval. */
    var secondsUntilNextRaid = FIRST_RAID_DELAY_SECONDS

    /** Routes still affordable this frame - see routeTo. */
    var routeBudget = 0

    /** Which ship the fleet sweep starts from this frame, so the rationing of courses comes round
     *  to everyone rather than always falling on the same hulls - see updateAiShips. */
    var fleetScanOffset = 0
    /** How many warships each power will commit to the next raid. Rises when a squadron is wiped
     *  out, resets when one comes home. Per faction on purpose: they act independently. */
    val navalEscalation = mutableMapOf<Faction, Int>()
    var currentAiTarget: AiShip? = null
    /** The ship the player is being asked about ("Fight / Sail On"), until they answer - a
     *  captain must not sink it while they choose (REVIEW.md, finding 7). */
    var pendingEncounter: AiShip? = null

    /** Ships already reported as having no way out, so a map problem is mentioned once rather
     *  than sixty times a second - see findWayOut. */
    val boxedInReported = mutableSetOf<Int>()

    // Fixed-size pool of invisible random-event locations scattered across the water; each slot
    // independently holds a live point or counts down to respawning one somewhere else (see
    // updateSeaEvents()). A castaway event sets passengerDestination until delivered.
    val seaEventSlots = List(SEA_EVENT_POOL_SIZE) { SeaEventSlot() }
    var passengerDestination: ResolvedPort? = null

    val shipPosition = Vector2()
    var shipHeadingDeg = 0f

    var navMode = NavMode.MANUAL
    var path: List<Vector2> = emptyList()
    var pathIndex = 0
    var destinationPort: ResolvedPort? = null

    val fleetPaths = mutableMapOf<Captain, FleetPathEntry>()
    /** (captain, threat) pairs already logged as having no course away (see reportNoCourse). */
    private val noCourseReported = mutableSetOf<Pair<Captain, AiShip>>()

    /** Ports (and the water right around them) are a safe harbor - no encounter can trigger
     *  there, so leaving a port never dumps the player straight into an ambush from whatever
     *  ship happened to be passing that waypoint at the time. */
    fun isNearAnyPort(pos: Vector2): Boolean =
        ports.any { it.worldPos.dst(pos) < PORT_SAFE_RADIUS }

    /** One tick of the fixed-size sea-event pool: live points check for contact with the player,
     *  spent points count down to respawning somewhere new. Deliberately not distance-culled to
     *  "nearby" points only - the pool is small (SEA_EVENT_POOL_SIZE), so checking all of it every
     *  frame is cheap. */
    fun updateSeaEvents(delta: Float) {
        for (slot in seaEventSlots) {
            val point = slot.point
            if (point != null) {
                // With a passenger already aboard, a second castaway stays in the water until the
                // first is delivered: picking them up used to overwrite the first passenger, who
                // vanished with their reward (REVIEW.md, finding 9).
                val berthTaken = point.type == SeaEventType.CASTAWAY && passengerDestination != null
                if (!berthTaken && shipPosition.dst(point.position) < SEA_EVENT_TRIGGER_RADIUS) {
                    triggerSeaEvent(slot, point)
                }
            } else {
                slot.respawnTimer -= delta
                if (slot.respawnTimer <= 0f) spawnSeaEvent(slot)
            }
        }
    }

    fun spawnSeaEvent(slot: SeaEventSlot) {
        var candidate: Vector2? = null
        var attempt = 0
        while (candidate == null && attempt < SEA_EVENT_SPAWN_ATTEMPTS) {
            val x = GameRandom.nextFloat(0f, MapProjection.MAP_WIDTH)
            val y = GameRandom.nextFloat(0f, MapProjection.MAP_HEIGHT)
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
        // A castaway is only offered while the player isn't already carrying one, and one already
        // in the water waits for the berth (see updateSeaEvents), so there's never ambiguity
        // about which port a "deliver the castaway" arrival refers to.
        val availableTypes = if (passengerDestination == null) {
            SeaEventType.entries
        } else {
            SeaEventType.entries.filter { it != SeaEventType.CASTAWAY }
        }
        slot.point = SeaEventPoint(position, GameRandom.pick(availableTypes))
    }

    private fun triggerSeaEvent(slot: SeaEventSlot, point: SeaEventPoint) {
        when (point.type) {
            SeaEventType.BOTTLE -> events.toast(GameRandom.pick(BOTTLE_MESSAGES), ToastIcon.Bottle)
            SeaEventType.CHEST_GOLD -> {
                val amount = GameRandom.nextIntInclusive(CHEST_GOLD_MIN, CHEST_GOLD_MAX)
                player.gold += amount
                events.toast("You spot a floating chest and haul it aboard - ${amount}g inside!", ToastIcon.Coin)
            }
            SeaEventType.CHEST_GOODS -> {
                if (player.cargoUsed >= player.cargoCapacity) {
                    player.gold += CHEST_FALLBACK_GOLD
                    events.toast(
                        "You spot a floating chest, but your hold is full - you salvage only ${CHEST_FALLBACK_GOLD}g worth of loose coin from it.",
                        ToastIcon.Coin
                    )
                } else {
                    val good = GameRandom.pick(Good.entries)
                    val qty = player.takeAboard(good, GameRandom.nextIntInclusive(CHEST_GOODS_MIN_QTY, CHEST_GOODS_MAX_QTY))
                    events.toast("A half-sunken crate yields $qty ${good.displayName}!", ToastIcon.Cargo(good))
                }
            }
            SeaEventType.CASTAWAY -> {
                val destination = GameRandom.pick(ports)
                passengerDestination = destination
                events.toast(
                    "You pull a half-drowned sailor from the water. They beg you to take them to ${destination.port.name}.",
                    ToastIcon.Castaway
                )
            }
        }
        slot.point = null
        slot.respawnTimer = GameRandom.nextFloat(SEA_EVENT_RESPAWN_MIN, SEA_EVENT_RESPAWN_MAX)
    }

    /** The castaway's part of arriving in [port]: if this is where they asked to go, they pay and
     *  leave. Called first thing on arrival, before the port screen opens - the order the game
     *  has always shown it in. */
    fun deliverPassenger(port: ResolvedPort) {
        if (port === passengerDestination) {
            val reward = GameRandom.nextIntInclusive(CASTAWAY_REWARD_MIN, CASTAWAY_REWARD_MAX)
            player.gold += reward
            passengerDestination = null
            events.toast(
                "You deliver the castaway to ${port.port.name}. Grateful, they press ${reward}g into your hand before disappearing into the crowd.",
                ToastIcon.Coin
            )
        }
    }

    // --- Shared AI helpers: spawning and replacing ships, where they go next and the routes
    // that take them there, who is hostile to whom, and where a captain's ship is on its leg.
    // Moved from PortRoyaleGame as they were (review batch 2, step 2). ---

    /** One trade + one military ship per faction patrolling that faction's own ports, plus a
     *  handful of pirates wandering between any port - a rough population, not simulated economy. */
    /** Every ship is anchored where it is born: a faction ship to the harbour it sailed out of, a
     *  pirate to the lair. That anchor is what gives it home waters - see [retargetAiShip]. */
    fun spawnAiShips() {
        for (faction in Faction.entries) {
            val homePorts = ports.filter { it.port.faction == faction }
            if (homePorts.isEmpty()) continue
            repeat(TRADE_SHIPS_PER_FACTION) {
                val home = GameRandom.pick(homePorts).approachPoint
                aiShips.add(AiShipFactory.trade(faction, home.cpy()).anchoredAt(home))
            }
            repeat(MILITARY_SHIPS_PER_FACTION) {
                val home = GameRandom.pick(homePorts).approachPoint
                aiShips.add(AiShipFactory.military(faction, home.cpy()).anchoredAt(home))
            }
        }
        val lair = pirateLairAnchor()
        // Not all eight at the lair. Pirates return home and are replaced there, so seeding them
        // there too meant a permanent crowd in the north-west corner and empty water everywhere
        // else - the map's whole upper quarter thick with black sails and the rest untroubled.
        // They begin where they would be after a passage out: already on station, spread over the
        // busy corridors, with part of a cruise behind them.
        repeat(PIRATE_SHIP_COUNT) { aiShips.add(pirateOnStation()) }
        // Start-up is not a drawn frame, and the world is built before any frame has granted a
        // course: under the per-frame allowance every ship was refused its route here, and the
        // game opened with them all in harbour (finding 29). One search per ship, once, at load is
        // the price scatterAlongRoutes always meant to pay; the first frame's allowance is untouched.
        val frameAllowance = routeBudget
        routeBudget = Int.MAX_VALUE
        scatterAlongRoutes()
        routeBudget = frameAllowance
        GameLog.log(
            GameLog.Category.AI,
            "spawned ${aiShips.size} ships; pirate lair at ${lair.x.toInt()}, ${lair.y.toInt()}"
        )
    }

    /**
     * Starts every ship partway through a voyage it is already making, rather than sitting in
     * harbour waiting for the first frame. Otherwise the world opens with thirty-six ships casting
     * off in unison - which reads as a simulation being switched on, not as a sea that was busy
     * before the player showed up.
     *
     * Done by giving each ship a real route and then dropping it somewhere along it, instead of
     * simulating a few minutes of movement at load time: one pathfinding call per ship either way,
     * but no stall while the game runs the world at ten times speed before showing anything.
     */
    private fun scatterAlongRoutes() {
        for (ship in aiShips) {
            retargetAiShip(ship)
            if (ship.path.isEmpty()) continue

            // Half the warships and pirates are on the way BACK. Reversing the route works
            // because it is the same chain of water cells read the other way; the ship then
            // arrives at its anchor and carries on normally. outbound goes back to true because
            // it means "the next leg heads out", and retargetAiShip has already flipped it.
            if (ship.role != AiShipRole.TRADE && GameRandom.nextFloat() < 0.5f) {
                ship.path = ship.path.reversed()
                ship.outbound = true
            }

            val progress = GameRandom.nextFloat(SCATTER_MIN_PROGRESS, SCATTER_MAX_PROGRESS)
            val index = ((ship.path.size - 1) * progress).toInt().coerceIn(0, ship.path.size - 1)
            ship.pathIndex = index
            ship.position.set(ship.path[index])
            ship.path.getOrNull(index + 1)?.let { next ->
                ship.headingDeg = MathUtils.atan2(next.y - ship.position.y, next.x - ship.position.x) *
                    MathUtils.radDeg
            }
        }
    }

    private fun AiShip.anchoredAt(point: Vector2): AiShip {
        anchor = point.cpy()
        return this
    }

    /** The lair itself, snapped to sailable water. It was measured to be open sea, but the map is
     *  due to be redrawn in M16 and a hand-drawn coastline could move under it. */
    /** A pirate already at work somewhere worth working, with part of its cruise behind it.
     *  Falls back to the lair only if no ground can be found at all. */
    private fun pirateOnStation(): AiShip {
        val ground = pickCruisingGround()
        val start = ground ?: pirateBerth()
        val ship = AiShipFactory.pirate(start.cpy()).anchoredAt(start)
        if (ground != null) {
            ship.voyage = PirateVoyage.CRUISING
            ship.cruisingGround = ground.cpy()
            ship.cruiseSecondsLeft = GameRandom.nextFloat(CRUISE_MIN_SECONDS * 0.2f, CRUISE_MAX_SECONDS)
        }
        return ship
    }

    fun pirateLairAnchor(): Vector2 {
        val lair = PirateSea.lair
        return worldMap.findNearestWater(lair.x, lair.y) ?: lair
    }

    /** A berth in the anchorage: pirates ride at anchor spread across it rather than stacked on
     *  one coordinate, which is what made them look like they were pecking at each other. */
    private fun pirateBerth(): Vector2 {
        repeat(PROWL_PLACEMENT_ATTEMPTS) {
            val candidate = PirateSea.berth(
                GameRandom.nextFloat(0f, MathUtils.PI2),
                GameRandom.nextFloat(0f, 1f)
            )
            worldMap.findNearestWater(candidate.x, candidate.y)?.let { return it }
        }
        return pirateLairAnchor()
    }

    /** Where a raid needs this ship to be, or null if it is not in one. */
    private fun raidDestinationFor(ship: AiShip): Vector2? {
        val raid = activeRaid ?: return null
        if (ship in raid.band) {
            return when (raid.phase) {
                RaidPhase.GATHERING -> pirateBerth()
                RaidPhase.SAILING -> raid.centre
                // On station the band opens out and works the whole corridor. Sending them to the
                // centre itself parks all three on one spot with nothing left to sail to, which
                // is both wrong (a raid is a patrolled stretch, not a picket) and what the
                // watchdog kept reporting as pirates that had stopped moving.
                RaidPhase.RAIDING -> prowlPointInside(raid)
            }
        }
        if (raid.squadrons.values.any { ship in it }) {
            return if (raid.contains(ship.position)) prowlPointInside(raid) else raid.centre
        }
        return null
    }

    /** Somewhere to be inside the raid - sailable, and actually within the circle rather than
     *  snapped to water outside it by a coastline. */
    private fun prowlPointInside(raid: PirateRaid): Vector2 {
        repeat(PROWL_PLACEMENT_ATTEMPTS) {
            val angle = GameRandom.nextFloat(0f, MathUtils.PI2)
            val distance = GameRandom.nextFloat(raid.radius * 0.25f, raid.radius * 0.9f)
            val candidate = Vector2(
                raid.centre.x + MathUtils.cos(angle) * distance,
                raid.centre.y + MathUtils.sin(angle) * distance
            )
            worldMap.findNearestWater(candidate.x, candidate.y)
                ?.takeIf { raid.contains(it) && !isNearAnyPort(it) }
                ?.let { return it }
        }
        return raid.centre
    }

    /** Ends a chase. [rest] marks a genuine give-up, which buys the rest of the world a long
     *  breather from this ship - a break-off forgotten in five seconds is not a break-off. */
    fun breakOffChase(hunter: AiShip, rest: Boolean) {
        if (hunter.quarry == null) return
        hunter.quarry = null
        hunter.chaseSeconds = 0f
        hunter.chaseStalledFor = 0f
        hunter.chaseBestRange = Float.MAX_VALUE
        if (rest) hunter.giveUpCooldown = Encounters.CHASE_BREAK_OFF_SECONDS
        // Its route now ends wherever the chase left off; ask for a proper one next frame.
        hunter.path = emptyList()
        hunter.pathIndex = 0
    }

    /** Working the ground: short hops around it rather than a picket on one spot, so a cruising
     *  pirate covers the water it came to cover. */
    private fun prowlNearGround(ship: AiShip): Vector2? {
        val ground = ship.cruisingGround ?: return null
        repeat(PROWL_PLACEMENT_ATTEMPTS) {
            val angle = GameRandom.nextFloat(0f, MathUtils.PI2)
            val distance = GameRandom.nextFloat(CRUISING_GROUND_RADIUS * 0.3f, CRUISING_GROUND_RADIUS)
            val spot = Vector2(
                ground.x + MathUtils.cos(angle) * distance,
                ground.y + MathUtils.sin(angle) * distance
            )
            if (worldMap.isWater(spot.x, spot.y) && !isNearAnyPort(spot)) return spot
        }
        return ground.cpy()
    }

    /** Somewhere worth working: weighted by how many other harbours are within sailing distance,
     *  because that is where the shipping is. Deliberately not weighted by distance from the lair
     *  - that weighting is what kept every pirate in the same stretch of water. */
    fun pickCruisingGround(): Vector2? {
        if (ports.isEmpty()) return null
        val port = GameRandom.pickWeighted(ports) { candidate ->
            1f + ports.count { it !== candidate && it.worldPos.dst(candidate.worldPos) < BUSY_CORRIDOR_RADIUS }
        }
        return prowlPointOutside(port)
    }

    /** Single-port factions (France, Netherlands) would otherwise have nowhere else to "patrol"
     *  to and just sit stuck re-targeting their own home port forever - fall back to any port,
     *  and always exclude wherever the ship already is, so it actually goes somewhere new. */
    /**
     * Where a ship goes next, and the reason the sea stopped looking aimless. This used to be one
     * uniform random pick among a faction's ports - and since M14b a faction spans the whole map,
     * so an English ship was as likely to cross to Belize as to potter around Barbados. Nothing
     * had a territory.
     *
     * Three behaviours now, one per role:
     *  - a trader goes port to port, weighted toward its own home waters (see [weightedPort]);
     *  - a warship patrols out and back, so its faction's waters are somewhere it visibly stays;
     *  - a pirate sorties from its lair to prowl a port's outskirts, then goes home again.
     */
    /**
     * Laying a course, subject to a per-frame allowance.
     *
     * Pathfinding is far and away the most expensive thing here, and the AI's appetite for it
     * scales with game speed while the budget to pay for it does not. Returning null when the
     * allowance is spent simply means "not this frame" - callers leave the ship on its present
     * course and ask again next time, which costs a few frames of staleness and buys a steady
     * frame rate at any speed.
     */
    fun routeTo(from: Vector2, to: Vector2): List<Vector2>? {
        if (routeBudget <= 0) return null
        routeBudget--
        return worldMap.findPath(from, to)
    }

    fun routeAvoiding(from: Vector2, to: Vector2, avoid: Vector2, radius: Float): List<Vector2>? {
        if (routeBudget <= 0) return null
        routeBudget--
        return worldMap.findPath(from, to, avoid, radius)
    }

    fun retargetAiShip(ship: AiShip) {
        // A ship in the middle of a chase has exactly one destination worth having.
        ship.quarry?.let { quarry ->
            routeTo(ship.position, quarry.position.cpy())?.let { route ->
                ship.path = route
                ship.pathIndex = 0
                ship.destination = quarry.position.cpy()
                ship.routeRelays++
            }
            return
        }
        // A ship taking part in a raid has somewhere it must be, and the ordinary weighting will
        // not take it there: an outbound pirate picks a port, so one anchored at the rally point
        // still spent half its time sailing away from it and the band never assembled.
        raidDestinationFor(ship)?.let { forced ->
            routeTo(ship.position, forced)?.let { route ->
                ship.path = route
                ship.pathIndex = 0
                ship.destination = forced.cpy()
                ship.routeRelays++
            }
            return
        }
        // Several attempts, because a destination can legitimately be no good: A* returns an
        // empty route for a place the ship is already sitting on, and the old code could not tell
        // that apart from "no route exists" - it left the ship with no route at all, which is a
        // ship that never moves again. The rescue in findWayOut could not help either, since a
        // ship parked on its own anchor has nowhere to back away towards. Warships idling at home
        // and pirates home from a cruise sat down permanently for exactly this reason.
        repeat(RETARGET_ATTEMPTS) {
            val destination = when (ship.role) {
                AiShipRole.TRADE -> weightedPort(ship)?.approachPoint
                AiShipRole.MILITARY -> if (ship.outbound) weightedPort(ship)?.approachPoint else ship.anchor
                // A pirate goes where its cruise says, not where the old home-waters weighting
                // pulled it - that weighting is precisely what kept the whole fleet in one corner.
                AiShipRole.PIRATE -> when (ship.voyage) {
                    PirateVoyage.TRANSIT -> ship.cruisingGround ?: pickCruisingGround().also { ship.cruisingGround = it }
                    PirateVoyage.CRUISING -> prowlNearGround(ship)
                    PirateVoyage.HOMEBOUND -> pirateBerth()
                }
            }
            if (destination == null || destination.dst(ship.position) <= AI_SHIP_ARRIVE_THRESHOLD * 2) {
                // Nowhere to go because it is already there. For a warship that means the leg is
                // over: homeward has reached home, so the next one is outward. Turning the leg
                // over only on a successful course - which is what this used to do - left a
                // warship that had just moored asking for a route to the spot it was moored on,
                // getting none, and lying there for the rest of the session while the watchdog
                // stayed silent because ships in harbour are not reported.
                if (ship.role == AiShipRole.MILITARY) ship.outbound = !ship.outbound
            } else {
                val newPath = routeTo(ship.position, destination)
                if (newPath.isNullOrEmpty()) {
                    // That cruising ground cannot be reached from here. Left set, it would be
                    // drawn again on every attempt for the rest of the session and the pirate
                    // would sit at its own front door for ever - which is exactly what two of
                    // them were caught doing. Drop it and pick another.
                    if (ship.role == AiShipRole.PIRATE && ship.voyage == PirateVoyage.TRANSIT) {
                        ship.cruisingGround = null
                    }
                } else {
                    ship.path = newPath
                    ship.pathIndex = 0
                    ship.destination = destination.cpy()
                    ship.routeRelays++
                    if (ship.role == AiShipRole.MILITARY) ship.outbound = !ship.outbound
                    return
                }
            }
        }
    }

    /**
     * Picks a port with a probability that falls off with distance from the ship's [AiShip.anchor]
     * - close to home is likely, the far side of the map is not, and nothing is impossible. A hard
     * radius was rejected: it draws a visible border on the map and can leave a ship at the edge
     * of its own territory with no candidates at all.
     *
     * [FOREIGN_PORT_CHANCE] of the time the faction filter is dropped entirely, which is what
     * keeps home waters a leaning rather than a fence - ships of every flag do turn up everywhere,
     * just not in equal numbers.
     */
    private fun weightedPort(ship: AiShip): ResolvedPort? {
        val ownWaters = ship.faction?.takeIf { GameRandom.nextFloat() >= FOREIGN_PORT_CHANCE }
            ?.let { faction -> ports.filter { it.port.faction == faction } }
            ?.takeIf { it.size > 1 }
        val pool = ownWaters ?: ports
        val candidates = pool.filter { it.approachPoint.dst(ship.position) > AI_SHIP_ARRIVE_THRESHOLD * 2 }
            .ifEmpty { ports }
        if (candidates.isEmpty()) return null
        return GameRandom.pickWeighted(candidates) { port ->
            val distance = port.approachPoint.dst(ship.anchor)
            val falloff = 1f + distance / HOME_WATERS_SCALE
            1f / (falloff * falloff)
        }
    }

    /**
     * A spot just outside a port's safe harbour - where a pirate can actually do something.
     * Sailing to the approach point itself, as they used to, put them where no encounter can
     * trigger by rule: half a pirate's patrol was spent being harmless.
     */
    private fun prowlPointOutside(port: ResolvedPort): Vector2? {
        val angle = GameRandom.nextFloat(0f, MathUtils.PI2)
        val radius = PORT_SAFE_RADIUS * PROWL_RADIUS_MULTIPLIER
        val target = Vector2(
            port.worldPos.x + MathUtils.cos(angle) * radius,
            port.worldPos.y + MathUtils.sin(angle) * radius
        )
        return worldMap.findNearestWater(target.x, target.y)
    }

    /** A pirate only picks a fight it can plausibly win: it engages a ship whose tier is at most
     *  its own, and otherwise flees on sight instead (see the flee movement branch in
     *  updateAiShips()) - a Brigantine-tier pirate ducks a Galleon-tier target, and a Galleon-
     *  tier pirate has no reason to duck a Brigantine-tier one. [targetTier] generalizes this
     *  over whoever might be encountering the pirate - the player (see checkAiEncounters) or a
     *  fleet captain's ship (see updateFleetEncounters), same comparison either way since a
     *  pirate has no idea who's steering. A faction's military only turns hostile once the
     *  player's reputation with that faction has soured (or a loan with them has gone overdue -
     *  see checkLoanOverdue) - shared across the whole fleet, not tracked per ship, since every
     *  hull still sails under the player's own flag. Trade ships are never auto-hostile to the
     *  player (has to deliberately click one to go after it - see [handleInput] / [huntedShip]),
     *  and by extension never hostile to a fleet captain either. */
    fun isHostile(ship: AiShip, targetTier: Int): Boolean {
        if (ship.role == AiShipRole.PIRATE) return ship.tier >= targetTier
        if (ship.role != AiShipRole.MILITARY) return false
        val faction = ship.faction ?: return false
        return when (militaryStance(player.reputation[faction] ?: 0)) {
            MilitaryStance.IGNORES -> false
            // Unfriendly, not yet at war: a patrol will bully a target it clearly outguns and
            // leave a well-armed one alone - the same judgement a pirate already makes.
            MilitaryStance.OPPORTUNISTIC -> ship.tier >= targetTier
            MilitaryStance.HUNTS -> true
        }
    }

    /** 1 (Sloop) through 5 (Galleon) matching ShipTypes.ALL's own order; a Wreck counts as the
     *  weakest possible tier (irrelevant in practice for the player - checkAiEncounters already
     *  exempts it from every encounter, hostile or not; a fleet ship is never a Wreck at all). */
    fun shipTier(shipType: ShipType): Int = (ShipTypes.ALL.indexOf(shipType) + 1).coerceAtLeast(1)

    /** Keeps the ambient population roughly constant - a sunk ship is replaced by a fresh one of
     *  the same role/faction rather than slowly depleting the world. */
    /** A replacement comes from where its kind lives, not from wherever there happens to be a
     *  port: a new pirate sails out of the Sea of Pirates like the one it replaces. */
    fun respawnReplacement(defeated: AiShip) {
        val spot = if (defeated.faction != null) {
            ports.filter { it.port.faction == defeated.faction }
                .let { if (it.isEmpty()) null else GameRandom.pick(it) }?.approachPoint
        } else {
            pirateLairAnchor()
        } ?: return
        if (defeated.role == AiShipRole.PIRATE) {
            // A replacement that always appears at the lair is how the north-west corner filled
            // up: every pirate lost anywhere on the map came back there and had to sail out
            // again. The trade goes on wherever it was going on.
            aiShips.add(pirateOnStation())
            return
        }
        val replacement = when (defeated.role) {
            AiShipRole.TRADE -> AiShipFactory.trade(defeated.faction!!, spot.cpy())
            AiShipRole.MILITARY -> AiShipFactory.military(defeated.faction!!, spot.cpy())
            AiShipRole.PIRATE -> AiShipFactory.pirate(spot.cpy())
        }
        replacement.anchor = spot.cpy()
        aiShips.add(replacement)
    }

    /** The water-adjacent point findPath should actually route to/from - a port's raw worldPos
     *  can sit on or right against land, same reason the player's own autopilot targets
     *  targetPort.approachPoint instead of the port marker itself (see handleInput()). Using
     *  worldPos here was the earlier bug: findPath couldn't start from a point it doesn't
     *  recognize as water, so it silently fell back to the straight-line-through-land path. */
    fun portApproachPos(name: String): Vector2? = ports.firstOrNull { it.port.name == name }?.approachPoint

    /** The real sailable route (A* around islands, same pathfinder as the player's own autopilot)
     *  between two ports by name - the single source of truth both [fleetPath]'s render cache and
     *  [fleetSeaDistance]'s timing calculation build on, so a fleet ship's on-screen speed and its
     *  actual arrival time can never disagree about how far apart two ports really are. */
    fun pathBetweenPorts(from: String, to: String): List<Vector2> {
        val originPos = portApproachPos(from) ?: return emptyList()
        val destPos = portApproachPos(to) ?: return emptyList()
        return worldMap.findPath(originPos, destPos)?.takeIf { it.isNotEmpty() } ?: listOf(originPos, destPos)
    }

    private fun fleetPath(captain: Captain): List<Vector2> {
        if (captain.shelterPort.isNotEmpty()) {
            val pinned = fleetPaths[captain]
            if (pinned != null && pinned.origin == "" && pinned.dest == captain.shelterPort) return pinned.path
            val pos = portApproachPos(captain.shelterPort) ?: return emptyList()
            return listOf(pos.cpy(), pos.cpy())
        }
        val origin = if (captain.headingToB) captain.portA else captain.portB
        val dest = if (captain.headingToB) captain.portB else captain.portA
        val existing = fleetPaths[captain]
        if (existing != null && existing.origin == origin && existing.dest == dest) return existing.path

        val computed = pathBetweenPorts(origin, dest)
        fleetPaths[captain] = FleetPathEntry(origin, dest, computed)
        return computed
    }

    /** A point [fraction] (0..1) of the way along a polyline's total length - constant speed
     *  along the whole path, not just "halfway through whichever segment index that'd naively
     *  land on". */
    private fun pointAlongPath(path: List<Vector2>, fraction: Float): Vector2 {
        if (path.isEmpty()) return Vector2()
        if (path.size == 1) return path[0].cpy()
        val segLengths = FloatArray(path.size - 1)
        var total = 0f
        for (i in 0 until path.size - 1) {
            val d = path[i].dst(path[i + 1])
            segLengths[i] = d
            total += d
        }
        if (total <= 0f) return path[0].cpy()
        var target = total * fraction.coerceIn(0f, 1f)
        for (i in segLengths.indices) {
            if (target <= segLengths[i] || i == segLengths.lastIndex) {
                val segFraction = if (segLengths[i] > 0f) (target / segLengths[i]).coerceIn(0f, 1f) else 0f
                val a = path[i]
                val b = path[i + 1]
                return Vector2(a.x + (b.x - a.x) * segFraction, a.y + (b.y - a.y) * segFraction)
            }
            target -= segLengths[i]
        }
        return path.last().cpy()
    }

    /** A fleet captain has no real simulated position (see Captain's class doc) - this derives
     *  one purely for rendering/click-detection, from [fleetPath] and [Captain.legProgress], plus
     *  a heading sampled a little further along the same path so the ship visually turns to
     *  follow the coastline instead of always facing straight at the final destination port. */
    fun fleetShipTransform(captain: Captain): Pair<Vector2, Float>? {
        val path = fleetPath(captain)
        if (path.size < 2) return null
        val progress = captain.legProgress(GameClock.totalGameMinutes)
        val pos = pointAlongPath(path, progress)
        val ahead = pointAlongPath(path, (progress + 0.02f).coerceAtMost(1f))
        val heading = if (ahead.dst(pos) > 0.01f) {
            MathUtils.atan2(ahead.y - pos.y, ahead.x - pos.x) * MathUtils.radDeg
        } else {
            0f
        }
        return pos to heading
    }

    // --- M11d: pirate raids -------------------------------------------------------------------

    /**
     * Runs the raid clock: starts one when the sea has been quiet long enough, brings the navies
     * in after a delay, lets the two sides fight where they meet, and winds the whole thing up.
     *
     * Everything here is anchor manipulation plus the combat resolver that already exists. There
     * is no raid-specific movement code at all - drafted ships simply live somewhere else for a
     * while and the weighting from M11c does the rest.
     */
    fun updateRaids(delta: Float) {
        val raid = activeRaid
        if (raid == null) {
            secondsUntilNextRaid -= delta
            if (secondsUntilNextRaid <= 0f) beginRaid()
            return
        }

        when (raid.phase) {
            RaidPhase.GATHERING -> {
                // Nothing is announced and no clock runs yet: a raid that is still assembling has
                // not happened, and telling the player now sends them out to look at empty sea.
                when (raid.gather(delta, aiShips, RAID_RALLY_TOLERANCE)) {
                    PirateRaid.Gathering.SET_OUT -> {
                        raid.phase = RaidPhase.SAILING
                        raid.band.forEach { it.anchor = raid.centre.cpy() }
                        GameLog.log(GameLog.Category.AI, "raid ${raid.label}: band formed up, setting out")
                    }
                    PirateRaid.Gathering.ABANDONED -> {
                        GameLog.log(GameLog.Category.AI, "raid ${raid.label}: nobody reached the rally point, called off")
                        endRaid(raid)
                        return
                    }
                    PirateRaid.Gathering.WAITING -> Unit
                }
            }
            RaidPhase.SAILING -> {
                keepFormation(raid, delta)
                val head = raid.leader(aiShips)
                if (head != null && head.position.dst(raid.centre) <= raid.radius) {
                    raid.phase = RaidPhase.RAIDING
                    announceRaid(raid)
                }
            }
            RaidPhase.RAIDING -> {
                if (!raid.responseSent) {
                    raid.responseDelay -= delta
                    if (raid.responseDelay <= 0f) sendNavalResponse(raid)
                }
                raid.squadrons.values.forEach { keepLineAhead(it, delta) }
                // No hunting logic of its own: the ships gathered here fight by the same standing
                // rules as everywhere else on the map (see updateEncounters). A raid supplies the
                // pirates and the reason a navy sails out, nothing more.
                raid.secondsRemaining -= delta
            }
        }

        // Ends early once the corridor is clear: "the activity has subsided" should not be a lie
        // told while three pirates are still working the route.
        if (raid.secondsRemaining <= 0f || raid.pirates(aiShips).isEmpty()) endRaid(raid)
    }

    private fun announceRaid(raid: PirateRaid) {
        raid.markAnnounced()
        GameLog.log(GameLog.Category.AI, "raid ${raid.label}: band on station, raiding begins")
        events.toast("Pirate activity reported between ${raid.label}. Trade there is unsafe.", ToastIcon.Debt)
    }

    /**
     * Line ahead: followers sail the leader's own path, a few waypoints behind it.
     *
     * They copy the route rather than steering to an offset beside the leader because the route
     * is a chain of water cells by construction, while an offset abeam runs a follower onto the
     * shore every time the band passes a headland. Trailing along the same track is also what a
     * squadron actually looked like.
     */
    private fun keepFormation(raid: PirateRaid, delta: Float) {
        val head = raid.leader(aiShips) ?: return
        if (head.path.isEmpty() || head.pathIndex >= head.path.size) retargetAiShip(head)
        keepLineAhead(listOf(head) + raid.followers(aiShips), delta)
    }

    /** Puts [ships] in line ahead behind the first of them still afloat. */
    /**
     * Puts [ships] in line astern behind the first of them still afloat - by having each follow
     * the water the leader has *already sailed through*, not the route the leader is about to.
     *
     * The difference is not academic; it cost a raid a third of its strength. Followers used to
     * be handed the leader's current route and dropped into it a few waypoints back. That route
     * begins wherever the leader is now, so the moment the leader rounded a peninsula the last
     * ship in the line was aimed at a waypoint on the far side of the land. It sailed into the
     * shore, sat there, eventually gave up and went home, and the band went on without it.
     *
     * A wake cannot do that. Every point in it is water a hull has demonstrably crossed, and
     * consecutive points are close enough together that steering from one to the next is safe by
     * construction - round a headland the wake goes round the headland too. A ship that has
     * somehow ended up a long way off the line asks the pathfinder for a proper route back to it
     * rather than steering blindly at it.
     */
    private fun keepLineAhead(ships: List<AiShip>, delta: Float) {
        val afloat = ships.filter { ship -> aiShips.any { it === ship } }
        val head = afloat.firstOrNull() ?: return
        if (head.path.isEmpty() || head.pathIndex >= head.path.size) retargetAiShip(head)
        recordWake(head)

        val wake = head.wake
        if (wake.size < 2) return
        afloat.drop(1).forEachIndexed { order, follower ->
            val station = wake.lastIndex - FORMATION_TRAIL_WAYPOINTS * (order + 1)
            if (station < 0) return@forEachIndexed // the wake is not long enough to hold station in yet
            val post = wake[station]

            if (follower.position.dst(post) > FORMATION_REJOIN_DISTANCE) {
                // Straggling badly - far enough off the line that steering straight at it could
                // mean crossing land. Rejoin by a real route, and not more than once a second.
                follower.repathCooldown -= delta
                if (follower.repathCooldown <= 0f) {
                    routeTo(follower.position, post.cpy())?.takeIf { it.isNotEmpty() }?.let {
                        follower.repathCooldown = FORMATION_REJOIN_INTERVAL
                        follower.path = it
                        follower.pathIndex = 0
                    }
                }
                return@forEachIndexed
            }
            follower.path = wake.toList()
            follower.pathIndex = station
        }
    }

    /** Drops a breadcrumb behind the leader once it has made enough ground to be worth one, and
     *  forgets the oldest so the trail cannot grow without bound. */
    private fun recordWake(leader: AiShip) {
        val wake = leader.wake
        val last = wake.lastOrNull()
        if (last != null && last.dst(leader.position) < WAKE_SPACING) return
        wake.add(leader.position.cpy())
        while (wake.size > WAKE_MAX_POINTS) wake.removeAt(0)
    }

    private fun beginRaid() {
        // Weighted toward busy water: a corridor with several other harbours around it is one
        // ships actually use, and a raid there is felt. The player's own routes are deliberately
        // NOT part of this - the world should not arrange its troubles around them.
        val from = GameRandom.pickWeighted(ports) { port ->
            1f + ports.count { it !== port && it.worldPos.dst(port.worldPos) < BUSY_CORRIDOR_RADIUS }
        }
        // A corridor, not two ports picked out of a hat: the second is drawn from the handful
        // nearest the first, so the raid sits on a stretch someone would plausibly sail.
        // The pair must be far enough apart that the midpoint clears both harbours' safe water.
        // Grenada and Saint-Pierre are 283 units apart, so their midpoint sat 141 from
        // Saint-Pierre - inside its safe radius of 160 - and the whole raid gathered somewhere
        // the rules forbid anything from happening. Ships arrived and pecked at each other.
        val to = ports.filter { it !== from && it.worldPos.dst(from.worldPos) > PORT_SAFE_RADIUS * 2.4f }
            .sortedBy { it.worldPos.dst(from.worldPos) }
            .take(RAID_NEIGHBOUR_POOL)
            .let { if (it.isEmpty()) return else GameRandom.pick(it) }

        val centre = Vector2((from.worldPos.x + to.worldPos.x) / 2f, (from.worldPos.y + to.worldPos.y) / 2f)
        // Capped, because "nearest five" means something very different for an isolated harbour:
        // a Demerara raid spanned 636 units of open sea, so its three pirates and two warships
        // could wander for ten minutes and barely meet. A raid has to be a stretch of water, not
        // a quarter of the map.
        val radius = (from.worldPos.dst(to.worldPos) / 2f + RAID_RADIUS_MARGIN)
            .coerceAtMost(RAID_MAX_RADIUS)

        // The midpoint of two ports is not necessarily at sea. Cap-Francais sits on the north
        // coast of Hispaniola and Santo Domingo on the south, so the point between them is
        // inland - and a raid sited there is one no ship can ever reach, which is exactly what
        // happened: the band formed up, was given a destination in the middle of an island, and
        // stood at anchor for the rest of the session. Take the nearest sailable water instead,
        // and only if it is still recognisably between the two.
        val station = worldMap.findNearestWater(centre.x, centre.y)
        if (station == null || station.dst(centre) > radius) {
            GameLog.log(GameLog.Category.AI, "raid site ${from.port.name} - ${to.port.name} is inland, skipping")
            return
        }
        centre.set(station)
        val raid = PirateRaid(
            portA = from.port.name,
            portB = to.port.name,
            centre = centre,
            radius = radius,
            secondsRemaining = GameRandom.nextFloat(RAID_MIN_SECONDS, RAID_MAX_SECONDS),
            responseDelay = GameRandom.nextFloat(RAID_RESPONSE_MIN_SECONDS, RAID_RESPONSE_MAX_SECONDS),
            rallyPoint = pirateLairAnchor()
        )

        val recruits = aiShips.filter { it.role == AiShipRole.PIRATE }
            .sortedBy { it.position.dst(centre) }
            .take(RAID_PIRATE_COUNT)
        if (recruits.isEmpty()) {
            secondsUntilNextRaid = GameRandom.nextFloat(RAID_GAP_MIN_SECONDS, RAID_GAP_MAX_SECONDS)
            return
        }
        // Anchored at the rally point first, not the corridor: they gather before they sail.
        // Being called up puts a pirate on station for the duration - a raider still nominally
        // "in transit" would sail into the corridor it was summoned to and decline to touch the
        // shipping there, which is the one thing a raid exists to do.
        recruits.forEach {
            raid.draftIn(it, raid.rallyPoint)
            it.voyage = PirateVoyage.CRUISING
            it.cruisingGround = raid.centre.cpy()
            it.cruiseSecondsLeft = raid.secondsRemaining + raid.responseDelay
            breakOffChase(it, rest = false)
        }
        raid.band.addAll(recruits)

        activeRaid = raid
        GameLog.log(
            GameLog.Category.AI,
            "raid forming ${raid.label}: ${recruits.size} pirates gathering, " +
                "lasting ${raid.secondsRemaining.toInt()}s once on station"
        )
    }

    /**
     * The navies of the two ports' owners send whichever warships are NEAREST - not a random
     * pick. Response time then follows from geography on its own: a patrol already in the area
     * turns up quickly, and a raid far from anyone's home waters gets to run for a while.
     * Standing with the player is irrelevant here; they are coming for the pirates.
     */
    private fun sendNavalResponse(raid: PirateRaid) {
        raid.markResponseSent()
        val owners = ports.filter { it.port.name == raid.portA || it.port.name == raid.portB }
            .mapNotNull { it.port.faction }
            .toSet()
        // Each power answers on its own account, with its own squadron and its own memory of how
        // the last one went - they are not a coalition, they merely have neighbouring problems.
        var anySent = false
        for (faction in owners) {
            if (sendSquadron(raid, faction)) anySent = true
        }
        if (!anySent) GameLog.log(GameLog.Category.AI, "raid ${raid.label}: no warships available to respond")
    }

    /**
     * One power's answer: the nearest warships it has, gathered at its own port closest to the
     * trouble and sent out together rather than trickling in one at a time.
     *
     * Strength comes from [navalEscalation] - a squadron that was beaten is replaced by a larger
     * one next time. A power that keeps losing ships to the same corridor eventually turns up in
     * force, which is both what a state would do and a way for the world to answer the player's
     * question "is anyone doing anything about this?".
     */
    private fun sendSquadron(raid: PirateRaid, faction: Faction): Boolean {
        val strength = navalEscalation.getOrDefault(faction, NAVAL_WAVE_BASE)
        val available = aiShips.filter {
            it.role == AiShipRole.MILITARY && it.faction == faction && it !in raid.displacedAnchors
        }
        if (available.isEmpty()) return false

        val musterPort = ports.filter { it.port.faction == faction }
            .minByOrNull { it.approachPoint.dst(raid.centre) }
        val squadron = available.sortedBy { it.position.dst(raid.centre) }.take(strength)
        // Anchored on the trouble itself. They set out from wherever the nearest of them were -
        // which is the muster point in practice, since the squadron is chosen by proximity - and
        // travel in company rather than trickling in one at a time.
        squadron.forEach { raid.draftIn(it, raid.centre) }
        musterPort?.let { GameLog.log(GameLog.Category.AI, "raid ${raid.label}: ${faction.displayName} musters at ${it.port.name}") }
        raid.squadrons.getOrPut(faction) { mutableListOf() }.addAll(squadron)

        GameLog.log(
            GameLog.Category.AI,
            "raid ${raid.label}: ${faction.displayName} sent ${squadron.size} warships " +
                "(wave strength $strength)"
        )
        events.toast(
            "${faction.displayName} sends ${squadron.size} warships against the pirates near ${raid.label}.",
            ToastIcon.Property
        )
        return true
    }

    /** Records how a power's squadron fared, so the next one is sized accordingly. */
    private fun settleEscalation(raid: PirateRaid) {
        for ((faction, squadron) in raid.squadrons) {
            val survivors = squadron.count { it in aiShips }
            val current = navalEscalation.getOrDefault(faction, NAVAL_WAVE_BASE)
            navalEscalation[faction] = if (survivors == 0 && squadron.isNotEmpty()) {
                (current + 1).coerceAtMost(NAVAL_WAVE_MAX)
            } else {
                NAVAL_WAVE_BASE
            }
            GameLog.log(
                GameLog.Category.AI,
                "raid ${raid.label}: ${faction.displayName} lost ${squadron.size - survivors} of " +
                    "${squadron.size}; next wave ${navalEscalation[faction]}"
            )
        }
    }

    /**
     * A ship goes down - and it is the same event whether a raid is running or not. If one is,
     * and this happened inside it, the loss is marked on the water so a player arriving late can
     * still read what went on there. Everything else is unconditional: the wreck leaves the
     * world, whoever flew that flag replaces it, and anyone chasing the deceased needs a new
     * target.
     */
    fun sinkShip(ship: AiShip, victor: String) {
        // Every way a ship leaves the world comes through here, and only once: a ship already
        // gone is not replaced a second time (REVIEW.md, finding 7).
        if (!aiShips.remove(ship)) return
        val raid = activeRaid
        val inRaid = raid != null && raid.contains(ship.position)
        if (inRaid) raid!!.marks.add(SinkMark(ship.position.cpy(), ship.role == AiShipRole.PIRATE))
        raid?.release(ship)
        // Nobody goes on chasing, fleeing from or steering round a ship that is gone: a reference
        // to it would point at nothing a save could bring back (full save).
        for (other in aiShips) {
            if (other.quarry === ship) other.quarry = null
            if (other.fleeingFrom === ship) other.fleeingFrom = null
            if (other.avoidedThreat === ship) other.avoidedThreat = null
        }
        respawnReplacement(ship)
        val where = if (inRaid) " near ${raid!!.label}" else ""
        // Logged, never announced. NPCs fight each other constantly now, and a toast for every
        // sinking within sight buries the player under notifications about ships that are none of
        // their business. What happened on the water is told by the water - the sink marks a raid
        // leaves - not by a popup.
        GameLog.log(GameLog.Category.COMBAT, "$victor sank ${ship.displayName}$where")
    }

    private fun endRaid(raid: PirateRaid) {
        settleEscalation(raid)
        // The survivors go home rather than loitering on a corridor nobody is raiding any more -
        // and going home is also what redistributes them, since each picks a fresh cruising
        // ground on the next outward leg.
        for (pirate in raid.band) {
            if (aiShips.none { it === pirate }) continue
            pirate.voyage = PirateVoyage.HOMEBOUND
            pirate.cruisingGround = null
            pirate.path = emptyList()
            pirate.pathIndex = 0
            breakOffChase(pirate, rest = false)
        }
        raid.releaseAll()
        activeRaid = null
        secondsUntilNextRaid = GameRandom.nextFloat(RAID_GAP_MIN_SECONDS, RAID_GAP_MAX_SECONDS)
        GameLog.log(GameLog.Category.AI, "raid ended ${raid.label}")
        if (raid.announced) events.toast("Pirate activity near ${raid.label} has subsided.", ToastIcon.Property)
    }

    fun updateAiShips(delta: Float) {
        updateEncounters(delta)
        val myTier = shipTier(player.ship)
        // Started from a different ship each frame. Laying courses is rationed, and the ration is
        // spent in the order ships are visited - so a fixed order means the same unlucky ships at
        // the end of the list are refused every single time, for ever, while the ones at the
        // front never wait. Rotating the starting point makes the wait everybody's in turn.
        val fleetSize = aiShips.size
        if (fleetSize == 0) return
        fleetScanOffset = (fleetScanOffset + 1) % fleetSize
        for (position in 0 until fleetSize) {
            val ship = aiShips[(fleetScanOffset + position) % fleetSize]
            // Measured at the top of a ship's turn, so it takes in everything that moved the
            // hull since - including separation, which runs after this loop has finished.
            ship.groundCovered += ship.odometerMark.dst(ship.position)
            ship.odometerMark.set(ship.position)
            if (ship.giveUpCooldown > 0f) ship.giveUpCooldown -= delta
            // Counted here rather than inside the "needs a route" branch below, because a hull
            // that is blocked while still holding waypoints never reaches that branch - and a
            // timer that is only ever set and never counted down is a ship that asks for help
            // exactly once and then lies there.
            if (ship.retargetCooldown > 0f) ship.retargetCooldown -= delta
            // Self-healing, because prevention alone is not enough: A* refuses to plan a route
            // that STARTS on land, so a ship that has somehow ended up ashore can neither move
            // nor be given anywhere to go, and would sit there for the rest of the session.
            if (!worldMap.isWater(ship.position.x, ship.position.y)) refloat(ship)
            repairInHomeWaters(ship, delta)
            if (ship.role == AiShipRole.PIRATE && ship.tier < myTier &&
                shipPosition.dst(ship.position) < PIRATE_FLEE_AWARENESS_RADIUS
            ) {
                if (fleeFromPlayer(ship, delta)) continue
            }
            updateFlight(ship, delta)
            avoidThreatIfNeeded(ship, delta)
            if (updatePursuit(ship, delta)) continue
            if (closeWithQuarry(ship, delta)) continue
            if (ship.pathIndex >= ship.path.size) {
                // Asking for a route and being given nothing used to end the matter: the path
                // stayed empty, the same question was asked again next frame, and the ship sat
                // there for the rest of the session. It also asked several times per frame, and
                // laying a course is the most expensive thing this game does - so a refusal now
                // buys a moment's silence while findWayOut works the ship loose.
                if (ship.retargetCooldown > 0f) {
                    findWayOut(ship, delta)
                    continue
                }
                retargetAiShip(ship)
                // Only treat this as a ship that cannot be routed if it was actually refused on
                // the merits. If the frame simply ran out of pathfinding allowance, the ship
                // waits a frame - it is not lost.
                if (ship.path.isEmpty() && routeBudget > 0) {
                    ship.retargetCooldown = RETARGET_RETRY_SECONDS
                    findWayOut(ship, delta)
                }
                continue
            }
            when (Pilot.follow(ship, AI_SHIP_ARRIVE_THRESHOLD, delta, worldMap::isWater)) {
                Pilot.Step.SAILING -> Unit
                // Out of waypoints. Nothing to do here - the branch above is exactly the one
                // that exists for a hull with nowhere left to go, and it runs next frame.
                Pilot.Step.ARRIVED -> Unit
                Pilot.Step.BLOCKED -> breakFree(ship)
                // Moving, but not arriving. Same answer as blocked - it needs a different way
                // there - and the same rationing, since being stuck is a per-frame condition.
                Pilot.Step.STALLED -> breakFree(ship)
            }
        }
        resolveContacts()
        // After everyone has moved, not during: separating mid-loop would push ships that have
        // already had their turn this frame and leave the result dependent on list order.
        separateAiShips(delta)
    }

    /** M11: a hostile ship that spots a target (the player, or any active fleet captain's ship)
     *  within [PIRATE_FLEE_AWARENESS_RADIUS] breaks off its patrol and heads straight for it -
     *  the counterpart to fleeFromPlayer, same straight-line-no-pathfinding approach and the same
     *  reason (recomputing a real path every frame toward a moving target is wasted work; this
     *  bails out on hitting land rather than risk crossing it). Once locked onto a target it
     *  won't retarget mid-chase (see ship.isPursuing) - reconsidering every frame would mean a
     *  target juking sideways instantly "erases" the fact it was ever spotted, which reads as the
     *  ship being oblivious rather than persistent. Returns true when pursuit consumed this
     *  ship's movement for the frame, so the caller skips normal patrol movement. */
    private fun updatePursuit(ship: AiShip, delta: Float): Boolean {
        if (ship.giveUpCooldown > 0f) {
            ship.isPursuing = false
            ship.pursuingCaptain = null
            return false
        }
        // Orders can outlive their target. A ship chasing the player who has since been reduced
        // to the ship's boat would otherwise keep closing on something it will never be allowed
        // to fight, and hang over it in circles until its own give-up timer expired.
        if (ship.isPursuing && ship.pursuingCaptain == null && !player.ship.attackable) {
            abandonPursuit(ship)
            return false
        }
        // The same for a captain lost or recalled since: the chase used to go on after a ghost on
        // the captain's old course (REVIEW.md, finding 32).
        val chased = ship.pursuingCaptain
        if (ship.isPursuing && chased != null && (chased !in player.captains || chased.isIdle)) {
            abandonPursuit(ship)
            return false
        }
        if (!ship.isPursuing) {
            var bestDist = PIRATE_FLEE_AWARENESS_RADIUS
            var bestCaptain: Captain? = null
            var found = false
            if (player.ship.attackable && isHostile(ship, shipTier(player.ship))) {
                val dist = ship.position.dst(shipPosition)
                if (dist < bestDist) {
                    bestDist = dist
                    found = true
                }
            }
            for (captain in player.captains) {
                if (captain.isIdle) continue
                val ownedShip = player.ownedShips.find { it.id == captain.shipId } ?: continue
                if (!isHostile(ship, shipTier(ownedShip.shipType))) continue
                val pos = fleetShipTransform(captain)?.first ?: continue
                val dist = ship.position.dst(pos)
                if (dist < bestDist) {
                    bestDist = dist
                    bestCaptain = captain
                    found = true
                }
            }
            if (!found) return false
            ship.isPursuing = true
            ship.pursuingCaptain = bestCaptain
        }

        val targetPos = ship.pursuingCaptain?.let { fleetShipTransform(it)?.first } ?: shipPosition
        val dist = ship.position.dst(targetPos)
        // A quarry that has reached harbour is gone. Ports are safe (see isNearAnyPort), so
        // hanging about outside one can never pay off - and it was exactly what left pirates
        // parked at a port's doorstep indefinitely after the player slipped inside.
        if (dist > PIRATE_FLEE_AWARENESS_RADIUS * PURSUIT_GIVE_UP_MULTIPLIER || isNearAnyPort(targetPos)) {
            abandonPursuit(ship)
            return false
        }
        val dx = targetPos.x - ship.position.x
        val dy = targetPos.y - ship.position.y
        val len = Vector2(dx, dy).len().coerceAtLeast(0.01f)
        val nx = dx / len
        val ny = dy / len
        if (steer(ship, nx, ny, delta)) {
            ship.pursuitBlockedFor = 0f
            return true
        }

        // Land in the way. Pursuit is straight-line steering with no route around anything, so
        // returning true here (as it used to) told the caller "handled" and skipped the ship's
        // ordinary path movement as well - leaving it frozen on the spot, chasing something it
        // could never reach. Fall back to the patrol path this frame, and give up the chase if
        // the way stays blocked.
        ship.pursuitBlockedFor += delta
        if (ship.pursuitBlockedFor >= PURSUIT_BLOCKED_GIVE_UP_SECONDS) {
            // Worth logging even though it is now handled: a ship repeatedly getting boxed in at
            // the same spot is how a map problem announces itself.
            GameLog.log(
                GameLog.Category.AI,
                "${ship.displayName} gave up a chase - blocked by land for " +
                    "${PURSUIT_BLOCKED_GIVE_UP_SECONDS}s at ${ship.position.x.toInt()}, ${ship.position.y.toInt()}"
            )
            abandonPursuit(ship)
            ship.giveUpCooldown = GIVE_UP_COOLDOWN_CAUTIOUS
        }
        return false
    }

    /** Runs the invariant checks (see [WorldWatchdog]). Every one of these is a state the world
     *  should never reach; each has a matching bug in this file's history. */
    fun runWatchdog(realDelta: Float, worldDelta: Float) {
        watchdogWorldSeconds += worldDelta
        val sweepGameSeconds = watchdogWorldSeconds
        val swept = watchdog.update(realDelta) { reporter ->
            for (ship in aiShips) {
                val id = "${ship.displayName}#${ship.id}"
                val position = ship.position
                if (!position.x.isFinite() || !position.y.isFinite()) {
                    reporter.problem("$id/nan") { "$id has a non-finite position ($position)" }
                    continue
                }
                if (!worldMap.isWater(position.x, position.y)) {
                    reporter.problem("$id/aground") {
                        "$id is on land at ${position.x.toInt()}, ${position.y.toInt()}"
                    }
                }
                val previous = lastWatchdogPositions[ship]
                val couldHaveMade = ship.speed * sweepGameSeconds
                // A hull at anchor is not a hull in trouble: a harbour is where ships are
                // supposed to sit still, and for a pirate the lair is the harbour.
                val atRest = isNearAnyPort(position) || PirateSea.contains(position)
                if (previous != null && couldHaveMade > 0f && !atRest &&
                    previous.dst(position) < couldHaveMade * WATCHDOG_STUCK_FRACTION
                ) {
                    reporter.problem("$id/stuck") {
                        "$id made ${previous.dst(position).toInt()} units in " +
                            "${sweepGameSeconds.toInt()}s of sailing, out of a possible " +
                            "${couldHaveMade.toInt()}, having actually sailed " +
                            "${ship.groundCovered.toInt()} - at ${position.x.toInt()}, " +
                            "${position.y.toInt()} ${describeShipState(ship)}"
                    }
                }
                lastWatchdogPositions[ship] = position.cpy()
                ship.routeRelays = 0
                ship.groundCovered = 0f
            }
            lastWatchdogPositions.keys.retainAll(aiShips.toSet())

            // Every loss is replaced, so the fleet is a fixed size. If it is not, a replacement
            // failed to be created somewhere and the seas are quietly emptying - which is
            // precisely the sort of slow drain nobody can spot by eye.
            if (aiShips.size < EXPECTED_FLEET_SIZE) {
                reporter.problem("fleet/short") {
                    "fleet is ${aiShips.size} ships, expected $EXPECTED_FLEET_SIZE " +
                        "(${aiShips.count { it.role == AiShipRole.TRADE }} trade, " +
                        "${aiShips.count { it.role == AiShipRole.MILITARY }} military, " +
                        "${aiShips.count { it.role == AiShipRole.PIRATE }} pirate)"
                }
            }

            for (captain in player.captains) {
                if (captain.cargoUnits > 0 && captain.cargoGood == null) {
                    reporter.problem("${captain.name}/phantom-cargo") {
                        "${captain.name} holds ${captain.cargoUnits} units of nothing"
                    }
                }
                if (!captain.isIdle && player.ownedShips.none { it.id == captain.shipId }) {
                    reporter.problem("${captain.name}/ghost-ship") {
                        "${captain.name} is crewing ship ${captain.shipId}, which is not in the fleet"
                    }
                }
            }

            if (!shipPosition.x.isFinite() || !shipPosition.y.isFinite()) {
                reporter.problem("player/nan") { "the player's position is non-finite ($shipPosition)" }
            }
        }
        if (swept) watchdogWorldSeconds = 0f
    }

    /** Puts a beached ship back on the nearest water and clears its stale route. Logged, not
     *  silent: needing this is a symptom, and if it starts happening often the cause is worth
     *  finding rather than papering over. */
    /**
     * Wear heals in home waters, at a careener's pace.
     *
     * Persisting battle damage without this is a one-way ratchet: every hull in the world drifts
     * towards nought and the seas quietly empty of everyone who ever fought. A ship that has made
     * it back to a friendly harbour - or, for pirates, to the anchorage - is a ship being patched
     * up, and is fit again in about [AI_REPAIR_SECONDS]. Away from home nothing heals, so a fleet
     * fighting wave after wave without going back does get worn down and can be finished.
     */
    /**
     * Why this hull is where it is, in one line.
     *
     * The first version of the stuck report said only "has not moved", which named the symptom
     * and nothing else - so every investigation started by guessing which of a dozen systems had
     * hold of the ship. A ship holding station in a raid squadron, a ship waiting its turn for a
     * course, and a ship pinned against a headland are three different faults with the same
     * symptom, and the log has to tell them apart on its own.
     */
    private fun describeShipState(ship: AiShip): String {
        val state = buildList {
            add(ship.role.name.lowercase())
            if (ship.role == AiShipRole.PIRATE) add(ship.voyage.name.lowercase())
            if (ship.isPursuing) add("pursuing-player")
            ship.quarry?.let { add("chasing ${it.displayName}#${it.id} ${ship.chaseSeconds.toInt()}s") }
            ship.fleeingFrom?.let { add("fleeing ${it.displayName}#${it.id}") }
            if (ship.avoidedThreat != null) add("avoiding")
            activeRaid?.let { raid ->
                if (ship in raid.band) add("raid-band ${raid.phase.name.lowercase()}")
                if (raid.squadrons.values.any { ship in it }) {
                    add("squadron ${raid.phase.name.lowercase()}")
                }
            }
            add("route ${ship.pathIndex}/${ship.path.size}")
            if (ship.routeRelays > 0) add("${ship.routeRelays} courses laid this sweep")
            if (ship.retargetCooldown > 0f) add("waiting-for-course")
            if (ship.pursuitBlockedFor > 0f) add("blocked ${ship.pursuitBlockedFor.toInt()}s")
            val nearest = ports.minByOrNull { it.approachPoint.dst(ship.position) }
            if (nearest != null) {
                add("${nearest.approachPoint.dst(ship.position).toInt()}u off ${nearest.port.name}")
            }
            add("clearance ${worldMap.clearanceCellsAt(ship.position.x, ship.position.y)}")
        }
        return "(${state.joinToString(", ")})"
    }

    private fun repairInHomeWaters(ship: AiShip, delta: Float) {
        if (ship.currentHull >= ship.maxHull) return
        val sheltered = if (ship.role == AiShipRole.PIRATE) {
            PirateSea.contains(ship.position)
        } else {
            ports.any {
                it.port.faction == ship.faction &&
                    it.worldPos.dst(ship.position) < WorldScale.PORT_SAFE_RADIUS
            }
        }
        if (!sheltered) return
        ship.repairProgress += ship.maxHull * delta / AI_REPAIR_SECONDS
        val whole = ship.repairProgress.toInt()
        if (whole > 0) {
            ship.repairProgress -= whole
            ship.currentHull = (ship.currentHull + whole).coerceAtMost(ship.maxHull)
        }
    }

    /**
     * Last resort for a ship the pathfinder will not give a route to.
     *
     * A* plans over a grid sampled from the map, and it flatly refuses to plan out of a pocket of
     * water it cannot connect to the open sea - a bay behind a headland, a gap between two
     * Bahamian cays. Ordinary patrol movement can never get into one, because it only ever
     * follows a route A* already approved; a chase can, because it steers straight at its target
     * and only ever checks the very next step for land.
     *
     * So the ship edges back the way it came - towards its anchor, the water it demonstrably
     * reached this place from - trying progressively wider angles until one of them is sea. After
     * a few seconds of that it is back somewhere routable and carries on normally. Reported once
     * per ship, because a spot that keeps swallowing hulls is a map problem worth knowing about.
     */
    private fun findWayOut(ship: AiShip, delta: Float) {
        ship.quarry = null
        val home = Vector2(ship.anchor).sub(ship.position)
        // Sitting exactly on its own anchor, so "back the way you came" means nothing. Any
        // direction will do - the point is to stop being where the pathfinder has nothing to say.
        if (home.len() < 1f) home.set(1f, 0f)
        home.nor()
        val step = ship.speed * delta
        for (turn in STRAND_ESCAPE_ANGLES) {
            val heading = Vector2(home).rotateDeg(turn.toFloat())
            val nextX = ship.position.x + heading.x * step
            val nextY = ship.position.y + heading.y * step
            if (!worldMap.isWater(nextX, nextY)) continue
            ship.position.set(nextX, nextY)
            ship.headingDeg = MathUtils.atan2(heading.y, heading.x) * MathUtils.radDeg
            return
        }
        if (boxedInReported.add(ship.id)) {
            GameLog.log(
                GameLog.Category.AI,
                "${ship.displayName}#${ship.id} has no sailable way out at " +
                    "${ship.position.x.toInt()}, ${ship.position.y.toInt()}"
            )
        }
    }

    private fun refloat(ship: AiShip) {
        val water = worldMap.findNearestWater(ship.position.x, ship.position.y) ?: return
        GameLog.log(
            GameLog.Category.AI,
            "refloated ${ship.displayName} from ${ship.position.x.toInt()}, ${ship.position.y.toInt()} " +
                "to ${water.x.toInt()}, ${water.y.toInt()}"
        )
        ship.position.set(water)
        ship.path = emptyList()
        ship.pathIndex = 0
    }

    /**
     * On station, the band spreads out and starts taking merchantmen - which is the whole point
     * of a raid, and the thing the corridor's reputation is made of.
     *
     * Only AI traders: the player and hired captains already have their own encounter systems,
     * better ones, and a raid threatens them simply by putting three pirates where there was
     * usually none.
     */
    /**
     * Being run down: the merchantman drops what it was doing and makes for harbour.
     *
     * The decision is taken **once** per pursuer and then held. The previous version re-decided
     * every frame whether to run for port or straight away, and since the geometry shifts as both
     * ships move, that judgement kept flipping - which is what the eye read as merchantmen jinking
     * pointlessly back and forth instead of fleeing. Commitment is the whole mechanic.
     *
     * From there it is purely a question of the two hulls' speeds, as it should be: a fast
     * merchantman reaches harbour, a slow one is caught, and nothing anywhere gives either of them
     * a turn of speed it does not have.
     */
    private fun updateFlight(ship: AiShip, delta: Float) {
        if (ship.role != AiShipRole.TRADE) return
        val hunter = aiShips.firstOrNull { it.quarry === ship }
        if (hunter == null) {
            // Nobody is after it any more - but it goes on running for a while yet, rather than
            // turning back the instant the pirate breaks off.
            ship.fleeCommitment -= delta
            if (ship.fleeCommitment <= 0f) ship.fleeingFrom = null
            return
        }
        ship.fleeCommitment = Encounters.FLIGHT_COMMITMENT_SECONDS
        if (ship.fleeingFrom === hunter) return

        val refuge = refugeFrom(ship, hunter) ?: return
        val route = routeTo(ship.position, refuge.cpy())
        // Marked as fleeing only once there is actually somewhere to flee along. Setting it first
        // meant that a course refused for any reason - no route, or simply no allowance left this
        // frame - left the ship permanently convinced it was running, so it never asked again and
        // never took evasive action of any kind. It sailed its trading route into the pirate.
        if (route.isNullOrEmpty()) return
        ship.fleeingFrom = hunter
        ship.path = route
        ship.pathIndex = 0
    }

    /** The harbour to run for: the nearest one that does not mean sailing back past the pursuer.
     *  Running towards the ship chasing you is not a plan. */
    private fun refugeFrom(ship: AiShip, hunter: AiShip): Vector2? {
        val away = Vector2(ship.position).sub(hunter.position)
        if (away.len() < 0.01f) return null
        away.nor()
        val ahead = ports.filter {
            Vector2(it.approachPoint).sub(ship.position).nor().dot(away) > 0f
        }
        return (ahead.ifEmpty { ports })
            .minByOrNull { it.approachPoint.dst(ship.position) }
            ?.approachPoint
    }

    /**
     * The world's standing rules of engagement, applied to every ship every frame whether or not
     * anything special is happening.
     *
     * This used to exist only inside a raid, which meant the map had two different physics: a
     * pirate that happened to be inside a red circle hunted merchantmen, and an identical pirate
     * fifty units outside it sailed politely past them. Events are supposed to concentrate
     * trouble, not to be the only place trouble is possible - so the deciding, the chasing and
     * the fighting all live here now, and a raid merely arranges for more pirates to be in one
     * place at one time.
     *
     * Choosing is deliberately slow ([HUNT_DECISION_INTERVAL]) and sticky: a hunter commits to
     * one target and stays after it. Re-deciding every frame would re-roll the odds sixty times a
     * second, which turns any long-odds attack into a certainty and makes the roll meaningless.
     */
    private fun updateEncounters(delta: Float) {
        val fleetSize = aiShips.size
        if (fleetSize == 0) return
        for (position in 0 until fleetSize) {
            val hunter = aiShips[(fleetScanOffset + position) % fleetSize]
            updateVoyage(hunter, delta)
            if (hunter.attackCooldown > 0f) {
                // Reloading, boarding, sorting out the rigging - whatever it is, it is not
                // available for another fight, and should not be shadowing a target either.
                hunter.attackCooldown -= delta
                breakOffChase(hunter, rest = false)
                continue
            }
            if (hunter.role == AiShipRole.TRADE) continue
            val current = hunter.quarry
            if (current != null) {
                pressChase(hunter, current, delta)
                continue
            }
            hunter.huntCheckCooldown -= delta
            if (hunter.huntCheckCooldown > 0f) continue
            hunter.huntCheckCooldown = HUNT_DECISION_INTERVAL
            pickQuarry(hunter)?.let { beginChase(hunter, it) }
        }
    }

    /**
     * Carrying on a chase, and knowing when to stop.
     *
     * A chase is a course like any other course in this game: laid by the pathfinder, refreshed
     * every second or so onto where the quarry has got to, and sailed by the ordinary movement
     * code. It used to be per-frame steering straight at the target, which fought with the ship's
     * own route, jammed against every headland and produced the jinking back-and-forth that made
     * the whole map look unnatural.
     *
     * And every chase ends. It ends on catching up, on the quarry reaching harbour, on the clock,
     * or - the honest one - on the hunter noticing that the range has stopped shrinking, which
     * means the ship ahead is simply faster and no amount of persistence will change that.
     */
    private fun pressChase(hunter: AiShip, quarry: AiShip, delta: Float) {
        hunter.chaseSeconds += delta
        val range = hunter.position.dst(quarry.position)
        if (range < hunter.chaseBestRange - Encounters.CHASE_PROGRESS_MARGIN) {
            hunter.chaseBestRange = range
            hunter.chaseStalledFor = 0f
        } else {
            hunter.chaseStalledFor += delta
        }

        val hopeless = hunter.chaseStalledFor >= Encounters.CHASE_STALL_SECONDS
        val overdue = hunter.chaseSeconds >= Encounters.CHASE_MAX_SECONDS
        if (hopeless || overdue || !worthChasing(hunter, quarry)) {
            breakOffChase(hunter, rest = true)
            return
        }

        hunter.repathCooldown -= delta
        if (hunter.repathCooldown > 0f) return
        val route = routeTo(hunter.position, quarry.position.cpy())
        // The clock restarts on a course actually laid, not on the attempt. Otherwise a chase
        // refused for want of allowance sits on stale aim for the full interval anyway.
        if (route.isNullOrEmpty()) return
        hunter.repathCooldown = if (range < Encounters.CHASE_CLOSE_RANGE) {
            Encounters.CHASE_CLOSE_REPATH_SECONDS
        } else {
            Encounters.CHASE_REPATH_SECONDS
        }
        hunter.path = route
        hunter.pathIndex = 0
    }

    /**
     * The last few lengths of a chase, sailed straight at the quarry.
     *
     * A chase course ends on the quarry, and [Pilot] counts a waypoint as reached once it lies
     * inside the hull's turning circle - which for a Galleon, and for a Brigantine after a ship
     * under way, is wider than [Encounters.CONTACT_RADIUS]. Such a hunter used to fetch up a
     * hull's length short of its prize, take every fresh course as already sailed, and lie there
     * until [pressChase] gave the chase up as hopeless (REVIEW.md, finding 30). So once the
     * course is spent and the quarry is within [Encounters.CHASE_CLOSE_RANGE], the hunter steers
     * at it the way [updatePursuit] steers at the player; a hull slows as it comes round, so
     * even a Galleon turns inside contact range. Returns true when that used the ship's turn.
     */
    private fun closeWithQuarry(ship: AiShip, delta: Float): Boolean {
        val quarry = ship.quarry ?: return false
        if (ship.pathIndex < ship.path.size) return false
        val dx = quarry.position.x - ship.position.x
        val dy = quarry.position.y - ship.position.y
        val close = Encounters.CHASE_CLOSE_RANGE
        if (dx * dx + dy * dy >= close * close) return false
        return steer(ship, dx, dy, delta)
    }

    private fun beginChase(hunter: AiShip, quarry: AiShip) {
        hunter.quarry = quarry
        hunter.chaseSeconds = 0f
        hunter.chaseStalledFor = 0f
        hunter.chaseBestRange = hunter.position.dst(quarry.position)
        hunter.repathCooldown = 0f
    }

    /**
     * Which leg of its cruise a pirate is on.
     *
     * Out to a cruising ground, work it for a while, go home - and only the middle leg hunts
     * shipping. This is what puts pirates on the trade routes instead of in a permanent brawl
     * outside their own lair: a pirate that stops for every sail it passes on the way out never
     * gets anywhere, which is exactly what the world looked like.
     *
     * The ground itself is chosen by how busy the water is, not by how near home it is, so the
     * fleet spreads across the whole map rather than orbiting one corner of it.
     */
    private fun updateVoyage(ship: AiShip, delta: Float) {
        if (ship.role != AiShipRole.PIRATE) return
        when (ship.voyage) {
            PirateVoyage.TRANSIT -> {
                val ground = ship.cruisingGround ?: pickCruisingGround().also { ship.cruisingGround = it }
                if (ground != null && ship.position.dst(ground) < CRUISING_GROUND_RADIUS) {
                    ship.voyage = PirateVoyage.CRUISING
                    ship.cruiseSecondsLeft = GameRandom.nextFloat(CRUISE_MIN_SECONDS, CRUISE_MAX_SECONDS)
                    ship.anchor = ground.cpy()
                }
            }
            PirateVoyage.CRUISING -> {
                ship.cruiseSecondsLeft -= delta
                if (ship.cruiseSecondsLeft <= 0f) {
                    ship.voyage = PirateVoyage.HOMEBOUND
                    ship.anchor = PirateSea.lair.cpy()
                    breakOffChase(ship, rest = false)
                    ship.path = emptyList()
                    ship.pathIndex = 0
                }
            }
            PirateVoyage.HOMEBOUND -> {
                if (ship.position.dst(PirateSea.lair) < PirateSea.LAIR_RADIUS) {
                    ship.voyage = PirateVoyage.TRANSIT
                    ship.cruisingGround = pickCruisingGround()
                    ship.path = emptyList()
                    ship.pathIndex = 0
                }
            }
        }
    }

    /** A chase is over once the quarry has left the world, reached safe water, or simply drawn
     *  far enough ahead - with some slack past sighting range, or a target drifting one unit too
     *  far would end the pursuit and immediately restart it. */
    private fun worthChasing(hunter: AiShip, quarry: AiShip): Boolean =
        aiShips.any { it === quarry } &&
            !isNearAnyPort(quarry.position) &&
            !isNearAnyPort(hunter.position) &&
            hunter.position.dst(quarry.position) < Encounters.SIGHT_RADIUS * Encounters.GIVE_UP_MULTIPLIER

    /**
     * Looking around, sizing up the nearest thing worth attacking, and deciding whether to try.
     * The decision is a roll against [Encounters.odds] rather than a threshold: a rule like
     * "attack anything weaker" makes every meeting on the map a foregone conclusion, whereas
     * sliding odds leave a heavy pirate usually willing to take on a patrol and a light one
     * occasionally reckless.
     *
     * Harbours are safe water for everyone, hunter and hunted alike.
     */
    private fun pickQuarry(hunter: AiShip): AiShip? {
        if (isNearAnyPort(hunter.position)) return null
        // A ship that has just given up a chase leaves everything alone for a while. This was
        // never consulted before, so a hunter could drop one target and seize the next two and a
        // half seconds later - which is no give-up at all.
        if (hunter.giveUpCooldown > 0f) return null
        val candidate = aiShips
            .filter {
                it !== hunter &&
                    Encounters.isHostile(hunter.role, it.role) &&
                    // Merchantmen are only fair game to a pirate actually on station. Navies and
                    // pirates, though, take notice of each other on any leg of any voyage.
                    (it.role != AiShipRole.TRADE || Encounters.huntsShipping(hunter.role, hunter.voyage)) &&
                    it.position.dst(hunter.position) < Encounters.SIGHT_RADIUS &&
                    !isNearAnyPort(it.position)
            }
            .minByOrNull { it.position.dst(hunter.position) } ?: return null
        return if (GameRandom.nextFloat(0f, 1f) < Encounters.odds(hunter, candidate)) candidate else null
    }

    /**
     * Settles every pair that ended this frame within touching distance.
     *
     * Deliberately after the movement pass rather than inside it. A fight can sink a ship, and
     * sinking removes it from [aiShips] while its replacement is added to the same list - doing
     * that partway through iterating it is exactly what brought the world down with a
     * ConcurrentModificationException. The pairs are collected into a list of their own first,
     * so the fighting is free to change the fleet.
     */
    private fun resolveContacts() {
        val alongside = aiShips.mapNotNull { hunter ->
            val quarry = hunter.quarry ?: return@mapNotNull null
            if (hunter.position.dst(quarry.position) < Encounters.CONTACT_RADIUS) hunter to quarry else null
        }
        for ((hunter, quarry) in alongside) {
            // Either party may already have gone down in an earlier fight this same frame.
            if (aiShips.none { it === hunter } || aiShips.none { it === quarry }) continue
            resolveEncounter(hunter, quarry)
        }
    }

    /**
     * Hull to hull, settled by the same resolver the player's own battles and a captain's use,
     * from both ships' real stats and the damage each is already carrying. Nothing here knows
     * whether a raid is running, which is exactly why a navy patrol can lose a ship to a pirate
     * on an ordinary afternoon with no event in sight.
     */
    private fun resolveEncounter(hunter: AiShip, target: AiShip) {
        val caution = if (hunter.role == AiShipRole.PIRATE) RAID_PIRATE_CAUTION else RAID_WARSHIP_CAUTION
        val outcome = FleetCombatResolver.resolve(
            shipMaxHull = hunter.maxHull,
            shipStartHull = hunter.currentHull,
            shipMinDamage = hunter.minDamage,
            shipMaxDamage = hunter.maxDamage,
            shipManeuverability = RAID_WARSHIP_MANEUVER,
            caution = caution,
            enemy = target
        )
        hunter.currentHull = outcome.remainingHull.coerceAtLeast(0)
        hunter.quarry = null
        hunter.attackCooldown = GameRandom.nextFloat(RAID_ATTACK_COOLDOWN_MIN, RAID_ATTACK_COOLDOWN_MAX)
        when (outcome.result) {
            FleetCombatResult.VICTORY -> sinkShip(target, hunter.displayName)
            FleetCombatResult.DEFEAT -> sinkShip(hunter, target.displayName)
            FleetCombatResult.FLED -> {
                // Broke off with both still afloat. A pirate that has had enough leaves the
                // corridor it was drafted into rather than circling back for more.
                if (hunter.role == AiShipRole.PIRATE) activeRaid?.release(hunter)
                hunter.giveUpCooldown = GIVE_UP_COOLDOWN_CAUTIOUS
            }
        }
    }

    /**
     * What this ship, in its role, considers dangerous enough to plan a course around.
     *
     * A merchantman runs from any pirate - it has cargo to lose and no business trading shots. A
     * pirate weighs it up instead: a heavy one will stand and fight a patrol (that decision is
     * [pickQuarry]'s), but one plainly outgunned gets out of the way. Warships avoid nobody -
     * clearing the sea lanes is the job.
     */
    private fun threatTo(ship: AiShip): AiShip? = when (ship.role) {
        AiShipRole.TRADE -> aiShips.firstOrNull {
            it.role == AiShipRole.PIRATE && it.position.dst(ship.position) < LOOKOUT_RADIUS
        }
        AiShipRole.PIRATE -> aiShips.firstOrNull {
            it.role == AiShipRole.MILITARY &&
                it.position.dst(ship.position) < LOOKOUT_RADIUS &&
                Encounters.shouldAvoid(ship, it)
        }
        AiShipRole.MILITARY -> null
    }

    /**
     * A merchantman with a lookout: sighting a pirate, it changes its plans rather than its
     * physics. If a harbour is nearer than the pirate it runs for it and waits there; otherwise it
     * lays a new course around the threat. Whether that works is then purely a question of the two
     * ships' speeds - a fast pirate runs it down, a slow one never closes.
     *
     * This is the same rule captains already follow (see evadeIfNeeded), now given to NPC traders,
     * so every ship in the world reacts to danger the same way and only the player's hand on the
     * tiller differs. It replaces a version that simply shoved the ship away from the pirate each
     * frame, which fought with its own route (a step back, a step forward, minutes of twitching in
     * place) and, worse, granted a speed bonus out of nowhere.
     */
    private fun avoidThreatIfNeeded(ship: AiShip, delta: Float) {
        // Already committed to running something down, or to running away: a ship cannot be
        // closing in, fleeing and idly re-routing all at once. Flight outranks a lookout's
        // sighting, because it is the same sighting acted on more seriously.
        if (ship.quarry != null || ship.fleeingFrom != null) return
        ship.threatCheckCooldown -= delta
        if (ship.threatCheckCooldown > 0f) return
        ship.threatCheckCooldown = THREAT_CHECK_INTERVAL

        val threat = threatTo(ship)
        if (threat == null) {
            ship.avoidedThreat = null
            return
        }
        // Already reacted to this one; re-laying the same course every couple of seconds would
        // just burn pathfinding and make the ship dither.
        if (ship.avoidedThreat === threat) return
        ship.avoidedThreat = threat
        if (isNearAnyPort(ship.position)) return

        val refuge = ports.minByOrNull { it.approachPoint.dst(ship.position) } ?: return
        val runForPort = refuge.approachPoint.dst(ship.position) < threat.position.dst(ship.position)
        val destination = if (runForPort) refuge.approachPoint else ship.path.lastOrNull() ?: refuge.approachPoint

        val route = routeAvoiding(ship.position, destination, threat.position, THREAT_AVOID_RADIUS)
        if (!route.isNullOrEmpty()) {
            ship.path = route
            ship.pathIndex = 0
        }
    }

    private fun abandonPursuit(ship: AiShip) {
        ship.isPursuing = false
        ship.pursuingCaptain = null
        ship.pursuitBlockedFor = 0f
    }

    /**
     * Keeps AI ships from stacking into a single flickering pile. Nothing stopped several
     * pursuers converging on the same target from ending up at the same coordinates, drawn on
     * top of each other and jittering as each corrected toward the same point.
     *
     * This is ordinary local separation steering, the same trick flocking AI has used for
     * decades: no collision solver and no reservations, each ship simply drifts away from
     * whatever is too close. Cheap enough to run brute force - a few dozen hulls is a couple of
     * thousand pair checks a frame - and the push is only applied where it lands on water, so
     * crowding can never shove a ship onto a beach.
     */
    private fun separateAiShips(delta: Float) {
        val pushX = FloatArray(aiShips.size)
        val pushY = FloatArray(aiShips.size)
        for (i in aiShips.indices) {
            for (j in i + 1 until aiShips.size) {
                val a = aiShips[i]
                val b = aiShips[j]
                var dx = b.position.x - a.position.x
                var dy = b.position.y - a.position.y
                val distanceSq = dx * dx + dy * dy
                if (distanceSq >= SHIP_SEPARATION_RADIUS * SHIP_SEPARATION_RADIUS) continue
                // An attacker and its target are exempt. Contact range is *inside* the separation
                // radius, so without this the very rule that stops hulls sharing a pixel would
                // hold every chase permanently a ship's length short of a fight.
                if (a.quarry === b || b.quarry === a) continue

                var distance = kotlin.math.sqrt(distanceSq)
                if (distance < 0.01f) {
                    // Exactly coincident: pick a deterministic direction from their identities so
                    // the pair does not sit vibrating on a random axis every frame.
                    val angle = ((i * 37 + j * 17) % 360).toFloat() * MathUtils.degRad
                    dx = MathUtils.cos(angle)
                    dy = MathUtils.sin(angle)
                    distance = 1f
                }
                val overlap = (SHIP_SEPARATION_RADIUS - distance) / SHIP_SEPARATION_RADIUS
                val push = overlap * SHIP_SEPARATION_STRENGTH * delta
                val nx = dx / distance * push
                val ny = dy / distance * push
                // Gathered rather than applied on the spot: with several neighbours the pushes
                // used to add up unchecked, and the sum could exceed the ship's own speed.
                // A ship on its final approach is exempt. Separation keeps ships a full sprite
                // apart, which is more than twice the distance at which arriving counts - so
                // without this, two ships heading for the same harbour shove each other out of
                // arrival range and mill about outside it forever. Crowding is cosmetic;
                // arriving is not, so arriving wins.
                if (!isDocking(a)) { pushX[i] -= nx; pushY[i] -= ny }
                if (!isDocking(b)) { pushX[j] += nx; pushY[j] += ny }
            }
        }

        for (index in aiShips.indices) {
            val ship = aiShips[index]
            var dx = pushX[index]
            var dy = pushY[index]
            if (dx == 0f && dy == 0f) continue
            // However crowded it is, a ship is never displaced faster than it can sail: crowding
            // is cosmetic, steering is not.
            val limit = ship.speed * SEPARATION_SPEED_FRACTION * delta
            val magnitude = kotlin.math.sqrt(dx * dx + dy * dy)
            if (magnitude > limit) {
                dx = dx / magnitude * limit
                dy = dy / magnitude * limit
            }
            nudgeIfWater(ship, dx, dy)
        }
    }

    /**
     * True while a ship is on the last stretch to its *destination*, where being nudged aside
     * would cost it the arrival - see separateAiShips.
     *
     * This used to ask about the current waypoint instead, which quietly switched separation off
     * for nearly the whole map: a route is laid on a 10-unit pathfinding grid, so the next
     * waypoint is essentially always inside the exemption distance, and almost every moving ship
     * counted as "docking" every frame. That is why hulls sailed straight through one another.
     * Only the end of the route earns the exemption.
     */
    private fun isDocking(ship: AiShip): Boolean {
        val destination = ship.path.lastOrNull() ?: return false
        return destination.dst(ship.position) < AI_SHIP_ARRIVE_THRESHOLD * DOCKING_APPROACH_MULTIPLIER
    }

    /**
     * Points the ship a little further towards [dirX], [dirY] and moves it along the heading it
     * actually has - not the heading it would like to have.
     *
     * Every AI ship moves through here, which is the whole point. Heading used to be assigned
     * outright at four separate call sites, so a course change of any size happened within a
     * single frame: ships appeared to stop dead, pivot on the spot and set off again, and a line
     * of ships rounding a headland snapped round one after another. Coming about at the hull's
     * own rate is what makes a change of course read as sailing.
     *
     * Returns false when there is no water ahead, leaving the caller to decide what to do about
     * it - a turn can carry a ship wide of where it meant to go, and the sea does not owe anyone
     * the room to finish one.
     */
    private fun steer(ship: AiShip, dirX: Float, dirY: Float, delta: Float): Boolean =
        Pilot.steer(ship, dirX, dirY, delta, worldMap::isWater)

    /** Moves the ship if the destination is sea; reports whether it did. */
    private fun nudgeIfWater(ship: AiShip, dx: Float, dy: Float): Boolean {
        val x = ship.position.x + dx
        val y = ship.position.y + dy
        if (!worldMap.isWater(x, y)) return false
        ship.position.set(x, y)
        return true
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
        // Plain hull speed: a ship cannot sail faster than it sails because it is frightened.
        return steer(ship, nx, ny, delta)
    }

    /**
     * A hull that could not move at all this tick.
     *
     * It used to call retargetAiShip, which picks a *new* destination - so one blocked frame was
     * enough to make a trader abandon the port it was bound for, and a warship flip its leg from
     * outward to homeward and back again (the leg turns over on every destination chosen). A
     * blocked ship has not changed its mind about where it is going; it only needs a different
     * way there. Re-lay the course to the same place, and only pick somewhere new if there is
     * genuinely no route to the old one.
     *
     * Rationed by [AiShip.retargetCooldown], because being blocked is a per-frame condition and
     * laying a course is the most expensive thing this game does.
     */
    private fun breakFree(ship: AiShip) {
        if (ship.retargetCooldown > 0f) return
        ship.retargetCooldown = RETARGET_RETRY_SECONDS
        if (!relayRoute(ship)) retargetAiShip(ship)
    }

    /** Lays a fresh course to where this ship was already bound. Reports whether it got one. */
    private fun relayRoute(ship: AiShip): Boolean {
        val destination = ship.destination ?: return false
        if (destination.dst(ship.position) <= AI_SHIP_ARRIVE_THRESHOLD) return false
        val route = routeTo(ship.position, destination.cpy())
        if (route.isNullOrEmpty()) return false
        ship.path = route
        ship.pathIndex = 0
        ship.routeRelays++
        return true
    }

    /** How long [ship] waits before it'll try [targetTier] again after failing to catch it this
     *  time (see finishCombat's FLED branch and updateFleetEncounters) - the persistence half of
     *  the threat model, isHostile above being the "does it attack at all" half. A military ship
     *  is relentless exactly when it's already hostile (reputation this low means it's actively
     *  hunting the player, not giving up after one miss); a pirate has no reputation stat, so it
     *  reuses how badly it outguns the target instead - a lopsided tier advantage means it has
     *  little to lose trying again immediately, a fairer fight means it backs off like any other
     *  cautious ship. */
    fun giveUpCooldownFor(ship: AiShip, targetTier: Int): Float {
        val relentless = when (ship.role) {
            AiShipRole.PIRATE -> ship.tier - targetTier >= PIRATE_RELENTLESS_TIER_GAP
            // Only an outright hostile navy keeps coming back. One that merely dislikes the
            // player (OPPORTUNISTIC) takes its shot and then lets go like any cautious ship.
            AiShipRole.MILITARY -> ship.faction?.let {
                militaryStance(player.reputation[it] ?: 0) == MilitaryStance.HUNTS
            } ?: false
            AiShipRole.TRADE -> false
        }
        return if (relentless) GIVE_UP_COOLDOWN_RELENTLESS else GIVE_UP_COOLDOWN_CAUTIOUS
    }

    fun checkAiEncounters() {
        // The ship's boat (see ShipTypes.LONGBOAT) cannot fight - 0 damage is just a stalemate
        // or a guaranteed loss - so nothing ever engages it; it only exists to limp to a port.
        if (!player.ship.attackable) return
        if (isNearAnyPort(shipPosition)) return
        val myTier = shipTier(player.ship)
        for (ship in aiShips) {
            val engaging = (isHostile(ship, myTier) && ship.giveUpCooldown <= 0f) || ship === huntedShip
            if (!engaging) continue
            if (shipPosition.dst(ship.position) < AI_SHIP_ENGAGE_RADIUS) {
                // M11: a ship that hunted the player down (or that the player deliberately went
                // after) attacks outright - no free preview. One stumbled into by chance offers a
                // real choice instead (see showEncounterChoice) - the whole point of the pursuit
                // mechanic is that being caught unaware feels different from a fair sighting.
                if (ship.isPursuing || ship === huntedShip) {
                    startAiCombat(ship)
                } else {
                    pendingEncounter = ship
                    events.offerEncounter(ship)
                }
                return
            }
        }
    }

    /** The world's half of a fight with the player: the ship stops chasing anyone else, becomes the
     *  target, and any hunt the player had on is over. The game then opens the battle
     *  ([WorldEvents.startCombat]) - the mode, the music and the screen are its business. */
    fun startAiCombat(ship: AiShip) {
        pendingEncounter = null
        // "Fight" can be pressed after a captain has sunk the ship (finding 7).
        if (aiShips.none { it === ship }) return
        ship.isPursuing = false
        ship.pursuingCaptain = null
        currentAiTarget = ship
        huntedShip = null
        events.startCombat(ship)
    }

    /** "Sail On" in the encounter choice (see checkAiEncounters): costs nothing but the same
     *  give-up cooldown a failed catch would (see giveUpCooldownFor) - the ship just moves on like
     *  it would after any other miss. */
    fun declineEncounter(ship: AiShip) {
        pendingEncounter = null
        ship.giveUpCooldown = giveUpCooldownFor(ship, shipTier(player.ship))
    }

    /** The world's half of the end of a fight with the player: standing, the prize ship, the
     *  player's losses and the enemy's cooldown. The game then puts the sea back on screen. */
    fun finishCombat(outcome: CombatOutcome) {
        val target = currentAiTarget
        when (outcome) {
            CombatOutcome.VICTORY -> if (target != null) {
                // The prize was already paid out by CombatState when the enemy sank (it is the
                // same target.goldReward); adding it again here would double every victory.
                target.faction?.let { faction ->
                    val loss = if (target.role == AiShipRole.MILITARY) REP_LOSS_MILITARY else REP_LOSS_TRADE
                    player.adjustReputation(faction, loss)
                }
                events.toast(
                    "Victory! You defeated the ${target.displayName} and claimed ${target.goldReward}g.",
                    ToastIcon.Coin
                )
                sinkShip(target, "the player")
            }
            CombatOutcome.DEFEAT -> {
                // Losing a fight is total: the hull, every unit of cargo and every coin aboard
                // are gone (anything already tucked away in a port warehouse is untouched), and
                // what is left is ShipTypes.LONGBOAT - no hold, no guns, just enough to reach a
                // harbour. Full hull on the boat, so that "reach a harbour" is always possible.
                player.castAdrift()
                // They have what they came for and they leave. Without this the victor is still
                // carrying its orders to hunt the player down, and spends the next minute circling
                // the boat it just emptied.
                if (target != null) {
                    abandonPursuit(target)
                    target.giveUpCooldown = GIVE_UP_COOLDOWN_CAUTIOUS
                }
                events.toast(
                    "Defeated! They take your ship, your cargo and every coin aboard, and leave " +
                        "you adrift in the ship's boat. It has no hold and no guns - make harbour, " +
                        "and start again.",
                    ToastIcon.Castaway
                )
            }
            CombatOutcome.FLED -> if (target != null) {
                target.giveUpCooldown = giveUpCooldownFor(target, shipTier(player.ship))
            }
            CombatOutcome.ONGOING -> {}
        }
        currentAiTarget = null
    }

    /** Ticks every hired captain's route (see FleetSimulation) - runs unconditionally whenever
     *  appState == PLAYING, same as MarketState/GameClock above, so a captain's clock keeps
     *  advancing no matter which screen the player is actually looking at. Routine arrivals
     *  (a trade went fine) deliberately do NOT pop a toast - only a genuine loss does (see
     *  updateFleetEncounters). A toast freezes sailing entirely (see the SAILING gate further
     *  up), and a captain finishing a leg roughly every several real minutes would otherwise
     *  repeatedly interrupt play just to report a routine trade - the whole point of delegating
     *  is not having to watch it closely; the result is always visible next time the player
     *  checks the Tavern screen. */
    fun updateFleet() {
        FleetSimulation.update(
            player = player,
            currentGameMinutes = GameClock.totalGameMinutes,
            distanceBetween = ::fleetSeaDistance
        )
    }

    /** Whether a fleet captain runs into real trouble - NOT an abstract per-leg dice roll, an
     *  actual proximity check against the same aiShips the player themselves can run into, using
     *  the same [isHostile] (pirates by relative tier, faction military by reputation). Three
     *  things can happen to an active captain each frame, in order: still sheltering/travelling
     *  to shelter from an earlier scare (see [resumeIfSafe] - nothing else runs for them this
     *  frame); actually caught (a hostile, off-cooldown ship within engage range - see
     *  [resolveFleetCombat], a REAL fight, not a coin flip); or just sailing, in which case
     *  [evadeIfNeeded] gets a chance to notice trouble early and react before it comes to that. */
    fun updateFleetEncounters() {
        // Over a copy: losing a fight removes the captain from player.captains (see
        // resolveFleetCombat), and removing from the list being iterated threw a
        // ConcurrentModificationException the moment a captain was actually lost - a crash that
        // needed a pirate to win before it would show itself.
        // A recalled or lost captain's drawn course goes with them (REVIEW.md, finding 33) - it is
        // also what kept a lost captain's "position" alive for a pirate to chase (finding 32).
        fleetPaths.keys.retainAll { it in player.captains && !it.isIdle }
        for (captain in player.captains.toList()) {
            if (captain.isIdle) continue
            val ownedShip = player.ownedShips.find { it.id == captain.shipId } ?: continue

            if (captain.sheltering) {
                resumeIfSafe(captain, ownedShip)
                continue
            }
            if (captain.shelterPort.isNotEmpty()) continue // still en route to shelter

            val pos = fleetShipTransform(captain)?.first ?: continue
            if (isNearAnyPort(pos)) continue

            val tier = shipTier(ownedShip.shipType)
            val caught = aiShips.firstOrNull {
                it !== currentAiTarget && it !== pendingEncounter &&
                    isHostile(it, tier) && it.giveUpCooldown <= 0f && pos.dst(it.position) < AI_SHIP_ENGAGE_RADIUS
            }
            if (caught != null) {
                resolveFleetCombat(captain, ownedShip, caught)
                continue
            }
            evadeIfNeeded(captain, ownedShip, pos)
        }
    }

    /** A real fight, resolved instantly - see FleetCombatResolver, which reuses the exact same
     *  formulas as the player's own turn-based combat. Victory keeps the ship (possibly battered)
     *  and pays out like the player's own victories; fleeing mid-fight also keeps the ship, just
     *  battered, and gives the enemy the same give-up cooldown a miss would; defeat is total and
     *  permanent, same as it's always been - no rescue rollback. */
    private fun resolveFleetCombat(captain: Captain, ownedShip: OwnedShip, threat: AiShip) {
        val outcome = FleetCombatResolver.resolve(
            shipMaxHull = ownedShip.shipType.maxHull,
            shipStartHull = ownedShip.condition.currentHull,
            shipMinDamage = ownedShip.effectiveMinDamage,
            shipMaxDamage = ownedShip.effectiveMaxDamage,
            shipManeuverability = ownedShip.effectiveManeuverability,
            caution = captain.caution,
            enemy = threat
        )
        when (outcome.result) {
            FleetCombatResult.VICTORY -> {
                ownedShip.condition.currentHull = outcome.remainingHull
                player.gold += outcome.goldReward
                captain.totalProfit += outcome.goldReward
                sinkShip(threat, captain.name)
                captain.lastReport = "${captain.name} fought off a ${threat.displayName} and claimed ${outcome.goldReward}g."
            }
            FleetCombatResult.FLED -> {
                ownedShip.condition.currentHull = outcome.remainingHull
                threat.giveUpCooldown = giveUpCooldownFor(threat, shipTier(ownedShip.shipType))
                captain.lastReport = "${captain.name} broke off from a ${threat.displayName}, ship battered but afloat."
            }
            FleetCombatResult.DEFEAT -> {
                events.toast(
                    "Your captain ${captain.name} was caught by a ${threat.displayName} and lost - " +
                        "along with their ship and everything aboard.",
                    ToastIcon.Castaway
                )
                GameLog.log(
                    GameLog.Category.FLEET,
                    "${captain.name} lost with their ship to a ${threat.displayName}"
                )
                FleetSimulation.loseCaptain(player, captain)
            }
        }
    }

    /** Proactive evasion (see M11's decision sheet) - a captain who spots trouble early tries to
     *  avoid it "by any means available", the same two tactics a paying-attention player would
     *  use: run for whichever port (any port, not just this route's own two) is closer than the
     *  threat itself is, or, if no port is close enough, steer around the threat the same way the
     *  ship already steers around land (see WorldMap.findPath's avoid param) and keep going.
     *  Reacts once per threat (see FleetPathEntry.avoidedThreat) rather than replanning every
     *  frame - a captain who already committed to a maneuver doesn't un-commit mid-manoeuvre. */
    private fun evadeIfNeeded(captain: Captain, ownedShip: OwnedShip, pos: Vector2) {
        val tier = shipTier(ownedShip.shipType)
        val threat = aiShips.firstOrNull {
            isHostile(it, tier) && it.giveUpCooldown <= 0f && pos.dst(it.position) < PIRATE_FLEE_AWARENESS_RADIUS
        } ?: return
        if (fleetPaths[captain]?.avoidedThreat === threat) return

        val nearestPort = ports.minByOrNull { it.approachPoint.dst(pos) } ?: return
        val distanceToPort = nearestPort.approachPoint.dst(pos)
        val distanceToThreat = threat.position.dst(pos)

        // No course laid means no manoeuvre: the captain keeps the route, and the threat is not
        // marked as dealt with, so the next look tries again. A two-point straight "path" used to
        // stand in, and the ship was drawn sailing through the land (REVIEW.md, finding 10).
        // Each manoeuvre is a course laid, and courses are rationed per frame: without the ration
        // a manoeuvre that keeps failing searched the sea every frame (REVIEW.md, finding 34).
        if (!spendCourse()) return
        if (distanceToPort < distanceToThreat) {
            val path = worldMap.findPath(pos, nearestPort.approachPoint)?.takeIf { it.isNotEmpty() }
                ?: return reportNoCourse(captain, threat, nearestPort.port.name)
            val legMinutes = GameClock.realSecondsToGameMinutes(distanceToPort / ownedShip.effectiveSpeed).coerceAtLeast(1f)
            FleetSimulation.departForShelter(captain, nearestPort.port.name, legMinutes, GameClock.totalGameMinutes)
            fleetPaths[captain] = FleetPathEntry("", nearestPort.port.name, path, threat)
        } else {
            val dest = if (captain.headingToB) captain.portB else captain.portA
            val destPos = portApproachPos(dest) ?: return
            val detourPath = worldMap.findPath(pos, destPos, threat.position, FLEET_DETOUR_AVOID_RADIUS)
                ?.takeIf { it.isNotEmpty() } ?: return reportNoCourse(captain, threat, dest)
            val legMinutes = GameClock.realSecondsToGameMinutes(pathLength(detourPath) / ownedShip.effectiveSpeed).coerceAtLeast(1f)
            FleetSimulation.redirectCurrentLeg(captain, legMinutes, GameClock.totalGameMinutes)
            val origin = if (captain.headingToB) captain.portA else captain.portB
            fleetPaths[captain] = FleetPathEntry(origin, dest, detourPath, threat)
        }
    }

    /** Takes one course from this frame's ration ([routeBudget]); false when none is left. */
    private fun spendCourse(): Boolean {
        if (routeBudget <= 0) return false
        routeBudget--
        return true
    }

    /** Logs, once per captain and threat, that no course away from [threat] could be laid - the
     *  look is repeated every frame, and GameLog is for decisions, not frames. */
    private fun reportNoCourse(captain: Captain, threat: AiShip, toward: String) {
        if (noCourseReported.add(captain to threat)) {
            GameLog.log(GameLog.Category.FLEET, "${captain.name} sighted ${threat.displayName} but no course to $toward can be laid")
        }
    }

    /** Checked every frame a captain is waiting at shelter - "safe" means no hostile, off-
     *  cooldown ship is within noticing range of the shelter port itself (broader than just
     *  tracking the one original threat: staying an extra moment costs nothing, ports are already
     *  an absolute safe harbor, so there's no reason to leave into a DIFFERENT danger that
     *  happened to wander close). Once safe, resumes toward wherever the captain was originally
     *  headed and pins a fresh render path from the real shelter position - not the named route
     *  port fleetPath would otherwise assume as the leg's start. */
    private fun resumeIfSafe(captain: Captain, ownedShip: OwnedShip) {
        val shelterPos = portApproachPos(captain.shelterPort) ?: return
        val tier = shipTier(ownedShip.shipType)
        val stillDangerous = aiShips.any {
            isHostile(it, tier) && it.giveUpCooldown <= 0f && it.position.dst(shelterPos) < PIRATE_FLEE_AWARENESS_RADIUS
        }
        // A ship that never leaves the waters off the port used to keep the captain here for the
        // rest of the session; past the limit the captain sails anyway (REVIEW.md, finding 31).
        // The limit runs from arriving here, which is what arrivesAtGameMinutes holds while waiting.
        val waitedLongEnough = GameClock.totalGameMinutes - captain.arrivesAtGameMinutes >= SHELTER_MAX_GAME_MINUTES
        if (stillDangerous && !waitedLongEnough) return

        val dest = if (captain.headingToB) captain.portB else captain.portA
        val destPos = portApproachPos(dest) ?: return
        val origin = if (captain.headingToB) captain.portA else captain.portB
        if (!spendCourse()) return
        FleetSimulation.resumeFromShelter(player, captain, GameClock.totalGameMinutes, ::fleetSeaDistance)
        val path = worldMap.findPath(shelterPos, destPos)?.takeIf { it.isNotEmpty() } ?: listOf(shelterPos.cpy(), destPos.cpy())
        fleetPaths[captain] = FleetPathEntry(origin, dest, path)
    }

    private fun pathLength(path: List<Vector2>): Float {
        var total = 0f
        for (i in 0 until path.size - 1) total += path[i].dst(path[i + 1])
        return total
    }

    /** Real sea-route distance (world units) between two ports, for FleetSimulation.departFor to
     *  turn into a leg duration via the ship's own speed - see updateFleet/TavernUI wiring. */
    fun fleetSeaDistance(from: String, to: String): Float = pathLength(pathBetweenPorts(from, to))

    // --- the player's ship (review batch 2, step 6) -------------------------------------------

    /**
     * The part of the world that runs whenever a game is being played, port screens and open
     * windows included: hired captains keep sailing and keep meeting trouble while the player is
     * ashore (review batch 2, decision 9).
     */
    fun updateAlways() {
        updateFleet()
        updateFleetEncounters()
    }

    /**
     * One frame of the world at sea - everything that runs only while the player is sailing with
     * nothing open over the chart (the game decides that, decision 9), in the order it always
     * ran: the player's own ship, the AI ships, the watchdog, meetings with the player, raids, sea
     * events, and the weed on the player's hull. [realDelta] is the frame's wall-clock length,
     * which the watchdog needs for how often to look.
     */
    fun updateSailing(worldDelta: Float, realDelta: Float) {
        if (huntedShip != null) updateShipHunt(worldDelta) else updateAutopilot(worldDelta)
        updateAiShips(worldDelta)
        // Watched from inside the block that actually moves the world. Run from the render
        // loop instead, it reported every ship at once as "not moving" the moment the player
        // opened a port screen - which is not a stuck ship, it is a paused simulation, and
        // thirty-nine such lines a session is exactly the noise this was built to avoid.
        //
        // Both clocks, and they are not interchangeable: how often to look is a question
        // about the person watching (real seconds), how far a ship should have got is a
        // question about the world (game seconds). Conflating them is what made the stuck
        // check fire every quarter of a second at speed.
        runWatchdog(realDelta, worldDelta)
        checkAiEncounters()
        // Game time, and in step with the ships. On real time the phases of a raid ran twenty
        // times slower than the world around them at 20x speed, which is why the first raid
        // turned up anywhere between three and eight minutes into a session instead of after
        // the minute it is set to. And it belongs here rather than above, with the rest of the
        // world: a raid whose clock drains while its ships are frozen in a port screen can
        // expire without ever having happened.
        updateRaids(worldDelta)
        updateSeaEvents(worldDelta)
        player.condition.accumulateFouling(worldDelta)
    }

    /** The player at the helm (WASD): any course, hunt or destination is dropped and the ship
     *  makes way the direction held. [moveX]/[moveY] need not be of unit length. */
    fun sailByHand(moveX: Float, moveY: Float, delta: Float) {
        navMode = NavMode.MANUAL
        path = emptyList()
        destinationPort = null
        huntedShip = null
        val len = Vector2(moveX, moveY).len()
        val dirX = moveX / len
        val dirY = moveY / len
        val speed = player.effectiveSpeed
        tryMove(dirX * speed * delta, dirY * speed * delta)
        shipHeadingDeg = MathUtils.atan2(dirY, dirX) * MathUtils.radDeg
    }

    /** The player clicked on [ship]: give chase (see [updateShipHunt]), dropping any course. */
    fun huntShip(ship: AiShip) {
        huntedShip = ship
        navMode = NavMode.MANUAL
        path = emptyList()
        destinationPort = null
    }

    /** Lays the autopilot's course to [destination] - a harbour's approach when [port] is given,
     *  and arriving there makes port. False when no course can be laid; the ship is then left
     *  exactly as it was. */
    fun setCourse(destination: Vector2, port: ResolvedPort?): Boolean {
        val newPath = worldMap.findPath(shipPosition, destination)
        if (newPath.isNullOrEmpty()) return false
        path = newPath
        pathIndex = 0
        navMode = NavMode.AUTOPILOT
        destinationPort = port
        huntedShip = null
        return true
    }

    /** Making port: the world's half - a passenger bound here is paid off - then the game opens
     *  the port (see [WorldEvents.arrivedAtPort]). */
    fun arriveAtPort(port: ResolvedPort) {
        deliverPassenger(port)
        events.arrivedAtPort(port)
    }

    /**
     * Sails the laid course for [delta] seconds' worth of way.
     *
     * Never past a waypoint: the way left over once one is reached goes on toward the next in
     * the same frame. A whole frame's way used to be spent in one straight step - one long frame
     * at speed overshot the waypoint, or ended on land and went nowhere - and the frame that
     * reached a waypoint was spent standing still, so one long frame and several short ones took
     * the ship to different places (REVIEW.md, finding 15).
     */
    fun updateAutopilot(delta: Float) {
        var way = player.effectiveSpeed * delta
        while (navMode == NavMode.AUTOPILOT && path.isNotEmpty()) {
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
                continue
            }
            if (way <= 0f) return

            val nx = dx / dist
            val ny = dy / dist
            val step = way.coerceAtMost(dist)
            tryMove(nx * step, ny * step)
            shipHeadingDeg = MathUtils.atan2(ny, nx) * MathUtils.radDeg
            way -= step
        }
    }

    /** Chasing a ship the player deliberately clicked on (see [handleInput]): a direct-line
     *  pursuit rather than an A* path, since re-pathing every frame toward a moving target would
     *  be expensive. Manual WASD input cancels the hunt exactly like it cancels autopilot. */
    fun updateShipHunt(delta: Float) {
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

    /** Moves the player's ship in slices no longer than [PLAYER_MOVE_SLICE], each checked for
     *  water: only the far end used to be tested, so one long frame at speed could carry the ship
     *  clean over a strip of land (REVIEW.md, finding 15). */
    private fun tryMove(dx: Float, dy: Float) {
        val slices = kotlin.math.ceil(kotlin.math.sqrt(dx * dx + dy * dy) / PLAYER_MOVE_SLICE).toInt().coerceAtLeast(1)
        repeat(slices) { moveSlice(dx / slices, dy / slices) }
    }

    private fun moveSlice(dx: Float, dy: Float) {
        val newX = shipPosition.x + dx
        val newY = shipPosition.y + dy
        when {
            worldMap.isWater(newX, newY) -> shipPosition.set(newX, newY)
            worldMap.isWater(newX, shipPosition.y) -> shipPosition.x = newX
            worldMap.isWater(shipPosition.x, newY) -> shipPosition.y = newY
        }
    }
}
