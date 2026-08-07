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

    /** Every hull the player owns, including the one currently being sailed (see [activeShipId]
     *  and OwnedShip) - a fleet roster, distinct from [ship] which stays a plain ShipType/
     *  ShipCondition pair so the many existing call sites reading player.ship didn't need to
     *  change when fleet ownership was added. */
    val ownedShips = mutableListOf(OwnedShip(1, "Sloop", ShipTypes.SLOOP, ""))
    var activeShipId: Int = 1
        private set

    val cargo = linkedMapOf<Good, Int>().apply {
        Good.entries.forEach { put(it, 0) }
    }

    val reputation = mutableMapOf<Faction, Int>().apply {
        Faction.entries.forEach { put(it, 0) }
    }

    /** Only ports the player has actually bought a warehouse in get an entry - see
     *  [warehouseAt]/[buyWarehouse]. */
    val warehouses = mutableMapOf<String, Warehouse>()

    /** At most one outstanding loan at a time - see economy/Loan.kt and FortressUI. */
    var loan: Loan? = null

    /** Hired captains running their own A<->B trade routes - see economy/Captain.kt,
     *  FleetSimulation, and TavernUI. */
    val captains = mutableListOf<Captain>()

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

    fun canBuyShip(shipType: ShipType): Boolean = gold >= shipType.price

    private fun nextShipId(): Int = (ownedShips.maxOfOrNull { it.id } ?: 0) + 1

    /** Buys a new hull and boards it immediately - the ship being left behind is NOT discarded,
     *  it docks here instead (see FleetDockUI for renaming/re-crewing it later). */
    fun buyShip(shipType: ShipType, name: String, currentPort: String) {
        gold -= shipType.price
        ownedShips.find { it.id == activeShipId }?.dockedAt = currentPort
        val newShip = OwnedShip(nextShipId(), name.ifBlank { shipType.name }, shipType, currentPort)
        ownedShips.add(newShip)
        activeShipId = newShip.id
        ship = shipType
        condition.resetFor(shipType.maxHull)
    }

    /** Swaps which owned ship the player is actively sailing - only valid for a ship idle and
     *  docked at [currentPort] (see FleetDockUI). The ship being left behind docks here in turn. */
    fun canBoardShip(shipId: Int, currentPort: String): Boolean {
        if (shipId == activeShipId) return false
        val target = ownedShips.find { it.id == shipId } ?: return false
        return target.dockedAt == currentPort
    }
    fun boardShip(shipId: Int, currentPort: String) {
        val target = ownedShips.find { it.id == shipId } ?: return
        ownedShips.find { it.id == activeShipId }?.dockedAt = currentPort
        activeShipId = target.id
        ship = target.shipType
        condition.resetFor(target.shipType.maxHull)
    }

    fun canRenameShip(shipId: Int): Boolean = ownedShips.any { it.id == shipId }
    fun renameShip(shipId: Int, name: String) {
        if (name.isBlank()) return
        ownedShips.find { it.id == shipId }?.name = name
    }

    /** Wrecks the player's ship after a lost battle - no purchase, no choice, just gone (removed
     *  from the fleet outright, not merely damaged). Any OTHER owned ships sitting in other ports
     *  are untouched - the player just has to reach one, or buy a new one. See ShipTypes.WRECK and
     *  PortRoyaleGame.finishCombat's DEFEAT handling. */
    fun wreckShip() {
        gold = 0
        Good.entries.forEach { cargo[it] = 0 }
        ownedShips.removeAll { it.id == activeShipId }
        activeShipId = -1
        ship = ShipTypes.WRECK
        condition.resetFor(ship.maxHull)
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

    fun warehouseAt(portName: String): Warehouse? = warehouses[portName]

    fun canBuyWarehouse(portName: String): Boolean = warehouses[portName] == null && gold >= WAREHOUSE_PURCHASE_PRICE
    fun buyWarehouse(portName: String) {
        gold -= WAREHOUSE_PURCHASE_PRICE
        warehouses[portName] = Warehouse()
    }

    fun canExpandWarehouse(portName: String): Boolean {
        val cost = warehouses[portName]?.expansionCost() ?: return false
        return gold >= cost
    }
    fun expandWarehouse(portName: String) {
        val warehouse = warehouses[portName] ?: return
        val cost = warehouse.expansionCost() ?: return
        gold -= cost
        warehouse.level++
    }

    fun canStoreGood(portName: String, good: Good): Boolean {
        val warehouse = warehouses[portName] ?: return false
        return (cargo[good] ?: 0) > 0 && warehouse.goodsUsed < warehouse.capacity
    }
    fun storeGood(portName: String, good: Good) {
        val warehouse = warehouses[portName] ?: return
        cargo[good] = (cargo[good] ?: 0) - 1
        warehouse.goods[good] = (warehouse.goods[good] ?: 0) + 1
    }

    fun canWithdrawGood(portName: String, good: Good): Boolean {
        val warehouse = warehouses[portName] ?: return false
        return (warehouse.goods[good] ?: 0) > 0 && cargoUsed < cargoCapacity
    }
    fun withdrawGood(portName: String, good: Good) {
        val warehouse = warehouses[portName] ?: return
        warehouse.goods[good] = (warehouse.goods[good] ?: 0) - 1
        cargo[good] = (cargo[good] ?: 0) + 1
    }

    fun canStoreGold(portName: String, amount: Int): Boolean = gold >= amount
    fun storeGold(portName: String, amount: Int) {
        val warehouse = warehouses[portName] ?: return
        gold -= amount
        warehouse.gold += amount
    }

    fun canWithdrawGold(portName: String, amount: Int): Boolean = (warehouses[portName]?.gold ?: 0) >= amount
    fun withdrawGold(portName: String, amount: Int) {
        val warehouse = warehouses[portName] ?: return
        warehouse.gold -= amount
        gold += amount
    }

    fun canTakeLoan(): Boolean = loan == null
    fun takeLoan(offer: LoanOffer, faction: Faction, currentGameMinutes: Float) {
        gold += offer.principal
        loan = Loan(
            faction = faction,
            principal = offer.principal,
            repayAmount = offer.repayAmount,
            takenAtGameMinutes = currentGameMinutes,
            deadlineGameMinutes = currentGameMinutes + offer.termGameMinutes
        )
    }

    fun canRepayLoan(): Boolean {
        val current = loan ?: return false
        return gold >= current.repayAmount
    }
    fun repayLoan() {
        val current = loan ?: return
        gold -= current.repayAmount
        loan = null
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
        warehouses.clear()
        loan = null
        captains.clear()
        ownedShips.clear()
        ownedShips.add(OwnedShip(1, "Sloop", ShipTypes.SLOOP, ""))
        activeShipId = 1
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

    /** Restores the fleet roster from a save - see SaveManager.resolveOwnedShips. Falls back to
     *  a single Sloop matching [ship] if the save predates fleet ownership (or somehow ends up
     *  with no ship at all), so an old save can't leave the player with no hull to stand on. */
    fun loadFleet(ownedShips: List<OwnedShip>, activeShipId: Int) {
        this.ownedShips.clear()
        if (ownedShips.isEmpty()) {
            this.ownedShips.add(OwnedShip(1, ship.name, ship, ""))
            this.activeShipId = 1
        } else {
            this.ownedShips.addAll(ownedShips)
            this.activeShipId = activeShipId
        }
    }
}
