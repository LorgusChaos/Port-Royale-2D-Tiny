package com.portroyale.tiny.combat

import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.world.AiShip

enum class FleetCombatResult { VICTORY, FLED, DEFEAT }

data class FleetCombatOutcome(val result: FleetCombatResult, val remainingHull: Int, val goldReward: Int)

private const val FLEE_THRESHOLD_PER_CAUTION = 0.8f
private const val MAX_ROUNDS = 200

/**
 * Resolves a fleet captain's fight the instant it happens, with no screen - reuses exactly the
 * same formulas as the player's own turn-based fight (see CombatState's now-shared constants),
 * just run to completion in one call instead of one round per click, and with a simple threshold
 * rule standing in for the player's own Fire/Flee choice each round (see [resolve]'s [caution]
 * param) - not real AI, the same trick old games used for "reasonable-looking" NPCs.
 */
object FleetCombatResolver {

    /** [caution] (0.2 bold .. 0.9 cautious, see Captain) sets how much hull the captain is
     *  willing to lose before trying to break off each round - a bold captain fights down to a
     *  sliver, a cautious one peels off early. Below that threshold the captain always attempts
     *  to flee instead of firing (same flee-chance formula as the player's own escape attempt).
     *  A capped round count guards against an absurd streak of double-misses looping forever -
     *  if it's hit, the fight is called a break-off rather than an infinite draw. */
    fun resolve(
        shipMaxHull: Int,
        shipStartHull: Int,
        shipMinDamage: Int,
        shipMaxDamage: Int,
        shipManeuverability: Int,
        caution: Float,
        enemy: AiShip
    ): FleetCombatOutcome {
        var shipHull = shipStartHull.coerceAtLeast(1)
        var enemyHull = enemy.maxHull
        val fleeChance = (BASE_FLEE_CHANCE + shipManeuverability * FLEE_CHANCE_PER_MANEUVER).coerceAtMost(MAX_FLEE_CHANCE)
        val enemyMissChance = (MISS_CHANCE + shipManeuverability * EVASION_PER_MANEUVER).coerceAtMost(MAX_ENEMY_MISS_CHANCE)
        val fleeThreshold = caution * FLEE_THRESHOLD_PER_CAUTION

        repeat(MAX_ROUNDS) {
            val hullFraction = shipHull.toFloat() / shipMaxHull
            if (hullFraction <= fleeThreshold) {
                if (GameRandom.nextFloat() < fleeChance) {
                    return FleetCombatOutcome(FleetCombatResult.FLED, shipHull, 0)
                }
            } else {
                if (GameRandom.nextFloat() >= MISS_CHANCE) {
                    enemyHull = (enemyHull - GameRandom.nextInt(shipMinDamage, shipMaxDamage + 1)).coerceAtLeast(0)
                }
                if (enemyHull <= 0) {
                    // Same prize the player would collect for the same opponent - see
                    // CombatState's victory branch and AiShip.goldReward.
                    return FleetCombatOutcome(FleetCombatResult.VICTORY, shipHull, enemy.goldReward)
                }
            }

            if (GameRandom.nextFloat() >= enemyMissChance) {
                shipHull = (shipHull - GameRandom.nextInt(enemy.minDamage, enemy.maxDamage + 1)).coerceAtLeast(0)
            }
            if (shipHull <= 0) {
                return FleetCombatOutcome(FleetCombatResult.DEFEAT, 0, 0)
            }
        }
        return FleetCombatOutcome(FleetCombatResult.FLED, shipHull, 0)
    }
}
