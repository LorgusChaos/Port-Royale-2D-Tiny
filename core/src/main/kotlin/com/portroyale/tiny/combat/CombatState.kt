package com.portroyale.tiny.combat

import com.portroyale.tiny.economy.Player
import kotlin.random.Random

enum class CombatOutcome { ONGOING, VICTORY, DEFEAT, FLED }

// Not private: shared with combat.FleetCombatResolver, which resolves the exact same kind of
// fight for a fleet captain's ship, just instantly and without a screen. Keeping one set of
// numbers means the player's own combat and a captain's can never quietly drift apart.
const val BASE_FLEE_CHANCE = 0.35f
const val FLEE_CHANCE_PER_MANEUVER = 0.004f
const val MAX_FLEE_CHANCE = 0.85f
const val MISS_CHANCE = 0.18f
const val EVASION_PER_MANEUVER = 0.002f
const val MAX_ENEMY_MISS_CHANCE = 0.5f
const val MIN_VICTORY_REWARD = 50
const val MAX_VICTORY_REWARD = 150

/** One turn-based encounter: player picks Fire/Flee each round, enemy always fires back unless
 *  the encounter already ended. Both sides can miss; the player's effective maneuverability
 *  (base stat + oar upgrades) improves both flee odds and the enemy's chance to miss. Hull
 *  damage persists on player.condition.currentHull, so it carries over past this one fight -
 *  playerStats is derived from the player's current ship, so buying a better ship (and
 *  installing upgrades) makes a real difference in combat. */
class CombatState(
    private val player: Player,
    private val playerStats: ShipCombatant,
    private val enemy: ShipCombatant
) {
    val enemyName = enemy.name
    val playerMaxHull = playerStats.maxHull
    val enemyMaxHull = enemy.maxHull
    val playerSpritePath = playerStats.spritePath
    val enemySpritePath = enemy.spritePath

    private val fleeChance: Float
        get() = (BASE_FLEE_CHANCE + player.effectiveManeuverability * FLEE_CHANCE_PER_MANEUVER)
            .coerceAtMost(MAX_FLEE_CHANCE)
    private val enemyMissChance: Float
        get() = (MISS_CHANCE + player.effectiveManeuverability * EVASION_PER_MANEUVER)
            .coerceAtMost(MAX_ENEMY_MISS_CHANCE)

    var playerHull = player.condition.currentHull
        private set(value) {
            field = value
            player.condition.currentHull = value
        }
    var enemyHull = enemyMaxHull
        private set

    var outcome = CombatOutcome.ONGOING
        private set

    val isOver: Boolean get() = outcome != CombatOutcome.ONGOING

    var log = "${enemy.name} closes in! Fire cannons or try to flee."
        private set

    fun playerAttack() {
        if (isOver) return

        var message = if (Random.nextFloat() < MISS_CHANCE) {
            "Your shot goes wide and misses ${enemy.name}."
        } else {
            val damage = Random.nextInt(playerStats.minDamage, playerStats.maxDamage + 1)
            enemyHull = (enemyHull - damage).coerceAtLeast(0)
            "You hit ${enemy.name} for $damage."
        }

        if (enemyHull <= 0) {
            val reward = Random.nextInt(MIN_VICTORY_REWARD, MAX_VICTORY_REWARD + 1)
            player.gold += reward
            outcome = CombatOutcome.VICTORY
            log = "$message ${enemy.name} is sunk! You salvage $reward gold."
            return
        }

        message += " " + enemyStrikesBack()
        log = message
    }

    fun playerFlee() {
        if (isOver) return
        if (Random.nextFloat() < fleeChance) {
            outcome = CombatOutcome.FLED
            log = "You break off and outrun ${enemy.name}."
            return
        }
        log = "Escape failed! " + enemyStrikesBack()
    }

    private fun enemyStrikesBack(): String {
        if (Random.nextFloat() < enemyMissChance) {
            return "${enemy.name}'s shot goes wide and misses you."
        }
        val damage = Random.nextInt(enemy.minDamage, enemy.maxDamage + 1)
        playerHull = (playerHull - damage).coerceAtLeast(0)
        var message = "${enemy.name} hits you for $damage."
        if (playerHull <= 0) {
            outcome = CombatOutcome.DEFEAT
            message += " Your ship is battered - you break off and limp away."
        }
        return message
    }
}
