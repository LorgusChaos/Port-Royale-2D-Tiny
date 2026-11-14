package com.portroyale.tiny.save

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.diagnostics.GameLog
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.world.AiShip
import com.portroyale.tiny.world.AiShipRole
import com.portroyale.tiny.world.FleetPathEntry
import com.portroyale.tiny.world.GameWorld
import com.portroyale.tiny.world.NavMode
import com.portroyale.tiny.world.PirateRaid
import com.portroyale.tiny.world.PirateVoyage
import com.portroyale.tiny.world.RaidPhase
import com.portroyale.tiny.world.SeaEventPoint
import com.portroyale.tiny.world.SeaEventType
import com.portroyale.tiny.world.SinkMark

/*
 * The world in a save (Project design/decisions/full-save.md; REVIEW.md, finding 12 as revised by
 * the author): every AI ship with everything it was doing, the raid at its stage and on its clock,
 * the sea events where they lay, the player's course and hunt, the captains' detours. A load puts
 * the player back in the same sea, not a fresh one.
 *
 * Plain no-arg POJOs for libGDX's reflection-based Json, like the rest of the save. Points are
 * float arrays - [x, y], a path as x0, y0, x1, y1... - and an absent point is an empty array.
 * Ships refer to one another by id, captains by their place in the player's roster.
 */

/** Bumped when the shape below changes in a way older code cannot read; an unknown version
 *  loads as a fresh world rather than as a wrong one. */
const val WORLD_SAVE_VERSION = 1

class WorldSaveData {
    var version: Int = 0
    var nextShipId: Int = 1
    var ships: MutableList<AiShipSaveEntry> = mutableListOf()
    var hasRaid: Boolean = false
    var raid: RaidSaveEntry = RaidSaveEntry()
    var secondsUntilNextRaid: Float = 0f
    var navalEscalation: MutableMap<String, Int> = mutableMapOf()
    var seaEvents: MutableList<SeaEventSaveEntry> = mutableListOf()
    var huntedShipId: Int = -1
    var navMode: String = NavMode.MANUAL.name
    var path: FloatArray = FloatArray(0)
    var pathIndex: Int = 0
    var destinationPort: String = ""
    var shipHeadingDeg: Float = 0f
    var fleetScanOffset: Int = 0
    var fleetPaths: MutableList<FleetPathSaveEntry> = mutableListOf()
}

class AiShipSaveEntry {
    var id: Int = 0
    var role: String = AiShipRole.TRADE.name
    var faction: String = ""
    var spritePath: String = ""
    var tint: Int = -1
    var speed: Float = 0f
    var maxHull: Int = 0
    var minDamage: Int = 0
    var maxDamage: Int = 0
    var goldReward: Int = 0
    var tier: Int = 1
    var maneuverability: Int = 50
    var position: FloatArray = FloatArray(0)
    var headingDeg: Float = 0f
    var path: FloatArray = FloatArray(0)
    var pathIndex: Int = 0
    var trackedWaypoint: FloatArray = FloatArray(0)
    var closestApproach: Float = Float.MAX_VALUE
    var stalledFor: Float = 0f
    var currentHull: Int = 0
    var repairProgress: Float = 0f
    var quarryId: Int = -1
    var chaseSeconds: Float = 0f
    var chaseBestRange: Float = Float.MAX_VALUE
    var chaseStalledFor: Float = 0f
    var repathCooldown: Float = 0f
    var retargetCooldown: Float = 0f
    var fleeingFromId: Int = -1
    var fleeCommitment: Float = 0f
    var voyage: String = PirateVoyage.TRANSIT.name
    var cruisingGround: FloatArray = FloatArray(0)
    var cruiseSecondsLeft: Float = 0f
    var huntCheckCooldown: Float = 0f
    var wake: FloatArray = FloatArray(0)
    var groundCovered: Float = 0f
    var odometerMark: FloatArray = FloatArray(0)
    var destination: FloatArray = FloatArray(0)
    var routeRelays: Int = 0
    var anchor: FloatArray = FloatArray(0)
    var outbound: Boolean = true
    var attackCooldown: Float = 0f
    var avoidedThreatId: Int = -1
    var threatCheckCooldown: Float = 0f
    var giveUpCooldown: Float = 0f
    var isPursuing: Boolean = false
    var pursuingCaptain: Int = -1
    var pursuitBlockedFor: Float = 0f
}

class RaidSaveEntry {
    var portA: String = ""
    var portB: String = ""
    var centre: FloatArray = FloatArray(0)
    var radius: Float = 0f
    var secondsRemaining: Float = 0f
    var responseDelay: Float = 0f
    var rallyPoint: FloatArray = FloatArray(0)
    var phase: String = RaidPhase.GATHERING.name
    var announced: Boolean = false
    var responseSent: Boolean = false
    var gatherSecondsLeft: Float = 0f
    var band: IntArray = IntArray(0)
    var displacedIds: IntArray = IntArray(0)
    var displacedAnchors: FloatArray = FloatArray(0)
    var marks: FloatArray = FloatArray(0)
    var markWasPirate: BooleanArray = BooleanArray(0)
    var squadrons: MutableList<SquadronSaveEntry> = mutableListOf()
}

class SquadronSaveEntry {
    var faction: String = ""
    var ships: IntArray = IntArray(0)
}

class SeaEventSaveEntry {
    var point: FloatArray = FloatArray(0)
    var type: String = ""
    var respawnTimer: Float = 0f
}

class FleetPathSaveEntry {
    var captain: Int = -1
    var origin: String = ""
    var dest: String = ""
    var path: FloatArray = FloatArray(0)
    var avoidedThreatId: Int = -1
}

object WorldSave {

    fun capture(world: GameWorld, player: Player): WorldSaveData {
        val data = WorldSaveData()
        data.version = WORLD_SAVE_VERSION
        data.nextShipId = AiShip.nextId
        data.ships = world.aiShips.map { capture(it, player) }.toMutableList()
        world.activeRaid?.let { raid ->
            data.hasRaid = true
            data.raid = capture(raid)
        }
        data.secondsUntilNextRaid = world.secondsUntilNextRaid
        data.navalEscalation = world.navalEscalation.entries.associate { it.key.name to it.value }.toMutableMap()
        data.seaEvents = world.seaEventSlots.map { slot ->
            SeaEventSaveEntry().apply {
                point = point(slot.point?.position)
                type = slot.point?.type?.name ?: ""
                respawnTimer = slot.respawnTimer
            }
        }.toMutableList()
        data.huntedShipId = world.huntedShip?.id ?: -1
        data.navMode = world.navMode.name
        data.path = path(world.path)
        data.pathIndex = world.pathIndex
        data.destinationPort = world.destinationPort?.port?.name ?: ""
        data.shipHeadingDeg = world.shipHeadingDeg
        data.fleetScanOffset = world.fleetScanOffset
        data.fleetPaths = world.fleetPaths.mapNotNull { (captain, entry) ->
            val index = player.captains.indexOf(captain)
            if (index < 0) return@mapNotNull null
            FleetPathSaveEntry().apply {
                this.captain = index
                origin = entry.origin
                dest = entry.dest
                path = path(entry.path)
                avoidedThreatId = entry.avoidedThreat?.id ?: -1
            }
        }.toMutableList()
        return data
    }

    /**
     * Puts the saved sea into [world], replacing whatever it held; [player] must already hold the
     * saved roster, since captains are found by their place in it. False - and [world] untouched -
     * for a version this code does not know, so the caller can build a fresh world instead.
     * A reference to a ship, captain or port that is no longer there comes back empty.
     */
    fun restore(data: WorldSaveData, world: GameWorld, player: Player): Boolean {
        if (data.version != WORLD_SAVE_VERSION) return false

        val ships = data.ships.mapNotNull(::restoreShip)
        val byId = ships.associateBy { it.id }
        for ((ship, entry) in ships.zip(data.ships.filter { e -> ships.any { it.id == e.id } })) {
            ship.quarry = byId[entry.quarryId]
            ship.fleeingFrom = byId[entry.fleeingFromId]
            ship.avoidedThreat = byId[entry.avoidedThreatId]
            ship.pursuingCaptain = player.captains.getOrNull(entry.pursuingCaptain)
            // A pursuit of a captain who is not in the roster is no pursuit at all.
            if (ship.isPursuing && entry.pursuingCaptain >= 0 && ship.pursuingCaptain == null) ship.isPursuing = false
        }
        world.aiShips.clear()
        world.aiShips.addAll(ships)
        AiShip.nextId = maxOf(data.nextShipId, (ships.maxOfOrNull { it.id } ?: 0) + 1)

        world.activeRaid = if (data.hasRaid) restoreRaid(data.raid, byId) else null
        world.secondsUntilNextRaid = data.secondsUntilNextRaid
        world.navalEscalation.clear()
        for ((name, level) in data.navalEscalation) {
            Faction.entries.firstOrNull { it.name == name }?.let { world.navalEscalation[it] = level }
        }

        world.seaEventSlots.forEachIndexed { index, slot ->
            val saved = data.seaEvents.getOrNull(index)
            val type = saved?.type?.let { name -> SeaEventType.entries.firstOrNull { it.name == name } }
            val at = saved?.point?.let(::pointOf)
            slot.point = if (type != null && at != null) SeaEventPoint(at, type) else null
            slot.respawnTimer = saved?.respawnTimer ?: 0f
        }

        world.huntedShip = byId[data.huntedShipId]
        world.navMode = NavMode.entries.firstOrNull { it.name == data.navMode } ?: NavMode.MANUAL
        world.path = pathOf(data.path)
        world.pathIndex = data.pathIndex.coerceIn(0, maxOf(0, world.path.size))
        world.destinationPort = world.ports.firstOrNull { it.port.name == data.destinationPort }
        // A course that has lost its way (a path gone empty) is no course.
        if (world.navMode == NavMode.AUTOPILOT && world.pathIndex >= world.path.size) {
            world.navMode = NavMode.MANUAL
            world.path = emptyList()
            world.destinationPort = null
        }
        world.shipHeadingDeg = data.shipHeadingDeg
        world.fleetScanOffset = data.fleetScanOffset

        world.fleetPaths.clear()
        for (entry in data.fleetPaths) {
            val captain = player.captains.getOrNull(entry.captain) ?: continue
            world.fleetPaths[captain] = FleetPathEntry(entry.origin, entry.dest, pathOf(entry.path), byId[entry.avoidedThreatId])
        }
        return true
    }

    // --- one ship -----------------------------------------------------------------------------

    private fun capture(ship: AiShip, player: Player) = AiShipSaveEntry().apply {
        id = ship.id
        role = ship.role.name
        faction = ship.faction?.name ?: ""
        spritePath = ship.spritePath
        tint = Color.rgba8888(ship.tint)
        speed = ship.speed
        maxHull = ship.maxHull
        minDamage = ship.minDamage
        maxDamage = ship.maxDamage
        goldReward = ship.goldReward
        tier = ship.tier
        maneuverability = ship.maneuverability
        position = point(ship.position)
        headingDeg = ship.headingDeg
        path = path(ship.path)
        pathIndex = ship.pathIndex
        trackedWaypoint = point(ship.trackedWaypoint)
        closestApproach = ship.closestApproach
        stalledFor = ship.stalledFor
        currentHull = ship.currentHull
        repairProgress = ship.repairProgress
        quarryId = ship.quarry?.id ?: -1
        chaseSeconds = ship.chaseSeconds
        chaseBestRange = ship.chaseBestRange
        chaseStalledFor = ship.chaseStalledFor
        repathCooldown = ship.repathCooldown
        retargetCooldown = ship.retargetCooldown
        fleeingFromId = ship.fleeingFrom?.id ?: -1
        fleeCommitment = ship.fleeCommitment
        voyage = ship.voyage.name
        cruisingGround = point(ship.cruisingGround)
        cruiseSecondsLeft = ship.cruiseSecondsLeft
        huntCheckCooldown = ship.huntCheckCooldown
        wake = path(ship.wake)
        groundCovered = ship.groundCovered
        odometerMark = point(ship.odometerMark)
        destination = point(ship.destination)
        routeRelays = ship.routeRelays
        anchor = point(ship.anchor)
        outbound = ship.outbound
        attackCooldown = ship.attackCooldown
        avoidedThreatId = ship.avoidedThreat?.id ?: -1
        threatCheckCooldown = ship.threatCheckCooldown
        giveUpCooldown = ship.giveUpCooldown
        isPursuing = ship.isPursuing
        pursuingCaptain = ship.pursuingCaptain?.let { player.captains.indexOf(it) } ?: -1
        pursuitBlockedFor = ship.pursuitBlockedFor
    }

    /** One ship back, references left for [restore] to wire up; null for a ship whose role or
     *  position cannot be read, which is dropped rather than guessed at. */
    private fun restoreShip(entry: AiShipSaveEntry): AiShip? {
        val role = AiShipRole.entries.firstOrNull { it.name == entry.role } ?: return dropped(entry, "role ${entry.role}")
        val at = pointOf(entry.position) ?: return dropped(entry, "no position")
        val ship = AiShip(
            role = role,
            faction = Faction.entries.firstOrNull { it.name == entry.faction },
            spritePath = entry.spritePath,
            tint = Color(entry.tint),
            speed = entry.speed,
            maxHull = entry.maxHull,
            minDamage = entry.minDamage,
            maxDamage = entry.maxDamage,
            goldReward = entry.goldReward,
            tier = entry.tier,
            position = at,
            maneuverability = entry.maneuverability,
            id = entry.id
        )
        ship.headingDeg = entry.headingDeg
        ship.path = pathOf(entry.path)
        ship.pathIndex = entry.pathIndex.coerceIn(0, ship.path.size)
        // Pilot knows the waypoint it is watching by identity, not by value (Pilot.follow), so the
        // watch is pointed at the very object in the restored path - a fresh copy would make every
        // load restart each ship's stall clock.
        val tracked = pointOf(entry.trackedWaypoint)
        ship.trackedWaypoint = tracked?.let { t ->
            ship.path.getOrNull(ship.pathIndex)?.takeIf { it == t } ?: ship.path.firstOrNull { it == t } ?: t
        }
        ship.closestApproach = entry.closestApproach
        ship.stalledFor = entry.stalledFor
        ship.currentHull = entry.currentHull.coerceIn(1, maxOf(1, entry.maxHull))
        ship.repairProgress = entry.repairProgress
        ship.chaseSeconds = entry.chaseSeconds
        ship.chaseBestRange = entry.chaseBestRange
        ship.chaseStalledFor = entry.chaseStalledFor
        ship.repathCooldown = entry.repathCooldown
        ship.retargetCooldown = entry.retargetCooldown
        ship.fleeCommitment = entry.fleeCommitment
        ship.voyage = PirateVoyage.entries.firstOrNull { it.name == entry.voyage } ?: PirateVoyage.TRANSIT
        ship.cruisingGround = pointOf(entry.cruisingGround)
        ship.cruiseSecondsLeft = entry.cruiseSecondsLeft
        ship.huntCheckCooldown = entry.huntCheckCooldown
        ship.wake.clear()
        ship.wake.addAll(pathOf(entry.wake))
        ship.groundCovered = entry.groundCovered
        pointOf(entry.odometerMark)?.let { ship.odometerMark.set(it) }
        ship.destination = pointOf(entry.destination)
        ship.routeRelays = entry.routeRelays
        ship.anchor = pointOf(entry.anchor) ?: at.cpy()
        ship.outbound = entry.outbound
        ship.attackCooldown = entry.attackCooldown
        ship.threatCheckCooldown = entry.threatCheckCooldown
        ship.giveUpCooldown = entry.giveUpCooldown
        ship.isPursuing = entry.isPursuing
        ship.pursuitBlockedFor = entry.pursuitBlockedFor
        return ship
    }

    private fun dropped(entry: AiShipSaveEntry, why: String): AiShip? {
        GameLog.log(GameLog.Category.SAVE, "saved ship #${entry.id} dropped on load: $why")
        return null
    }

    // --- the raid -----------------------------------------------------------------------------

    private fun capture(raid: PirateRaid) = RaidSaveEntry().apply {
        portA = raid.portA
        portB = raid.portB
        centre = point(raid.centre)
        radius = raid.radius
        secondsRemaining = raid.secondsRemaining
        responseDelay = raid.responseDelay
        rallyPoint = point(raid.rallyPoint)
        phase = raid.phase.name
        announced = raid.announced
        responseSent = raid.responseSent
        gatherSecondsLeft = raid.gatherSecondsLeft
        band = raid.band.map { it.id }.toIntArray()
        displacedIds = raid.displacedAnchors.keys.map { it.id }.toIntArray()
        displacedAnchors = path(raid.displacedAnchors.values.toList())
        marks = path(raid.marks.map { it.position })
        markWasPirate = raid.marks.map { it.wasPirate }.toBooleanArray()
        squadrons = raid.squadrons.map { (faction, ships) ->
            SquadronSaveEntry().apply {
                this.faction = faction.name
                this.ships = ships.map { it.id }.toIntArray()
            }
        }.toMutableList()
    }

    /** The raid back with the ships it holds; members that did not come back are simply not in
     *  it - a raid left with no pirates ends by its own rules on the next tick. */
    private fun restoreRaid(entry: RaidSaveEntry, byId: Map<Int, AiShip>): PirateRaid? {
        val centre = pointOf(entry.centre) ?: return null
        val rally = pointOf(entry.rallyPoint) ?: return null
        val raid = PirateRaid(
            portA = entry.portA,
            portB = entry.portB,
            centre = centre,
            radius = entry.radius,
            secondsRemaining = entry.secondsRemaining,
            responseDelay = entry.responseDelay,
            rallyPoint = rally
        )
        raid.phase = RaidPhase.entries.firstOrNull { it.name == entry.phase } ?: RaidPhase.GATHERING
        if (entry.announced) raid.markAnnounced()
        if (entry.responseSent) raid.markResponseSent()
        raid.gatherSecondsLeft = entry.gatherSecondsLeft
        entry.band.asList().mapNotNullTo(raid.band) { byId[it] }
        val anchors = pathOf(entry.displacedAnchors)
        entry.displacedIds.forEachIndexed { index, shipId ->
            val ship = byId[shipId] ?: return@forEachIndexed
            val anchor = anchors.getOrNull(index) ?: return@forEachIndexed
            raid.displacedAnchors[ship] = anchor
        }
        pathOf(entry.marks).forEachIndexed { index, at ->
            raid.marks += SinkMark(at, entry.markWasPirate.getOrElse(index) { true })
        }
        for (squadron in entry.squadrons) {
            val faction = Faction.entries.firstOrNull { it.name == squadron.faction } ?: continue
            raid.squadrons[faction] = squadron.ships.asList().mapNotNull { byId[it] }.toMutableList()
        }
        return raid
    }

    // --- points -------------------------------------------------------------------------------

    private fun point(v: Vector2?): FloatArray = if (v == null) FloatArray(0) else floatArrayOf(v.x, v.y)

    private fun pointOf(a: FloatArray): Vector2? = if (a.size >= 2) Vector2(a[0], a[1]) else null

    private fun path(points: List<Vector2>): FloatArray {
        val out = FloatArray(points.size * 2)
        points.forEachIndexed { i, p ->
            out[i * 2] = p.x
            out[i * 2 + 1] = p.y
        }
        return out
    }

    private fun pathOf(a: FloatArray): List<Vector2> = (0 until a.size / 2).map { Vector2(a[it * 2], a[it * 2 + 1]) }
}
