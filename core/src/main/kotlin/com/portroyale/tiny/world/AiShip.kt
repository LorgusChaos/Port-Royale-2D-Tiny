package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.ShipTypes

enum class AiShipRole { TRADE, MILITARY, PIRATE }

/**
 * Where a pirate is in its cruise, which is what decides whether it starts fights.
 *
 * A pirate that is always willing to attack never finishes a voyage: it leaves the lair, finds
 * prey within sight, chases, fights, reloads, finds more prey - and so spends its whole life in
 * the water it was born in. The map beyond stays untouched while the shipping near the lair is
 * cut to pieces. Splitting the cruise into legs fixes that at the root: only the middle leg
 * hunts, so the outward and homeward legs are what actually spread pirates across the Caribbean.
 *
 * Note this governs *starting* a fight with merchantmen only. Navies and pirates react to each
 * other on any leg, and a merchantman treats any pirate as a danger whatever it happens to be
 * doing - a pirate that shipping sails calmly past would look absurd.
 */
enum class PirateVoyage { TRANSIT, CRUISING, HOMEBOUND }

/** A roaming, non-player ship: trade/military ships belong to a Faction and sail between that
 *  faction's ports; pirates belong to no one and wander between any port. Stats are a flat
 *  per-role template (see [AiShipFactory]), not randomized - a rough first pass, tune later.
 *  [tier] (1-5, matching the ship2_<color>_<tier> sprite set and ShipTypes.ALL's own order) is
 *  what a pirate's fight-or-flee decision compares against the player's own ship tier - see
 *  PortRoyaleGame.isHostile/updateAiShips. */
class AiShip(
    val role: AiShipRole,
    val faction: Faction?,
    val spritePath: String,
    val tint: Color,
    val speed: Float,
    val maxHull: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val goldReward: Int,
    val tier: Int,
    val position: Vector2,
    /** How smartly this hull answers her helm - the same statistic the ship classes carry and
     *  combat already uses. Defaulted so the many tests that build ships by hand need not care. */
    val maneuverability: Int = 50
) {
    /**
     * Damage this hull is carrying. Fights used to be resolved from [maxHull] every time, so a
     * warship that had just been through three battles met the fourth as good as new - which made
     * navies effectively unsinkable and the escalating-waves rule unreachable. Wear persists here
     * instead, and a ship that has been in the thick of it is genuinely easier to finish.
     */
    /**
     * A name that stays with this hull for as long as it is afloat.
     *
     * Diagnostics used to identify a ship by its index in the fleet list, which shifts every time
     * anything sinks or respawns - so the watchdog's "report each problem once" rule silently
     * broke down, one stuck ship was reported under a dozen different names, and forty honest
     * lines became fourteen hundred. Identity has to outlive the list holding it.
     */
    val id: Int = nextId++

    var currentHull: Int = maxHull

    /** Fractional hull points carried between frames, so a slow repair rate is not rounded to
     *  nothing every tick. */
    var repairProgress: Float = 0f

    /**
     * The ship this one has decided to run down, if any. Held rather than recomputed each frame
     * for the same reason pursuit of the player is: a hunter that re-picks its target every tick
     * loses it the moment the target juks sideways, which reads as obliviousness rather than
     * as a chase. Cleared on contact, on losing sight, or on either party reaching harbour.
     */
    var quarry: AiShip? = null

    /** How long the current chase has been running. Every chase has an end - see
     *  Encounters.CHASE_MAX_SECONDS. */
    var chaseSeconds: Float = 0f

    /** The closest this hunter has managed to get during the current chase, and how long it has
     *  been failing to beat that. Together they are how a ship works out that it is simply not
     *  going to catch this one: if the range stops shrinking, the prey is faster, and no amount
     *  of persistence changes that. */
    var chaseBestRange: Float = Float.MAX_VALUE
    var chaseStalledFor: Float = 0f

    /** Time until this ship re-lays its course onto its quarry's current position. A chase is a
     *  route like any other, refreshed periodically - not per-frame steering. */
    var repathCooldown: Float = 0f

    /** Time to wait before asking for a route again after being refused one. Without it a ship
     *  that cannot be routed anywhere asks several times a frame, for ever - and thirty such
     *  ships between them once brought the whole game to a visible crawl. */
    var retargetCooldown: Float = 0f

    /** The hunter this ship has committed to running from, and how long it goes on believing in
     *  the escape after that hunter loses interest. Choosing a refuge once and sticking to it is
     *  the whole point: re-deciding every frame is what had merchantmen visibly jinking back and
     *  forth instead of running. */
    var fleeingFrom: AiShip? = null
    var fleeCommitment: Float = 0f

    /** Which leg of its cruise a pirate is on, and where it is going to work. Traders and
     *  warships ignore both. */
    var voyage: PirateVoyage = PirateVoyage.TRANSIT
    var cruisingGround: Vector2? = null
    var cruiseSecondsLeft: Float = 0f

    /** Seconds until this ship next looks around for something worth attacking. The sizing-up is
     *  a roll, so re-rolling it sixty times a second would make every sighting a certainty. */
    var huntCheckCooldown: Float = 0f

    /** Degrees per second this hull can come round - see Helm. */
    val turnRate: Float get() = Helm.turnRate(maneuverability)

    /**
     * Where this ship has actually been, sampled every so often, for anyone following it.
     *
     * A ship in line astern used to be handed the *leader's current route* and told to join it
     * partway along. That route starts from where the leader is now, so the moment the leader
     * rounded a headland the follower was aimed at a waypoint on the far side of the land - and
     * sailed straight into the beach. Following the water the leader has demonstrably already
     * sailed through cannot have that problem.
     */
    val wake: MutableList<Vector2> = mutableListOf()

    var headingDeg: Float = 0f
    var path: List<Vector2> = emptyList()
    var pathIndex: Int = 0

    /**
     * The point this ship lives around - its home port for a faction ship, the lair for a pirate.
     * Destinations are weighted by distance from HERE rather than from wherever the ship happens
     * to be, and that distinction is the whole mechanic: weighting from the current position
     * gives short hops but an unbounded walk, which is just aimless wandering at a slower pace.
     * Weighting from a fixed anchor produces an orbit, and an orbit is what reads as territory.
     */
    var anchor: Vector2 = position.cpy()

    /**
     * Which half of an out-and-back cycle this ship is on. Warships patrol away from their home
     * port and return to it; pirates sortie from the lair, prowl a port's outskirts and go home
     * again. Traders ignore it - they genuinely do go port to port for a living.
     */
    var outbound: Boolean = true

    /** Seconds before this ship will take another merchantman. Without it a band on station
     *  falls on the same trader three at once and empties a corridor in seconds, which reads as a
     *  bug rather than as a raid. */
    var attackCooldown: Float = 0f

    /** The pirate this ship has already re-routed around, so a lookout does not re-plan the same
     *  evasion every few seconds. Cleared once the sea is empty again. */
    var avoidedThreat: AiShip? = null
    var threatCheckCooldown: Float = 0f

    /** World-seconds (see PortRoyaleGame.updateAiShips - already scaled by GameSpeed, same as
     *  SeaEventSlot.respawnTimer) until this ship will attempt to engage its last failed target
     *  again. Set on a failed catch (player fled combat, or a fleet captain evaded) - see
     *  PortRoyaleGame.giveUpCooldownFor - and gates both checkAiEncounters and
     *  updateFleetEncounters so a ship that just lost its prey doesn't re-roll every frame it's
     *  still nearby. Zero means "free to engage", the default for a ship that's never failed. */
    var giveUpCooldown: Float = 0f

    /** True while this ship has broken off its patrol/wander path to actively close in on a
     *  spotted target (see PortRoyaleGame.updateAiShips) - [pursuingCaptain] identifies WHICH
     *  target when it's a fleet captain's ship, null while pursuing means the player themselves.
     *  Catching up (reaching engage range) while this is true skips the player's pre-combat
     *  Fight/Sail On choice entirely - see checkAiEncounters - since a target that was hunted
     *  down doesn't get a free warning the way one stumbled into by chance does. */
    var isPursuing: Boolean = false
    var pursuingCaptain: Captain? = null

    /** How long this ship has been trying to close on its target with land in the way. Pursuit
     *  steers in a straight line and has no route around obstacles, so without this a pursuer
     *  that ends up behind a headland - or outside the port its quarry escaped into - sits there
     *  forever, unable to advance and never giving up. See PortRoyaleGame.updatePursuit. */
    var pursuitBlockedFor: Float = 0f

    val displayName: String
        get() = when (role) {
            AiShipRole.PIRATE -> "Pirate Ship"
            AiShipRole.TRADE -> "${faction?.displayName} Trader"
            AiShipRole.MILITARY -> "${faction?.displayName} Warship"
        }

    private companion object {
        /** Monotonic, never reused within a session - see [id]. */
        var nextId: Int = 1
    }
}

/** Stat templates per role. Trade ships are weak and worth a modest reward (easy prey, the
 *  point of piracy); military ships are the toughest fight but pay best; pirates roll a random
 *  tier (1-5) each spawn - both their stats/reward AND their sprite (a bigger hull for a bigger
 *  tier) scale with it, and a low-tier pirate that runs into a much better-armed player flees
 *  rather than picking a fight it can't win (see PortRoyaleGame.isHostile).
 *
 *  Sprites come from the same 5-tier ship2_<color>_<tier> set as the player's own ships (see
 *  ShipTypes) - color carries faction identity here (no tint multiply on top, the color IS the
 *  sail), tier carries role weight (trade = a modest tier-2 hull, military = a bigger tier-4
 *  hull, pirates = whatever tier they rolled). [speed] is pulled from that same tier's real
 *  ShipTypes entry, not a separate hand-tuned number - a ship's hull type is what determines how
 *  fast it sails regardless of who owns it (player, a hired captain, or an NPC faction), same
 *  reasoning the player's own effectiveSpeed and FleetSimulation's leg timing both already use.
 *  maxHull/damage/goldReward stay separately tuned per role on purpose (trade = deliberately
 *  weak/easy prey, pirates = a strict tier difficulty ladder) - ShipTypes' own hull numbers
 *  aren't a strict power ladder (a Cutter trades hull for speed), so reusing them there would
 *  undermine the tier-vs-tier fight-or-flee comparison. */
object AiShipFactory {
    private const val TRADE_TIER = 2
    private const val MILITARY_TIER = 4

    private val TRADE_SPRITES = mapOf(
        Faction.SPAIN to "sprites/ships/ship2_cream_2.png",
        Faction.ENGLAND to "sprites/ships/ship2_red_2.png",
        Faction.FRANCE to "sprites/ships/ship2_blue_2.png",
        Faction.NETHERLANDS to "sprites/ships/ship2_green_2.png"
    )
    private val MILITARY_SPRITES = mapOf(
        Faction.SPAIN to "sprites/ships/ship2_cream_4.png",
        Faction.ENGLAND to "sprites/ships/ship2_red_4.png",
        Faction.FRANCE to "sprites/ships/ship2_blue_4.png",
        Faction.NETHERLANDS to "sprites/ships/ship2_green_4.png"
    )
    private val PIRATE_SPRITES = (1..5).associateWith { "sprites/ships/ship2_black_$it.png" }

    /** Every sprite path an AiShip can be created with - PortRoyaleGame preloads exactly these
     *  (plus each purchasable ShipType's own sprite) into its texture map, so this list must stay
     *  in sync with the paths actually used below. */
    val ALL_SPRITES = (TRADE_SPRITES.values + MILITARY_SPRITES.values + PIRATE_SPRITES.values).distinct()

    fun trade(faction: Faction, position: Vector2) = AiShip(
        role = AiShipRole.TRADE,
        faction = faction,
        spritePath = TRADE_SPRITES.getValue(faction),
        tint = Color.WHITE,
        speed = ShipTypes.ALL[TRADE_TIER - 1].speed,
        maxHull = 40,
        minDamage = 3,
        maxDamage = 8,
        goldReward = 120,
        tier = TRADE_TIER,
        position = position,
        maneuverability = ShipTypes.ALL[TRADE_TIER - 1].baseManeuverability
    )

    fun military(faction: Faction, position: Vector2) = AiShip(
        role = AiShipRole.MILITARY,
        faction = faction,
        spritePath = MILITARY_SPRITES.getValue(faction),
        tint = Color.WHITE,
        speed = ShipTypes.ALL[MILITARY_TIER - 1].speed,
        maxHull = 160,
        minDamage = 18,
        maxDamage = 30,
        goldReward = 250,
        tier = MILITARY_TIER,
        position = position,
        maneuverability = ShipTypes.ALL[MILITARY_TIER - 1].baseManeuverability
    )

    fun pirate(position: Vector2): AiShip {
        val tier = GameRandom.nextIntInclusive(1, 5)
        return AiShip(
            role = AiShipRole.PIRATE,
            faction = null,
            spritePath = PIRATE_SPRITES.getValue(tier),
            tint = Color.WHITE,
            speed = ShipTypes.ALL[tier - 1].speed,
            maxHull = 25 + tier * 25,
            minDamage = 3 + tier * 4,
            maxDamage = 6 + tier * 7,
            goldReward = 40 + tier * 45,
            tier = tier,
            position = position,
            maneuverability = ShipTypes.ALL[tier - 1].baseManeuverability
        )
    }
}
