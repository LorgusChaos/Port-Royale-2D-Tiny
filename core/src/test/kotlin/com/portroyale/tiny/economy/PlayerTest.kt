package com.portroyale.tiny.economy

import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.REPUTATION_MAX
import com.portroyale.tiny.factions.REPUTATION_MIN
import com.portroyale.tiny.factions.ReputationTier
import com.portroyale.tiny.ships.MAX_UPGRADE_LEVEL
import com.portroyale.tiny.ships.ShipTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerTest {

    private fun freshPlayer() = Player()

    // --- starting state ---

    @Test
    fun `a new captain starts in a sloop with an empty hold`() {
        val player = freshPlayer()
        assertEquals(10000, player.gold)
        assertSame(ShipTypes.SLOOP, player.ship)
        assertEquals(0, player.cargoUsed)
        assertEquals(ShipTypes.SLOOP.cargoCapacity, player.cargoCapacity)
        assertEquals(ShipTypes.SLOOP.maxHull, player.condition.currentHull)
    }

    @Test
    fun `a new captain owns exactly the ship they are sailing`() {
        val player = freshPlayer()
        assertEquals(1, player.ownedShips.size)
        assertEquals(player.activeShipId, player.ownedShips.single().id)
    }

    @Test
    fun `a new captain is neutral with every faction`() {
        val player = freshPlayer()
        for (faction in Faction.entries) {
            assertEquals(ReputationTier.NEUTRAL, player.reputationTier(faction))
        }
    }

    // --- trading ---

    @Test
    fun `buying moves gold into cargo`() {
        val player = freshPlayer()
        player.buy(Good.SUGAR, 120)
        assertEquals(9880, player.gold)
        assertEquals(1, player.cargo[Good.SUGAR])
        assertEquals(1, player.cargoUsed)
    }

    @Test
    fun `selling moves cargo back into gold`() {
        val player = freshPlayer()
        player.buy(Good.SUGAR, 120)
        player.sell(Good.SUGAR, 150)
        assertEquals(10030, player.gold)
        assertEquals(0, player.cargo[Good.SUGAR])
    }

    @Test
    fun `a captain cannot buy what they cannot pay for`() {
        val player = Player(gold = 50)
        assertFalse(player.canBuy(51))
        assertTrue(player.canBuy(50))
    }

    @Test
    fun `a captain cannot buy with a full hold`() {
        val player = freshPlayer()
        repeat(ShipTypes.SLOOP.cargoCapacity) { player.buy(Good.WOOD, 1) }
        assertEquals(player.cargoCapacity, player.cargoUsed)
        assertFalse(player.canBuy(1))
    }

    @Test
    fun `a captain cannot sell what they are not carrying`() {
        val player = freshPlayer()
        assertFalse(player.canSell(Good.SUGAR))
        player.buy(Good.SUGAR, 10)
        assertTrue(player.canSell(Good.SUGAR))
    }

    // --- buying and swapping hulls ---

    @Test
    fun `buying a ship pays for it and boards it immediately`() {
        val player = freshPlayer()
        player.buyShip(ShipTypes.GALLEON, "Santa Maria", "Havana")
        assertEquals(10000 - ShipTypes.GALLEON.price, player.gold)
        assertSame(ShipTypes.GALLEON, player.ship)
        assertEquals(ShipTypes.GALLEON.maxHull, player.condition.currentHull)
        assertEquals("Santa Maria", player.ownedShips.first { it.id == player.activeShipId }.name)
    }

    @Test
    fun `the ship left behind docks at the port rather than being discarded`() {
        val player = freshPlayer()
        val oldId = player.activeShipId
        player.buyShip(ShipTypes.CUTTER, "Swift", "Nassau")
        assertEquals(2, player.ownedShips.size)
        assertEquals("Nassau", player.ownedShips.first { it.id == oldId }.dockedAt)
    }

    @Test
    fun `an unnamed ship falls back to its class name`() {
        val player = freshPlayer()
        player.buyShip(ShipTypes.BARQUE, "   ", "Havana")
        assertEquals("Barque", player.ownedShips.first { it.id == player.activeShipId }.name)
    }

    @Test
    fun `a fresh hull comes with no upgrades and no wear`() {
        val player = freshPlayer()
        player.upgradeCannons()
        player.condition.accumulateFouling(600f)
        player.buyShip(ShipTypes.CUTTER, "Swift", "Havana")
        assertEquals(0, player.condition.cannonLevel)
        assertEquals(0f, player.condition.fouling)
        assertEquals(ShipTypes.CUTTER.maxHull, player.condition.currentHull)
    }

    @Test
    fun `boarding is only possible for another ship docked right here`() {
        val player = freshPlayer()
        val oldId = player.activeShipId
        player.buyShip(ShipTypes.CUTTER, "Swift", "Nassau")

        assertFalse(player.canBoardShip(player.activeShipId, "Nassau"), "cannot board the ship already sailed")
        assertFalse(player.canBoardShip(oldId, "Havana"), "that ship is docked in Nassau, not Havana")
        assertFalse(player.canBoardShip(999, "Nassau"), "no such ship")
        assertTrue(player.canBoardShip(oldId, "Nassau"))
    }

    @Test
    fun `boarding swaps which hull is sailed and docks the other`() {
        val player = freshPlayer()
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.CUTTER, "Swift", "Nassau")
        val cutterId = player.activeShipId

        player.boardShip(sloopId, "Nassau")

        assertEquals(sloopId, player.activeShipId)
        assertSame(ShipTypes.SLOOP, player.ship)
        assertEquals("Nassau", player.ownedShips.first { it.id == cutterId }.dockedAt)
    }

    // --- the hull carries its own condition (regression: upgrades used to vanish on a swap) ---

    @Test
    fun `an upgrade bought on deck reaches the hull itself`() {
        // Upgrades used to land on a Player-level condition the hull never saw, so a captain
        // later given this ship sailed it as if it were stock.
        val player = freshPlayer()
        player.upgradeSails()
        player.upgradeCannons()

        val hull = player.ownedShips.first { it.id == player.activeShipId }
        assertEquals(1, hull.condition.sailLevel)
        assertEquals(1, hull.condition.cannonLevel)
        assertEquals(player.effectiveSpeed, hull.effectiveSpeed, 0.0001f)
        assertEquals(player.effectiveMaxDamage, hull.effectiveMaxDamage)
    }

    @Test
    fun `upgrades survive sailing another ship and coming back`() {
        val player = Player(gold = 100_000)
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana")
        val galleonId = player.activeShipId
        player.upgradeCannons(); player.upgradeOars(); player.upgradeSails()

        player.boardShip(sloopId, "Havana")
        player.boardShip(galleonId, "Havana")

        assertEquals(1, player.condition.cannonLevel, "paid-for upgrades must not evaporate")
        assertEquals(1, player.condition.oarLevel)
        assertEquals(1, player.condition.sailLevel)
    }

    @Test
    fun `each hull keeps its own upgrades`() {
        val player = Player(gold = 100_000)
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana")
        player.upgradeCannons()

        player.boardShip(sloopId, "Havana")

        assertEquals(0, player.condition.cannonLevel, "the sloop was never upgraded")
    }

    @Test
    fun `swapping ships is not a free repair`() {
        // Boarding used to reset the hull to pristine, so a round trip through another ship
        // repaired and scrubbed it for nothing - undercutting repairCost and cleanCost entirely.
        val player = Player(gold = 100_000)
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana")
        val galleonId = player.activeShipId
        player.condition.currentHull = 40
        player.condition.fouling = 0.8f

        player.boardShip(sloopId, "Havana")
        player.boardShip(galleonId, "Havana")

        assertEquals(40, player.condition.currentHull, "damage must still be there")
        assertEquals(0.8f, player.condition.fouling, "fouling must still be there")
        assertTrue(player.canRepair(), "and it must still cost gold to put right")
    }

    @Test
    fun `a newly bought hull is pristine`() {
        val player = Player(gold = 100_000)
        player.condition.currentHull = 5
        player.condition.fouling = 1f
        player.upgradeCannons()

        player.buyShip(ShipTypes.BARQUE, "Fresh", "Havana")

        assertEquals(ShipTypes.BARQUE.maxHull, player.condition.currentHull)
        assertEquals(0f, player.condition.fouling)
        assertEquals(0, player.condition.cannonLevel)
    }

    @Test
    fun `a wrecked captain still has a condition to read`() {
        // activeShipId points at nothing after a wreck, so condition falls back rather than
        // crashing whatever HUD or combat screen asks for the hull.
        val player = freshPlayer()
        player.castAdrift()
        assertEquals(ShipTypes.LONGBOAT.maxHull, player.condition.currentHull)
        assertEquals(0, player.condition.cannonLevel)
    }

    // --- boarding with a hold that will not fit ---

    @Test
    fun `boarding a smaller ship is refused while the hold is too full`() {
        val player = Player(gold = 100_000)
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana")
        repeat(ShipTypes.SLOOP.cargoCapacity + 1) { player.buy(Good.WOOD, 1) }

        assertFalse(player.cargoFitsOn(ShipTypes.SLOOP))
        assertFalse(player.canBoardShip(sloopId, "Havana"), "the cargo would not fit")
    }

    @Test
    fun `boarding a smaller ship is allowed once the hold fits`() {
        val player = Player(gold = 100_000)
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana")
        repeat(ShipTypes.SLOOP.cargoCapacity) { player.buy(Good.WOOD, 1) }

        assertTrue(player.cargoFitsOn(ShipTypes.SLOOP), "exactly full still fits")
        assertTrue(player.canBoardShip(sloopId, "Havana"))

        player.boardShip(sloopId, "Havana")
        assertTrue(player.cargoUsed <= player.cargoCapacity, "the hold must never exceed the hull")
    }

    @Test
    fun `moving up to a bigger ship is never blocked by cargo`() {
        val player = Player(gold = 100_000)
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana")
        val galleonId = player.activeShipId
        val sloopId = player.ownedShips.first { it.id != galleonId }.id
        player.boardShip(sloopId, "Havana")
        repeat(ShipTypes.SLOOP.cargoCapacity) { player.buy(Good.WOOD, 1) }

        assertTrue(player.canBoardShip(galleonId, "Havana"))
    }

    @Test
    fun `renaming a ship ignores a blank name`() {
        val player = freshPlayer()
        val id = player.activeShipId
        player.renameShip(id, "Revenge")
        assertEquals("Revenge", player.ownedShips.first { it.id == id }.name)
        player.renameShip(id, "  ")
        assertEquals("Revenge", player.ownedShips.first { it.id == id }.name)
    }

    @Test
    fun `every new hull gets its own id`() {
        val player = freshPlayer()
        player.buyShip(ShipTypes.CUTTER, "A", "Havana")
        player.buyShip(ShipTypes.BARQUE, "B", "Havana")
        assertEquals(3, player.ownedShips.map { it.id }.distinct().size)
    }

    // --- losing a ship ---

    @Test
    fun `being cast adrift costs the captain their gold, cargo and hull`() {
        val player = freshPlayer()
        player.buy(Good.SUGAR, 100)
        player.castAdrift()

        assertEquals(0, player.gold)
        assertEquals(0, player.cargoUsed)
        assertSame(ShipTypes.LONGBOAT, player.ship)
        assertTrue(player.ownedShips.isEmpty())
    }

    @Test
    fun `being cast adrift leaves ships docked in other ports untouched`() {
        val player = freshPlayer()
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana") // sloop now docked in Havana
        player.castAdrift()

        assertEquals(listOf(sloopId), player.ownedShips.map { it.id })
        assertEquals("Havana", player.ownedShips.single().dockedAt)
    }

    // --- upgrades and upkeep ---

    @Test
    fun `each upgrade level costs more than the last and stops at the cap`() {
        val player = freshPlayer()
        var spent = 0
        repeat(MAX_UPGRADE_LEVEL) {
            val cost = player.condition.cannonUpgradeCost()
            assertNotNull(cost)
            assertTrue(player.canUpgradeCannons())
            player.upgradeCannons()
            spent += cost
        }
        assertEquals(MAX_UPGRADE_LEVEL, player.condition.cannonLevel)
        assertEquals(10000 - spent, player.gold)
        assertNull(player.condition.cannonUpgradeCost())
        assertFalse(player.canUpgradeCannons(), "cannons are maxed out")
    }

    @Test
    fun `oar and sail upgrades cap the same way`() {
        val player = freshPlayer()
        repeat(MAX_UPGRADE_LEVEL) { player.upgradeOars(); player.upgradeSails() }
        assertFalse(player.canUpgradeOars())
        assertFalse(player.canUpgradeSails())
        assertEquals(MAX_UPGRADE_LEVEL, player.condition.oarLevel)
        assertEquals(MAX_UPGRADE_LEVEL, player.condition.sailLevel)
    }

    @Test
    fun `a broke captain cannot upgrade anything`() {
        val player = Player(gold = 0)
        assertFalse(player.canUpgradeCannons())
        assertFalse(player.canUpgradeOars())
        assertFalse(player.canUpgradeSails())
    }

    @Test
    fun `upgrades feed straight into combat and sailing stats`() {
        val player = freshPlayer()
        val baseDamage = player.effectiveMaxDamage
        val baseSpeed = player.effectiveSpeed
        val baseManeuver = player.effectiveManeuverability

        player.upgradeCannons()
        player.upgradeSails()
        player.upgradeOars()

        assertTrue(player.effectiveMaxDamage > baseDamage)
        assertTrue(player.effectiveSpeed > baseSpeed)
        assertTrue(player.effectiveManeuverability > baseManeuver)
    }

    @Test
    fun `cleaning the hull is only offered when the hull is actually fouled`() {
        val player = freshPlayer()
        assertFalse(player.canCleanHull(), "a clean hull needs no scrub")

        player.condition.accumulateFouling(300f)
        assertTrue(player.canCleanHull())

        val before = player.gold
        player.cleanHull()
        assertEquals(0f, player.condition.fouling)
        assertTrue(player.gold < before)
    }

    @Test
    fun `repairs are only offered on a damaged hull and restore it fully`() {
        val player = freshPlayer()
        assertFalse(player.canRepair(), "an undamaged hull needs no repair")

        player.condition.currentHull -= 40
        assertTrue(player.canRepair())

        val before = player.gold
        player.repair()
        assertEquals(player.effectiveMaxHull, player.condition.currentHull)
        assertEquals(before - 40 * 4, player.gold)
    }

    @Test
    fun `a broke captain cannot afford a repair`() {
        val player = Player(gold = 10)
        player.condition.currentHull -= 50
        assertFalse(player.canRepair())
    }

    // --- warehouses ---

    @Test
    fun `a warehouse is bought once per port`() {
        val player = freshPlayer()
        assertNull(player.warehouseAt("Havana"))
        assertTrue(player.canBuyWarehouse("Havana"))

        player.buyWarehouse("Havana")

        assertEquals(10000 - WAREHOUSE_PURCHASE_PRICE, player.gold)
        assertNotNull(player.warehouseAt("Havana"))
        assertFalse(player.canBuyWarehouse("Havana"), "already owns one here")
        assertTrue(player.canBuyWarehouse("Nassau"), "a different port is a separate purchase")
    }

    @Test
    fun `a broke captain cannot buy a warehouse`() {
        assertFalse(Player(gold = WAREHOUSE_PURCHASE_PRICE - 1).canBuyWarehouse("Havana"))
    }

    @Test
    fun `expanding a warehouse raises its capacity up to the cap`() {
        val player = freshPlayer()
        player.buyWarehouse("Havana")
        val warehouse = player.warehouseAt("Havana")!!
        assertEquals(WAREHOUSE_BASE_CAPACITY, warehouse.capacity)

        repeat(WAREHOUSE_MAX_LEVEL - 1) {
            assertTrue(player.canExpandWarehouse("Havana"))
            player.expandWarehouse("Havana")
        }

        assertEquals(WAREHOUSE_MAX_LEVEL, warehouse.level)
        assertEquals(
            WAREHOUSE_BASE_CAPACITY + (WAREHOUSE_MAX_LEVEL - 1) * WAREHOUSE_EXPANSION_CAPACITY,
            warehouse.capacity
        )
        assertFalse(player.canExpandWarehouse("Havana"), "already at max level")
    }

    @Test
    fun `expanding a warehouse in a port without one does nothing`() {
        val player = freshPlayer()
        assertFalse(player.canExpandWarehouse("Havana"))
        player.expandWarehouse("Havana")
        assertEquals(10000, player.gold)
    }

    @Test
    fun `storing a good moves it out of the hold and back again`() {
        val player = freshPlayer()
        player.buyWarehouse("Havana")
        player.buy(Good.SUGAR, 100)

        assertTrue(player.canStoreGood("Havana", Good.SUGAR))
        player.storeGood("Havana", Good.SUGAR)
        assertEquals(0, player.cargo[Good.SUGAR])
        assertEquals(1, player.warehouseAt("Havana")!!.goods[Good.SUGAR])

        assertTrue(player.canWithdrawGood("Havana", Good.SUGAR))
        player.withdrawGood("Havana", Good.SUGAR)
        assertEquals(1, player.cargo[Good.SUGAR])
        assertEquals(0, player.warehouseAt("Havana")!!.goods[Good.SUGAR])
    }

    @Test
    fun `storing needs both a warehouse here and the good in the hold`() {
        val player = freshPlayer()
        player.buy(Good.SUGAR, 100)
        assertFalse(player.canStoreGood("Havana", Good.SUGAR), "no warehouse in Havana")

        player.buyWarehouse("Havana")
        assertFalse(player.canStoreGood("Havana", Good.RUM), "carrying no rum")
    }

    @Test
    fun `a full warehouse refuses more goods`() {
        val player = Player(gold = 1_000_000)
        player.buyWarehouse("Havana")
        val warehouse = player.warehouseAt("Havana")!!
        warehouse.goods[Good.WOOD] = warehouse.capacity

        player.buy(Good.SUGAR, 1)
        assertFalse(player.canStoreGood("Havana", Good.SUGAR))
    }

    @Test
    fun `withdrawing into a full hold is refused`() {
        val player = freshPlayer()
        player.buyWarehouse("Havana")
        player.warehouseAt("Havana")!!.goods[Good.SUGAR] = 5
        repeat(player.cargoCapacity) { player.buy(Good.WOOD, 1) }
        assertFalse(player.canWithdrawGood("Havana", Good.SUGAR))
    }

    @Test
    fun `gold left in a warehouse leaves the purse and comes back`() {
        val player = freshPlayer()
        player.buyWarehouse("Havana")

        assertTrue(player.canStoreGold("Havana", 500))
        player.storeGold("Havana", 500)
        assertEquals(10000 - WAREHOUSE_PURCHASE_PRICE - 500, player.gold)
        assertEquals(500, player.warehouseAt("Havana")!!.gold)

        assertFalse(player.canWithdrawGold("Havana", 501), "only 500 is stored here")
        assertTrue(player.canWithdrawGold("Havana", 500))
        player.withdrawGold("Havana", 500)
        assertEquals(10000 - WAREHOUSE_PURCHASE_PRICE, player.gold)
    }

    @Test
    fun `a captain cannot store gold they do not have`() {
        val player = Player(gold = 100)
        player.warehouses["Havana"] = Warehouse()
        assertFalse(player.canStoreGold("Havana", 101))
    }

    @Test
    fun `gold cannot be stored in a port with no warehouse`() {
        // storeGold silently no-ops without a warehouse, so canStoreGold agreeing that it is
        // possible would leave the UI offering an action that does nothing.
        val player = freshPlayer()
        assertFalse(player.canStoreGold("Havana", 100), "there is no warehouse in Havana")

        player.buyWarehouse("Havana")
        assertTrue(player.canStoreGold("Havana", 100))
        assertFalse(player.canStoreGold("Nassau", 100), "still nothing in Nassau")
    }

    @Test
    fun `the ship's boat has no hold, so nothing can be carried or traded from it`() {
        // The whole point of the boat: it carries a man, not a cargo. Being unable to trade is
        // what forces a hull to be bought before trading can resume, and it is why the stored
        // gold and the fleet below are the way back rather than a convenience.
        val player = freshPlayer()
        player.castAdrift()
        player.gold = 10_000

        assertEquals(0, player.cargoCapacity)
        assertFalse(player.canBuy(1), "a boat with no hold cannot take on cargo at any price")

        player.buyWarehouse("Havana")
        // Stocked directly rather than through storeGood, which moves a unit out of the hold -
        // and the hold is exactly what this player does not have.
        player.warehouseAt("Havana")!!.goods[Good.SUGAR] = 1
        assertFalse(
            player.canWithdrawGood("Havana", Good.SUGAR),
            "there is nowhere aboard to put it"
        )
    }

    @Test
    fun `a bought hull is the way out of the boat`() {
        // The bottom of a game has to have a way up from it, or it is simply a lost save.
        val player = freshPlayer()
        player.castAdrift()
        player.gold = ShipTypes.SLOOP.price

        player.buyShip(ShipTypes.SLOOP, "Second Chance", "Havana")
        assertSame(ShipTypes.SLOOP, player.ship)
        assertEquals(ShipTypes.SLOOP.cargoCapacity, player.cargoCapacity)
    }

    @Test
    fun `warehouse gold survives losing the ship`() {
        // The stated point of a warehouse: a hedge against being cleaned out by pirates.
        val player = freshPlayer()
        player.buyWarehouse("Havana")
        player.storeGold("Havana", 1000)
        player.castAdrift()
        assertEquals(0, player.gold)
        assertEquals(1000, player.warehouseAt("Havana")!!.gold)
    }

    // --- loans ---

    @Test
    fun `taking a loan pays out now and books a deadline`() {
        val player = freshPlayer()
        val offer = LOAN_OFFERS.first()
        assertTrue(player.canTakeLoan())

        player.takeLoan(offer, Faction.SPAIN, currentGameMinutes = 500f)

        assertEquals(10000 + offer.principal, player.gold)
        val loan = player.loan
        assertNotNull(loan)
        assertEquals(Faction.SPAIN, loan.faction)
        assertEquals(offer.repayAmount, loan.repayAmount)
        assertEquals(500f, loan.takenAtGameMinutes)
        assertEquals(500f + offer.termGameMinutes, loan.deadlineGameMinutes)
        assertFalse(loan.overdue)
    }

    @Test
    fun `only one loan can be outstanding at a time`() {
        val player = freshPlayer()
        player.takeLoan(LOAN_OFFERS.first(), Faction.SPAIN, 0f)
        assertFalse(player.canTakeLoan())
    }

    @Test
    fun `repaying clears the debt and the interest is real`() {
        val player = freshPlayer()
        val offer = LOAN_OFFERS.first()
        player.takeLoan(offer, Faction.ENGLAND, 0f)
        assertTrue(player.canRepayLoan())

        player.repayLoan()

        assertNull(player.loan)
        assertEquals(10000 + offer.principal - offer.repayAmount, player.gold)
        assertTrue(offer.repayAmount > offer.principal, "a loan must cost something")
        assertTrue(player.canTakeLoan(), "the next loan is available once the last is cleared")
    }

    @Test
    fun `a captain who cannot cover the repayment is refused`() {
        val player = Player(gold = 0)
        player.takeLoan(LOAN_OFFERS.first(), Faction.FRANCE, 0f)
        player.gold = 0
        assertFalse(player.canRepayLoan())
    }

    @Test
    fun `repaying with no loan is a no-op`() {
        val player = freshPlayer()
        assertFalse(player.canRepayLoan())
        player.repayLoan()
        assertEquals(10000, player.gold)
    }

    @Test
    fun `bigger loans cost proportionally more to pay back`() {
        val ratios = LOAN_OFFERS.map { it.repayAmount.toFloat() / it.principal }
        assertEquals(ratios.sorted(), ratios, "a larger loan should not be cheaper per gold borrowed")
        assertTrue(LOAN_OFFERS.all { it.termGameMinutes > 0f })
    }

    // --- reputation ---

    @Test
    fun `reputation moves with a faction and is clamped at both ends`() {
        val player = freshPlayer()
        player.adjustReputation(Faction.SPAIN, 30)
        assertEquals(30, player.reputation[Faction.SPAIN])
        assertEquals(ReputationTier.FRIENDLY, player.reputationTier(Faction.SPAIN))

        player.adjustReputation(Faction.SPAIN, 10_000)
        assertEquals(REPUTATION_MAX, player.reputation[Faction.SPAIN])

        player.adjustReputation(Faction.SPAIN, -10_000)
        assertEquals(REPUTATION_MIN, player.reputation[Faction.SPAIN])
        assertEquals(ReputationTier.HOSTILE, player.reputationTier(Faction.SPAIN))
    }

    @Test
    fun `angering one faction leaves the others alone`() {
        val player = freshPlayer()
        player.adjustReputation(Faction.SPAIN, -80)
        assertEquals(0, player.reputation[Faction.ENGLAND])
        assertEquals(ReputationTier.NEUTRAL, player.reputationTier(Faction.ENGLAND))
    }

    // --- new game / load ---

    @Test
    fun `reset puts a well-worn captain back to the starting line`() {
        val player = freshPlayer()
        player.buy(Good.SUGAR, 100)
        player.buyShip(ShipTypes.GALLEON, "Flagship", "Havana")
        player.buyWarehouse("Nassau")
        player.takeLoan(LOAN_OFFERS.first(), Faction.SPAIN, 0f)
        player.adjustReputation(Faction.SPAIN, -60)
        player.captains.add(Captain("Someone", 0f, 0.5f))

        player.reset()

        assertEquals(10000, player.gold)
        assertSame(ShipTypes.SLOOP, player.ship)
        assertEquals(0, player.cargoUsed)
        assertTrue(player.warehouses.isEmpty())
        assertNull(player.loan)
        assertTrue(player.captains.isEmpty())
        assertEquals(1, player.ownedShips.size)
        assertEquals(1, player.activeShipId)
        assertEquals(0, player.reputation[Faction.SPAIN])
        assertEquals(ShipTypes.SLOOP.maxHull, player.condition.currentHull)
    }

    @Test
    fun `loading state overwrites gold, ship and cargo without charging for it`() {
        val player = freshPlayer()
        player.loadState(777, ShipTypes.BRIGANTINE, mapOf(Good.RUM to 12))

        assertEquals(777, player.gold)
        assertSame(ShipTypes.BRIGANTINE, player.ship)
        assertEquals(12, player.cargo[Good.RUM])
        assertEquals(0, player.cargo[Good.SUGAR], "goods missing from the save load as empty")
    }

    @Test
    fun `the hull's own condition comes from the fleet roster, not from loadState`() {
        // Condition lives on the active OwnedShip now, so it arrives with loadFleet - which is
        // why PortRoyaleGame.loadGame restores the saved condition AFTER the roster is in place.
        val player = freshPlayer()
        player.loadState(777, ShipTypes.BRIGANTINE, emptyMap())
        player.loadFleet(listOf(OwnedShip(3, "Sea Hawk", ShipTypes.BRIGANTINE, "")), activeShipId = 3)

        assertEquals(ShipTypes.BRIGANTINE.maxHull, player.condition.currentHull)

        player.condition.currentHull = 55
        assertEquals(55, player.ownedShips.single().condition.currentHull, "the write must reach the hull")
    }

    @Test
    fun `loading a fleet replaces the roster wholesale`() {
        val player = freshPlayer()
        player.loadFleet(
            listOf(
                OwnedShip(7, "Old Salt", ShipTypes.BARQUE, "Nassau"),
                OwnedShip(9, "Runner", ShipTypes.CUTTER, "")
            ),
            activeShipId = 9
        )
        assertEquals(listOf(7, 9), player.ownedShips.map { it.id })
        assertEquals(9, player.activeShipId)
    }

    @Test
    fun `a save with no fleet still leaves the captain a hull to stand on`() {
        // Guards the pre-fleet save format: an empty roster must not strand the player shipless.
        val player = freshPlayer()
        player.loadState(500, ShipTypes.GALLEON, emptyMap())
        player.loadFleet(emptyList(), activeShipId = 42)

        assertEquals(1, player.ownedShips.size)
        assertEquals(1, player.activeShipId)
        assertSame(ShipTypes.GALLEON, player.ownedShips.single().shipType)
    }
}
