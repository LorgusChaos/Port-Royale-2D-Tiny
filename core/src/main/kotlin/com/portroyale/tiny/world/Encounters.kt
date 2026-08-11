package com.portroyale.tiny.world

import kotlin.math.pow

/**
 * Who picks a fight with whom, and whether they actually mean it.
 *
 * This is the baseline behaviour of every ship in the world, in force at all times and everywhere
 * - not something a raid switches on. A raid concentrates pirates on one stretch of water; what
 * they then do to the shipping they find there is decided here, by exactly the same rules that
 * govern a lone pirate meeting a lone merchantman in an empty corner of the map. Events are meant
 * to thicken the world, never to replace how it works.
 *
 * The decision is a roll, not a threshold. A rule of the form "attack if stronger" makes every
 * meeting a foregone conclusion the moment two sprites are close enough to compare, and the world
 * stops being able to surprise anyone. Odds that slide with the strength ratio keep a heavy
 * pirate usually - not always - willing to take on a frigate, and leave a sloop a small honest
 * chance of trying something reckless.
 */
object Encounters {

    /**
     * How close two hulls must be before shots are exchanged: **eight tenths of a ship length**.
     *
     * It used to be 52 units - nearly two hull lengths, and wider than the red mark left on the
     * water where a ship went down. Merchantmen died with a clear gap of open sea between them
     * and the pirate that supposedly boarded them. At this range the sprites genuinely overlap
     * from any angle, so a sinking looks like the collision it is meant to be.
     *
     * Note this is *smaller* than the separation radius that keeps hulls from stacking up, which
     * is why an attacker and its quarry are exempt from separation - otherwise the very rule that
     * stops ships sharing a pixel would hold them permanently out of each other's reach.
     */
    const val CONTACT_RADIUS = WorldScale.TYPICAL_SHIP_LENGTH * 0.8f

    /**
     * How far any ship sees another. **The only sight range in the game.**
     *
     * There used to be three: a hunter noticed prey at 360, a merchantman noticed a pirate at
     * 420, and a pirate noticed the player at 140. All three were invented per role, which is
     * nonsense - a lookout at a masthead sees what there is to see, and does not see further
     * because of what the ship he is standing on happens to do for a living. Ship classes already
     * carry their own statistics; role is not one of them.
     *
     * Making it symmetrical also makes escapes honest. Both ships spot each other at the same
     * moment, so what follows is settled entirely by their speed and handling, which is exactly
     * where it should be settled. At this range a pursuer has 156 units to close before the chase
     * times out, so a slightly faster hull only just runs its prey down and a much faster one does
     * it comfortably.
     */
    const val SIGHT_RADIUS = 180f

    /** How far past sighting range a hunter will keep pushing before writing the chase off. Some
     *  hysteresis, or a target drifting a unit past the line ends the pursuit and instantly
     *  restarts it. */
    const val GIVE_UP_MULTIPLIER = 1.6f

    /**
     * How much a role fancies its chances beyond what the hulls justify. A pirate is after cargo
     * and will take a fight it might lose; a navy is professional rather than desperate; a
     * merchantman has no business attacking anyone and is only ever the one being assessed.
     */
    const val PIRATE_BOLDNESS = 1.6f
    const val NAVY_BOLDNESS = 1.4f
    const val TRADER_BOLDNESS = 1f

    /** How sharply the odds swing around an even match. Higher is more deterministic; 2 keeps a
     *  clear underdog at long odds without ever making it impossible. */
    const val ODDS_SHARPNESS = 2f

    /** Below these odds a ship stops considering a fight and starts working out how to keep out
     *  of the other's way. */
    const val AVOID_ODDS = 0.3f

    /**
     * The longest a hunter will press a chase before writing it off, whatever the odds looked
     * like when it started.
     *
     * There was no limit at all, and that single omission explains most of what the world got
     * wrong: a pirate that could not catch a faster merchantman followed it anyway, indefinitely,
     * which is both why chases looked like two ships jinking pointlessly back and forth and why
     * pirates never got more than a few hundred units from their lair - the chase consumed the
     * entire voyage.
     */
    const val CHASE_MAX_SECONDS = 55f

    /**
     * How long a hunter tolerates not gaining before accepting it cannot catch this one. This is
     * where "only the ships' speeds decide it" stops being a slogan: a hunter that is not closing
     * the range is a hunter with a slower hull, and it works that out for itself.
     */
    const val CHASE_STALL_SECONDS = 12f

    /** How much the range has to actually shrink to count as gaining, so that yawing about does
     *  not read as progress. */
    const val CHASE_PROGRESS_MARGIN = 8f

    /** How long a hunter that gave up leaves everything alone afterwards. Long on purpose: a
     *  break-off that is forgotten in five seconds is not a break-off. */
    const val CHASE_BREAK_OFF_SECONDS = 90f

    /** How often a chase re-lays its course onto the quarry's current position - and how often
     *  once close enough that stale aim would tell. */
    const val CHASE_REPATH_SECONDS = 1.5f
    const val CHASE_CLOSE_REPATH_SECONDS = 0.5f
    const val CHASE_CLOSE_RANGE = 150f

    /** How long a merchantman keeps running after its pursuer has lost interest. */
    const val FLIGHT_COMMITMENT_SECONDS = 20f

    /**
     * Whether a ship in this state will *start* a fight with merchantmen.
     *
     * Only a pirate actually on station does. One on its way out or on its way home has somewhere
     * to be, and letting it stop for every sail it passes is what kept the whole pirate fleet
     * loitering around its own front door.
     */
    fun huntsShipping(role: AiShipRole, voyage: PirateVoyage): Boolean = when (role) {
        AiShipRole.PIRATE -> voyage == PirateVoyage.CRUISING
        AiShipRole.MILITARY -> false
        AiShipRole.TRADE -> false
    }

    /** Fighting power as the hulls actually are: what it can still take, times what it deals.
     *  Uses current rather than maximum hull, so a warship worn down by three skirmishes is
     *  correctly read as a softer target by the next pirate to see it. */
    fun strength(ship: AiShip): Float =
        ship.currentHull.coerceAtLeast(0).toFloat() * (ship.minDamage + ship.maxDamage) / 2f

    fun boldnessOf(role: AiShipRole): Float = when (role) {
        AiShipRole.PIRATE -> PIRATE_BOLDNESS
        AiShipRole.MILITARY -> NAVY_BOLDNESS
        AiShipRole.TRADE -> TRADER_BOLDNESS
    }

    /**
     * Whether [hunter] has any reason to go after [target]. Pirates prey on merchantmen and will
     * fight the navy sent after them; navies hunt pirates. Nation against nation is deliberately
     * absent - there is no war system yet, and inventing one here would start sinking neutral
     * shipping for reasons the player can neither see nor influence.
     */
    fun isHostile(hunter: AiShipRole, target: AiShipRole): Boolean = when (hunter) {
        AiShipRole.PIRATE -> target == AiShipRole.TRADE || target == AiShipRole.MILITARY
        AiShipRole.MILITARY -> target == AiShipRole.PIRATE
        AiShipRole.TRADE -> false
    }

    /**
     * The chance [hunter] presses an attack on [target], from the two ships' real fighting power
     * and the hunter's temperament. An even match at even boldness is a coin toss; the curve is
     * smooth, so nothing is ever certain and nothing is ever barred.
     */
    fun odds(hunter: AiShip, target: AiShip): Float {
        val theirs = strength(target)
        if (theirs <= 0f) return 1f
        val ratio = strength(hunter) / theirs * boldnessOf(hunter.role)
        val weighted = ratio.pow(ODDS_SHARPNESS)
        return weighted / (1f + weighted)
    }

    /** Whether [ship] is outmatched enough by [threat] that it should be laying a course around
     *  it rather than sizing it up. */
    fun shouldAvoid(ship: AiShip, threat: AiShip): Boolean = odds(ship, threat) < AVOID_ODDS
}
