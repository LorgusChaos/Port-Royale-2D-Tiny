package com.portroyale.tiny.economy

import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.REPUTATION_MAX
import com.portroyale.tiny.factions.REPUTATION_MIN
import com.portroyale.tiny.factions.ReputationTier
import com.portroyale.tiny.factions.reputationTier
import com.portroyale.tiny.ships.CANNON_DAMAGE_BONUS
import com.portroyale.tiny.ships.FOULING_SPEED_PENALTY
import com.portroyale.tiny.ships.OAR_MANEUVER_BONUS
import com.portroyale.tiny.ships.SAIL_SPEED_BONUS
import com.portroyale.tiny.ships.ShipCondition
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

// TODO balance: bumped for testing so Brigantine/Galleon are affordable without a long grind;
// revisit for real economy balance once the core loop is confirmed working.
class Player(var gold: Int = 10000) {
    var ship: ShipType = ShipTypes.SLOOP
        private set

    val condition = ShipCondition(ship.maxHull)

    val cargo = linkedMapOf<Good, Int>().apply {
        Good.entries.forEach { put(it, 0) }
    }

    val reputation = mutableMapOf<Faction, Int>().apply {
        Faction.entries.forEach { put(it, 0) }
    }

    val cargoCapacity: Int get() = ship.cargoCapacity
    val cargoUsed: Int get() = cargo.values.sum()

    val effectiveMaxHull: Int get() = ship.maxHull
    val effectiveMinDamage: Int get() = ship.minDamage + condition.cannonLevel * CANNON_DAMAGE_BONUS
    val effectiveMaxDamage: Int get() = ship.maxDamage + condition.cannonLevel * CANNON_DAMAGE_BONUS
    val effectiveManeuverability: Int get() = ship.baseManeuverability + condition.oarLevel * OAR_MANEUVER_BONUS
    val effectiveSpeed: Float
        get() = (ship.speed + condition.sailLevel * SAIL_SPEED_BONUS) * (1f - condition.fouling * FOULING_SPEED_PENALTY)

    fun canBuy(price: Int): Boolean = gold >= price && cargoUsed < cargoCapacity

    fun buy(good: Good, price: Int) {
        gold -= price
        cargo[good] = (cargo[good] ?: 0) + 1
    }

    fun canSell(good: Good): Boolean = (cargo[good] ?: 0) > 0

    fun sell(good: Good, price: Int) {
        gold += price
        cargo[good] = (cargo[good] ?: 0) - 1
    }

    fun canBuyShip(shipType: ShipType): Boolean = shipType != ship && gold >= shipType.price

    fun buyShip(shipType: ShipType) {
        gold -= shipType.price
        ship = shipType
        condition.resetFor(shipType.maxHull)
    }

    fun canUpgradeCannons(): Boolean = condition.cannonUpgradeCost()?.let { gold >= it } ?: false
    fun upgradeCannons() {
        condition.cannonUpgradeCost()?.let { cost -> gold -= cost; condition.cannonLevel++ }
    }

    fun canUpgradeOars(): Boolean = condition.oarUpgradeCost()?.let { gold >= it } ?: false
    fun upgradeOars() {
        condition.oarUpgradeCost()?.let { cost -> gold -= cost; condition.oarLevel++ }
    }

    fun canUpgradeSails(): Boolean = condition.sailUpgradeCost()?.let { gold >= it } ?: false
    fun upgradeSails() {
        condition.sailUpgradeCost()?.let { cost -> gold -= cost; condition.sailLevel++ }
    }

    fun canCleanHull(): Boolean = condition.fouling > 0f && gold >= condition.cleanCost()
    fun cleanHull() {
        gold -= condition.cleanCost()
        condition.fouling = 0f
    }

    fun canRepair(): Boolean = condition.currentHull < effectiveMaxHull && gold >= condition.repairCost(effectiveMaxHull)
    fun repair() {
        gold -= condition.repairCost(effectiveMaxHull)
        condition.currentHull = effectiveMaxHull
    }

    fun reputationTier(faction: Faction): ReputationTier = reputationTier(reputation[faction] ?: 0)

    fun adjustReputation(faction: Faction, delta: Int) {
        val current = reputation[faction] ?: 0
        reputation[faction] = (current + delta).coerceIn(REPUTATION_MIN, REPUTATION_MAX)
    }

    /** Resets to a fresh captain's starting state, for New Game. */
    fun reset() {
        gold = 10000
        ship = ShipTypes.SLOOP
        condition.resetFor(ship.maxHull)
        Good.entries.forEach { cargo[it] = 0 }
        Faction.entries.forEach { reputation[it] = 0 }
    }

    /** Overwrites state from a loaded save, bypassing the buy/sell gold accounting. Ship
     *  condition (hull/fouling/upgrades) and reputation are restored separately by the caller,
     *  since they come from the same save data but aren't part of this method's existing
     *  parameter shape. */
    fun loadState(gold: Int, ship: ShipType, cargo: Map<Good, Int>) {
        this.gold = gold
        this.ship = ship
        condition.resetFor(ship.maxHull)
        Good.entries.forEach { this.cargo[it] = cargo[it] ?: 0 }
        Faction.entries.forEach { reputation[it] = 0 }
    }
}
