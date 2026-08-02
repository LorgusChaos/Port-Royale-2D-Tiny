package com.portroyale.tiny.ships

const val MAX_UPGRADE_LEVEL = 3
const val CANNON_DAMAGE_BONUS = 4
const val OAR_MANEUVER_BONUS = 8
const val SAIL_SPEED_BONUS = 10f
const val FOULING_SPEED_PENALTY = 0.35f

private const val FOULING_PER_SECOND = 1f / (60f * 15f) // ~15 minutes of sailing to fully foul
private const val CANNON_BASE_COST = 900
private const val OAR_BASE_COST = 500
private const val SAIL_BASE_COST = 600
private const val CLEAN_HULL_COST = 150
private const val REPAIR_COST_PER_HP = 4

/**
 * Mutable, persistent condition layered on top of whatever ShipType the player currently owns:
 * installed upgrade levels, hull damage, and fouling from time spent at sea. Reset whenever a
 * new ShipType is bought - upgrades and wear don't transfer to a freshly purchased hull. Numbers
 * here are a rough first pass, not a tuned economy.
 */
class ShipCondition(maxHull: Int) {
    var currentHull: Int = maxHull
    var fouling: Float = 0f
    var cannonLevel: Int = 0
    var oarLevel: Int = 0
    var sailLevel: Int = 0

    fun resetFor(maxHull: Int) {
        currentHull = maxHull
        fouling = 0f
        cannonLevel = 0
        oarLevel = 0
        sailLevel = 0
    }

    fun accumulateFouling(deltaSeconds: Float) {
        fouling = (fouling + deltaSeconds * FOULING_PER_SECOND).coerceIn(0f, 1f)
    }

    fun cannonUpgradeCost(): Int? = nextCost(cannonLevel, CANNON_BASE_COST)
    fun oarUpgradeCost(): Int? = nextCost(oarLevel, OAR_BASE_COST)
    fun sailUpgradeCost(): Int? = nextCost(sailLevel, SAIL_BASE_COST)

    private fun nextCost(level: Int, base: Int): Int? =
        if (level >= MAX_UPGRADE_LEVEL) null else base * (level + 1)

    fun cleanCost(): Int = CLEAN_HULL_COST
    fun repairCost(maxHull: Int): Int = (maxHull - currentHull) * REPAIR_COST_PER_HP
}
