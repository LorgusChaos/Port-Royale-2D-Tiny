package com.portroyale.tiny.world

import com.badlogic.gdx.math.Vector2

/** Where a ship sank, kept until the raid it belongs to is over so a player arriving late still
 *  sees that something happened here. */
class SinkMark(val position: Vector2, val wasPirate: Boolean)

/**
 * What a raiding band is doing right now.
 *
 * [GATHERING] and [SAILING] exist because a raid that begins with three pirates converging
 * separately from wherever they happened to be reads as three unrelated ships, and spends half its
 * lifetime in transit. They form up first, then travel together, and only then does anything
 * happen - which is also when the player is told, since being told earlier means sailing out to
 * look at empty water.
 */
enum class RaidPhase { GATHERING, SAILING, RAIDING }

/**
 * A stretch of sea gone bad for a while: pirates working a corridor between two ports, and the
 * navies that own those ports coming out to deal with them.
 *
 * The whole thing is expressed through the anchors introduced in M11c. "Pirates have moved into
 * the corridor" is literally "these pirates' anchor is somewhere else for a few minutes", and the
 * ordinary destination weighting then keeps them there without a line of special-case movement
 * code. Nothing is spawned and nothing is destroyed to make a raid happen, which is what stops
 * repeated raids from slowly filling the map with ships.
 *
 * Not saved. Like the AI ships themselves, a raid lives only as long as the session - loading a
 * game starts the world calm, which is honest rather than a lie about what was happening.
 */
class PirateRaid(
    val portA: String,
    val portB: String,
    val centre: Vector2,
    val radius: Float,
    /** Real seconds of raid left. Only counts down once the band is actually on station. */
    var secondsRemaining: Float,
    /** Real seconds before the navies arrive - long enough that the corridor is genuinely
     *  dangerous for a while, rather than a skirmish the player is always too late for. */
    var responseDelay: Float,
    /** Where the band forms up before setting out. */
    val rallyPoint: Vector2
) {
    var phase: RaidPhase = RaidPhase.GATHERING

    /** Ordered: the first surviving entry leads, the rest follow in its wake. */
    val band = mutableListOf<AiShip>()

    var announced: Boolean = false
        private set

    /** Ships pulled into the raid, with the anchor each one had before - restored without fail
     *  when the raid ends, or the ship would spend the rest of the session living in a corridor
     *  it only ever visited. */
    val displacedAnchors = LinkedHashMap<AiShip, Vector2>()

    val marks = mutableListOf<SinkMark>()

    /** Which ships each power committed, so the raid can report back how that power's squadron
     *  fared and size the next one - see PortRoyaleGame.settleEscalation. */
    val squadrons = LinkedHashMap<com.portroyale.tiny.factions.Faction, MutableList<AiShip>>()

    var responseSent: Boolean = false
        private set

    val label: String get() = "$portA - $portB"

    fun contains(position: Vector2): Boolean = position.dst(centre) <= radius

    fun draftIn(ship: AiShip, newAnchor: Vector2) {
        if (displacedAnchors.containsKey(ship)) return
        displacedAnchors[ship] = ship.anchor.cpy()
        ship.anchor = newAnchor.cpy()
        ship.path = emptyList()
        ship.pathIndex = 0
    }

    /** Sends one ship home early - a pirate that survived a fight, or anything that sank. */
    fun release(ship: AiShip) {
        displacedAnchors.remove(ship)?.let { original ->
            ship.anchor = original
            ship.path = emptyList()
            ship.pathIndex = 0
        }
    }

    fun releaseAll() {
        for ((ship, original) in displacedAnchors) {
            ship.anchor = original
            ship.path = emptyList()
            ship.pathIndex = 0
        }
        displacedAnchors.clear()
    }

    fun markResponseSent() {
        responseSent = true
    }

    fun markAnnounced() {
        announced = true
    }

    fun pirates(): List<AiShip> = displacedAnchors.keys.filter { it.role == AiShipRole.PIRATE }

    fun warships(): List<AiShip> = displacedAnchors.keys.filter { it.role == AiShipRole.MILITARY }

    /** Whoever is leading the band right now - the first still afloat. A leader that sinks or is
     *  released must not leave the rest circling an empty patch of sea, so this is resolved fresh
     *  every time rather than stored. */
    fun leader(alive: Collection<AiShip>): AiShip? = band.firstOrNull { it in alive }

    fun followers(alive: Collection<AiShip>): List<AiShip> {
        val head = leader(alive) ?: return emptyList()
        return band.filter { it !== head && it in alive }
    }

    /** True once every surviving member is close enough to the rally point to set out together. */
    fun formedUp(alive: Collection<AiShip>, tolerance: Float): Boolean {
        val members = band.filter { it in alive }
        return members.isNotEmpty() && members.all { it.position.dst(rallyPoint) <= tolerance }
    }
}
