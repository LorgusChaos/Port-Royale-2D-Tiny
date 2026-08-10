package com.portroyale.tiny.economy

import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.GOLD_PER_REPUTATION_POINT
import com.portroyale.tiny.factions.REPUTATION_MAX
import com.portroyale.tiny.factions.REPUTATION_MIN
import com.portroyale.tiny.factions.RIVAL_PENALTY_RATIO
import com.portroyale.tiny.factions.ReputationTier
import com.portroyale.tiny.factions.reputationTier
import com.portroyale.tiny.ships.ShipCondition
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.ships.effectiveMaxDamage
import com.portroyale.tiny.ships.effectiveManeuverability
import com.portroyale.tiny.ships.effectiveMinDamage
import com.portroyale.tiny.ships.effectiveSpeed

// TODO balance: bumped for testing so Brigantine/Galleon are affordable without a long grind;
// revisit for real economy balance once the core loop is confirmed working.
class Player(var gold: Int = 10000) {
    var ship: ShipType = ShipTypes.SLOOP
        private set

    /** Every hull the player owns, including the one currently being sailed (see [activeShipId]
     *  and OwnedShip) - a fleet roster, distinct from [ship] which stays a plain ShipType/
     *  ShipCondition pair so the many existing call sites reading player.ship didn't need to
     *  change when fleet ownership was added. */
    val ownedShips = mutableListOf(OwnedShip(1, "Sloop", ShipTypes.SLOOP, ""))
    var activeShipId: Int = 1
        private set

    /** Stands in for [condition] only while [activeShipId] points at no real hull - i.e. after
     *  [wreckShip], which leaves the player with no OwnedShip to carry it. */
    private val wreckCondition = ShipCondition(ShipTypes.WRECK.maxHull)

    /** Wear and upgrades of the hull being sailed right now - deliberately a *view* onto the
     *  active OwnedShip's own ShipCondition, not a second object holding the same kind of data.
     *  While the two were separate, upgrades bought here never reached the hull (so a captain
     *  later given that ship sailed it unupgraded), and boarding reset them to zero while also
     *  silently handing out a free full repair and hull scrub. One condition per hull means the
     *  two can no longer disagree - the invariant OwnedShip's own docs already claimed. */
    val condition: ShipCondition
        get() = ownedShips.find { it.id == activeShipId }?.condition ?: wreckCondition

    val cargo = linkedMapOf<Good, Int>().apply {
        Good.entries.forEach { put(it, 0) }
    }

    val reputation = mutableMapOf<Faction, Int>().apply {
        Faction.entries.forEach { put(it, 0) }
    }

    /** Sub-point reputation carried between sales - see [recordSale]. Always in (-1, 1); kept
     *  apart from [reputation] so the visible number stays a plain Int for the UI and for every
     *  save written so far. */
    val reputationRemainder = mutableMapOf<Faction, Float>()

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
    val effectiveMinDamage: Int get() = effectiveMinDamage(ship, condition)
    val effectiveMaxDamage: Int get() = effectiveMaxDamage(ship, condition)
    val effectiveManeuverability: Int get() = effectiveManeuverability(ship, condition)
    val effectiveSpeed: Float get() = effectiveSpeed(ship, condition)

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
     *  it docks here instead (see FleetDockUI for renaming/re-crewing it later). The new hull
     *  arrives pristine because it is a brand new OwnedShip carrying its own fresh condition,
     *  not because anything is reset here. */
    fun buyShip(shipType: ShipType, name: String, currentPort: String) {
        gold -= shipType.price
        ownedShips.find { it.id == activeShipId }?.dockedAt = currentPort
        val newShip = OwnedShip(nextShipId(), name.ifBlank { shipType.name }, shipType, currentPort)
        ownedShips.add(newShip)
        activeShipId = newShip.id
        ship = shipType
    }

    /** Whether the hold would still fit if the player moved onto this class of ship - see
     *  [canBoardShip]. Exposed so the dock can say WHY a swap is refused. */
    fun cargoFitsOn(shipType: ShipType): Boolean = cargoUsed <= shipType.cargoCapacity

    /** Swaps which owned ship the player is actively sailing - only valid for a ship idle and
     *  docked at [currentPort] (see FleetDockUI), and only when the hold actually fits: cargo
     *  lives on the player, not the hull, so boarding a smaller ship with a full hold would
     *  otherwise leave more cargo aboard than the ship can carry. Refusing is deliberate -
     *  quietly throwing the excess overboard would destroy goods the player paid for. */
    fun canBoardShip(shipId: Int, currentPort: String): Boolean {
        if (shipId == activeShipId) return false
        val target = ownedShips.find { it.id == shipId } ?: return false
        if (target.dockedAt != currentPort) return false
        return cargoFitsOn(target.shipType)
    }

    /** Boards [shipId], carrying that hull's OWN wear and upgrades with it - see [condition].
     *  Nothing is reset: a battered ship is still battered, an upgraded one still upgraded. */
    fun boardShip(shipId: Int, currentPort: String) {
        val target = ownedShips.find { it.id == shipId } ?: return
        ownedShips.find { it.id == activeShipId }?.dockedAt = currentPort
        activeShipId = target.id
        ship = target.shipType
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
        wreckCondition.resetFor(ship.maxHull)
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

    fun canStoreGold(portName: String, amount: Int): Boolean =
        warehouses[portName] != null && gold >= amount
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

    /**
     * Books a completed sale of [revenue] gold in a port owned by [sellerFaction]: that faction
     * gains standing, the other three lose a [RIVAL_PENALTY_RATIO]-times smaller amount. This is
     * the whole of M14's faction conflict - selling to one power is, by itself, trading against
     * the others, so no record of where a good was *bought* is needed anywhere.
     *
     * Reputation is driven by gold, not by units, so a cargo of silver counts for more than the
     * same hold full of wood. Buying moves nothing: only a sale takes a side.
     *
     * Called by the market screen for the player's own sales and by FleetSimulation for a hired
     * captain's, so delegating trade cannot be used to dodge its consequences.
     */
    fun recordSale(sellerFaction: Faction?, revenue: Int) {
        if (revenue <= 0) return
        val gain = revenue / GOLD_PER_REPUTATION_POINT
        val penalty = gain / RIVAL_PENALTY_RATIO
        for (faction in Faction.entries) {
            applyReputationDelta(faction, if (faction == sellerFaction) gain else -penalty)
        }
    }

    /** Applies a fractional reputation change, carrying the leftover to the next sale. Without
     *  this a 500g sale would round to zero and small hand-made trades would count for nothing
     *  at all, while a captain's bulk sale counted fully. */
    private fun applyReputationDelta(faction: Faction, delta: Float) {
        val carried = (reputationRemainder[faction] ?: 0f) + delta
        val whole = carried.toInt() // truncates toward zero, so the remainder keeps delta's sign
        reputationRemainder[faction] = carried - whole
        if (whole != 0) adjustReputation(faction, whole)
    }

    /** Resets to a fresh captain's starting state, for New Game. */
    fun reset() {
        gold = 10000
        ship = ShipTypes.SLOOP
        Good.entries.forEach { cargo[it] = 0 }
        Faction.entries.forEach { reputation[it] = 0 }
        reputationRemainder.clear()
        warehouses.clear()
        loan = null
        captains.clear()
        ownedShips.clear()
        // The replacement Sloop brings its own pristine condition, which [condition] then
        // resolves to - there is nothing left over from the old hull to reset.
        ownedShips.add(OwnedShip(1, "Sloop", ShipTypes.SLOOP, ""))
        activeShipId = 1
        wreckCondition.resetFor(ShipTypes.WRECK.maxHull)
    }

    /** Overwrites state from a loaded save, bypassing the buy/sell gold accounting. Ship
     *  condition (hull/fouling/upgrades) and reputation are restored separately by the caller,
     *  since they come from the same save data but aren't part of this method's existing
     *  parameter shape. Condition specifically must be restored AFTER [loadFleet] - it now
     *  lives on the active OwnedShip (see [condition]), which does not exist until the roster
     *  is in place. */
    fun loadState(gold: Int, ship: ShipType, cargo: Map<Good, Int>) {
        this.gold = gold
        this.ship = ship
        Good.entries.forEach { this.cargo[it] = cargo[it] ?: 0 }
        Faction.entries.forEach { reputation[it] = 0 }
        reputationRemainder.clear()
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
