package com.portroyale.tiny.combat

import com.portroyale.tiny.economy.Player
import kotlin.random.Random

enum class CombatOutcome { ONGOING, VICTORY, DEFEAT, FLED }

private const val FLEE_CHANCE = 0.6f
private const val MISS_CHANCE = 0.18f
private const val MIN_VICTORY_REWARD = 50
private const val MAX_VICTORY_REWARD = 150

/** One turn-based encounter: player picks Fire/Flee each round, enemy always fires back unless
 *  the encounter already ended. Both sides can miss. playerStats is derived from the player's
 *  current ship, so buying a better ship makes a real difference in combat. */
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

    var playerHull = playerMaxHull
        private set
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
        if (Random.nextFloat() < FLEE_CHANCE) {
            outcome = CombatOutcome.FLED
            log = "You break off and outrun ${enemy.name}."
            return
        }
        log = "Escape failed! " + enemyStrikesBack()
    }

    private fun enemyStrikesBack(): String {
        if (Random.nextFloat() < MISS_CHANCE) {
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
