package com.portroyale.tiny.economy

import com.portroyale.tiny.ships.ShipTypes
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * FleetSimulation trades against the process-wide [MarketState], so every test clears it first.
 * Captains are built by hand rather than rolled, because [Captain.rollCandidate] randomizes
 * tradeSense - which would make every price a captain pays or receives non-deterministic.
 */
class FleetSimulationTest {

    private val anyDistance: (String, String) -> Float = { _, _ -> 1000f }

    private val portA = "Havana"
    private val portB = "Portobelo"

    @BeforeTest
    fun clearMarket() {
        MarketState.reset()
    }

    private fun captain(caution: Float = 0.5f) = Captain(name = "Test Captain", tradeSense = 0f, caution = caution)

    /** A player sailing a Cutter with their original Sloop docked in [portA] - the Sloop is the
     *  only ship a captain can actually be given, since the active hull is never free. */
    private fun playerWithSpareSloop(gold: Int = 100_000): Pair<Player, Int> {
        val player = Player(gold)
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.CUTTER, "Runner", portA)
        return player to sloopId
    }

    // --- hiring ---

    @Test
    fun `hiring puts a captain on the roster, on a ship, on a route`() {
        val (player, sloopId) = playerWithSpareSloop()
        val goldBefore = player.gold

        val hired = FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)

        assertTrue(hired)
        assertEquals(1, player.captains.size)
        val captain = player.captains.single()
        assertEquals(sloopId, captain.shipId)
        assertEquals(portA, captain.portA)
        assertEquals(portB, captain.portB)
        assertTrue(captain.headingToB, "the first leg runs A to B")
        assertFalse(captain.isIdle)
        assertTrue(player.gold <= goldBefore - Captain.hireCost(0), "the hire fee was not charged")
    }

    @Test
    fun `a captain who cannot be paid for is not hired`() {
        val (player, sloopId) = playerWithSpareSloop(gold = 100_000)
        player.gold = Captain.hireCost(0) - 1

        assertFalse(FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance))
        assertTrue(player.captains.isEmpty())
        assertEquals(Captain.hireCost(0) - 1, player.gold, "a refused hire must cost nothing")
    }

    @Test
    fun `a captain cannot be put on the ship the player is sailing`() {
        val (player, _) = playerWithSpareSloop()

        assertFalse(FleetSimulation.hire(player, captain(), player.activeShipId, portA, portB, 0f, anyDistance))
        assertTrue(player.captains.isEmpty())
    }

    @Test
    fun `a captain cannot be put on a ship that does not exist`() {
        val (player, _) = playerWithSpareSloop()
        assertFalse(FleetSimulation.hire(player, captain(), 9999, portA, portB, 0f, anyDistance))
    }

    @Test
    fun `two captains cannot crew the same ship`() {
        val (player, sloopId) = playerWithSpareSloop()
        assertTrue(FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance))
        assertFalse(FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance))
        assertEquals(1, player.captains.size)
    }

    @Test
    fun `each extra captain on the payroll costs more than the last`() {
        val (player, sloopId) = playerWithSpareSloop(gold = 1_000_000)
        player.buyShip(ShipTypes.BARQUE, "Second", portA)
        val secondSpareId = player.ownedShips.first { it.id != sloopId && it.id != player.activeShipId }.id

        val goldBeforeFirst = player.gold
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val firstTotal = goldBeforeFirst - player.gold

        val goldBeforeSecond = player.gold
        FleetSimulation.hire(player, captain(), secondSpareId, portA, portB, 0f, anyDistance)
        val secondTotal = goldBeforeSecond - player.gold

        // Each hire also stocks the new ship's hold, so compare against the fee floor rather
        // than the totals - the fee itself is what has to escalate.
        assertEquals(2, player.captains.size)
        assertTrue(Captain.hireCost(1) > Captain.hireCost(0))
        assertTrue(firstTotal >= Captain.hireCost(0), "first hire charged $firstTotal")
        assertTrue(secondTotal >= Captain.hireCost(1), "second hire charged $secondTotal")
    }

    @Test
    fun `a hired captain stocks the hold before sailing`() {
        // Havana sells tobacco cheap and Portobelo pays well for it, so there is a trade to make.
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)

        val captain = player.captains.single()
        assertEquals(Good.TOBACCO, captain.cargoGood)
        assertTrue(captain.cargoUnits > 0)
        assertTrue(captain.cargoCostTotal > 0)
        assertTrue(
            captain.cargoUnits <= ShipTypes.SLOOP.cargoCapacity,
            "a captain cannot overload their hull"
        )
    }

    @Test
    fun `a captain with no money to trade on still sails, just empty`() {
        val (player, sloopId) = playerWithSpareSloop()
        player.gold = Captain.hireCost(0) // exactly the fee, nothing left for cargo

        assertTrue(FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance))
        val captain = player.captains.single()
        assertEquals(0, captain.cargoUnits)
        assertTrue(captain.lastReport.contains("afford"), "the player should be told why: ${captain.lastReport}")
    }

    // --- ship availability ---

    @Test
    fun `the ship the player is sailing is never free`() {
        val (player, sloopId) = playerWithSpareSloop()
        assertFalse(FleetSimulation.isShipFree(player, player.activeShipId))
        assertTrue(FleetSimulation.isShipFree(player, sloopId))
    }

    @Test
    fun `a ship out with a captain is not free`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        assertFalse(FleetSimulation.isShipFree(player, sloopId))
    }

    // --- reassignment and recall ---

    @Test
    fun `reassigning an idle captain costs nothing extra`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        FleetSimulation.recall(player, captain)

        player.gold = 0 // no money for a second hire fee, but none should be charged
        assertTrue(FleetSimulation.assign(player, captain, sloopId, portB, portA, 0f, anyDistance))
        assertEquals(0, player.gold)
        assertEquals(portB, captain.portA)
        assertEquals(portA, captain.portB)
        assertTrue(captain.headingToB)
    }

    @Test
    fun `a captain cannot be reassigned onto a busy ship`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val idle = captain()
        player.captains.add(idle)

        assertFalse(FleetSimulation.assign(player, idle, sloopId, portA, portB, 0f, anyDistance))
        assertTrue(idle.isIdle)
    }

    @Test
    fun `recalling an outbound captain docks the ship at the port they left`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        assertTrue(captain.headingToB)

        FleetSimulation.recall(player, captain)

        assertEquals(portA, player.ownedShips.first { it.id == sloopId }.dockedAt)
    }

    @Test
    fun `recalling an inbound captain docks the ship at the far port`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        captain.headingToB = false

        FleetSimulation.recall(player, captain)

        assertEquals(portB, player.ownedShips.first { it.id == sloopId }.dockedAt)
    }

    @Test
    fun `recalling a sheltering captain docks the ship where it is actually hiding`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        FleetSimulation.departForShelter(captain, "Nassau", 30f, 0f)

        FleetSimulation.recall(player, captain)

        assertEquals("Nassau", player.ownedShips.first { it.id == sloopId }.dockedAt)
        assertFalse(captain.sheltering)
        assertEquals("", captain.shelterPort)
    }

    @Test
    fun `a recalled captain stays on the payroll with their record intact`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        captain.totalProfit = 4200

        FleetSimulation.recall(player, captain)

        assertTrue(captain.isIdle)
        assertNull(captain.cargoGood)
        assertEquals(0, captain.cargoUnits)
        assertEquals(0, captain.cargoCostTotal)
        assertEquals(4200, captain.totalProfit, "lifetime profit is the captain's record, not the ship's")
        assertTrue(player.captains.contains(captain))
        assertTrue(player.ownedShips.any { it.id == sloopId }, "recall must not scuttle the ship")
    }

    @Test
    fun `recalling an already idle captain does nothing`() {
        val (player, _) = playerWithSpareSloop()
        val idle = captain()
        player.captains.add(idle)
        FleetSimulation.recall(player, idle)
        assertTrue(idle.isIdle)
    }

    @Test
    fun `losing a captain costs both the captain and their ship`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()

        FleetSimulation.loseCaptain(player, captain)

        assertTrue(player.captains.isEmpty())
        assertFalse(player.ownedShips.any { it.id == sloopId })
        assertTrue(player.ownedShips.any { it.id == player.activeShipId }, "the player's own ship is untouched")
    }

    // --- redirecting mid-route ---

    @Test
    fun `a route may only be redirected while the captain is heading home`() {
        val captain = captain()
        captain.headingToB = true
        assertFalse(FleetSimulation.canRedirect(captain), "redirecting an outbound leg would teleport the ship")
        captain.headingToB = false
        assertTrue(FleetSimulation.canRedirect(captain))
    }

    @Test
    fun `a detour keeps the destination but pushes the arrival back`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val straightArrival = captain.arrivesAtGameMinutes

        FleetSimulation.redirectCurrentLeg(captain, legMinutes = straightArrival + 50f, currentGameMinutes = 0f)

        assertEquals(portA, captain.portA)
        assertEquals(portB, captain.portB)
        assertTrue(captain.headingToB)
        assertTrue(captain.arrivesAtGameMinutes > straightArrival)
        assertEquals(captain.arrivesAtGameMinutes, captain.legDurationGameMinutes, 0.0001f)
    }

    @Test
    fun `a leg is never stamped as instantaneous`() {
        // legProgress divides by the duration, so zero would put a NaN on the map.
        val captain = captain()
        FleetSimulation.redirectCurrentLeg(captain, legMinutes = 0f, currentGameMinutes = 100f)
        assertTrue(captain.legDurationGameMinutes >= 1f)
        assertTrue(captain.arrivesAtGameMinutes > 100f)
    }

    // --- sheltering ---

    @Test
    fun `running for shelter sets a course without arriving yet`() {
        val captain = captain()
        FleetSimulation.departForShelter(captain, "Nassau", legMinutes = 40f, currentGameMinutes = 10f)

        assertEquals("Nassau", captain.shelterPort)
        assertFalse(captain.sheltering, "still en route, not yet hiding")
        assertEquals(40f, captain.legDurationGameMinutes)
        assertEquals(50f, captain.arrivesAtGameMinutes)
        assertTrue(captain.lastReport.contains("Nassau"))
    }

    @Test
    fun `arriving at shelter starts the wait instead of a new leg`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        FleetSimulation.departForShelter(captain, "Nassau", legMinutes = 40f, currentGameMinutes = 0f)

        FleetSimulation.update(player, currentGameMinutes = 40f, distanceBetween = anyDistance)

        assertTrue(captain.sheltering)
        assertEquals("Nassau", captain.shelterPort)
        assertTrue(captain.headingToB, "hiding does not count as completing the leg")
    }

    @Test
    fun `a sheltering captain waits and does not trade`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        FleetSimulation.departForShelter(captain, "Nassau", 40f, 0f)
        FleetSimulation.update(player, 40f, anyDistance)

        val cargoUnits = captain.cargoUnits
        val gold = player.gold
        FleetSimulation.update(player, 10_000f, anyDistance)

        assertTrue(captain.sheltering)
        assertEquals(cargoUnits, captain.cargoUnits)
        assertEquals(gold, player.gold)
    }

    @Test
    fun `resuming from shelter puts the captain back on their original course`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        FleetSimulation.departForShelter(captain, "Nassau", 40f, 0f)
        FleetSimulation.update(player, 40f, anyDistance)

        FleetSimulation.resumeFromShelter(player, captain, currentGameMinutes = 100f, distanceBetween = anyDistance)

        assertEquals("", captain.shelterPort)
        assertFalse(captain.sheltering)
        assertTrue(captain.headingToB, "the original destination is unchanged")
        assertTrue(captain.arrivesAtGameMinutes > 100f)
        assertTrue(captain.lastReport.contains("Nassau"))
    }

    // --- running the route ---

    @Test
    fun `a captain mid-leg is left alone`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val arrival = captain.arrivesAtGameMinutes
        val report = captain.lastReport

        FleetSimulation.update(player, arrival - 0.5f, anyDistance)

        assertEquals(arrival, captain.arrivesAtGameMinutes, "the leg was resolved early")
        assertEquals(report, captain.lastReport)
        assertTrue(captain.headingToB)
    }

    @Test
    fun `arriving turns the ship around and books the next leg`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val arrival = captain.arrivesAtGameMinutes

        FleetSimulation.update(player, arrival, anyDistance)

        assertFalse(captain.headingToB, "the captain now heads back to the home port")
        assertTrue(captain.arrivesAtGameMinutes > arrival, "a fresh leg was not booked")
    }

    @Test
    fun `an idle captain is skipped by the simulation`() {
        val (player, _) = playerWithSpareSloop()
        val idle = captain()
        idle.arrivesAtGameMinutes = 0f
        player.captains.add(idle)

        FleetSimulation.update(player, 10_000f, anyDistance)

        assertEquals(0f, idle.arrivesAtGameMinutes, "an idle captain has no leg to advance")
    }

    @Test
    fun `selling a full hold at a profit pays the player and is recorded`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val goldAfterHire = player.gold

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertTrue(captain.totalProfit > 0, "a tobacco run from Havana to Portobelo should clear a profit")
        assertTrue(player.gold > goldAfterHire, "the proceeds never reached the purse")
        assertNotEquals(Good.TOBACCO, captain.cargoGood, "the tobacco should have been sold and the hold restocked")
    }

    @Test
    fun `a profitable sale is only visible as lifetime profit, not in the last report`() {
        // Documents current behaviour rather than endorsing it: resolveArrival calls attemptSell
        // and then attemptBuy, and attemptBuy always overwrites lastReport - so the
        // "sold Nx ... (+Ng profit)" line attemptSell writes is never the one the player reads.
        // Captain.totalProfit is what actually surfaces the gain.
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertTrue(captain.totalProfit > 0)
        assertTrue(captain.lastReport.contains("bought"), captain.lastReport)
        assertFalse(captain.lastReport.contains("profit"), "if this now passes, the report order was fixed")
    }

    @Test
    fun `a captain refuses to sell at a loss and carries the cargo onward`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        // Rewrite the hold as something bought far above what the destination will pay.
        captain.cargoGood = Good.SILVER
        captain.cargoUnits = 10
        captain.cargoCostTotal = 1_000_000
        val goldBefore = player.gold

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertEquals(10, captain.cargoUnits, "the cargo should still be aboard")
        assertSame(Good.SILVER, captain.cargoGood)
        assertEquals(0, captain.totalProfit)
        assertTrue(player.gold <= goldBefore, "nothing should have been sold")
        assertTrue(captain.lastReport.contains("waiting for a better price"), captain.lastReport)
    }

    @Test
    fun `a faster hull covers the same route in less game time`() {
        val slow = Player(1_000_000)
        val slowSpare = slow.activeShipId
        slow.buyShip(ShipTypes.BARQUE, "Active", portA)
        FleetSimulation.hire(slow, captain(), slowSpare, portA, portB, 0f, anyDistance)

        MarketState.reset()

        val fast = Player(1_000_000)
        val fastSpare = fast.activeShipId
        fast.buyShip(ShipTypes.BARQUE, "Active", portA)
        fast.ownedShips.first { it.id == fastSpare }.condition.sailLevel = 3
        FleetSimulation.hire(fast, captain(), fastSpare, portA, portB, 0f, anyDistance)

        assertTrue(
            fast.captains.single().legDurationGameMinutes < slow.captains.single().legDurationGameMinutes,
            "sail upgrades should shorten a captain's leg"
        )
    }

    @Test
    fun `a fouled hull slows a captain's route down`() {
        val clean = Player(1_000_000)
        val cleanSpare = clean.activeShipId
        clean.buyShip(ShipTypes.BARQUE, "Active", portA)
        FleetSimulation.hire(clean, captain(), cleanSpare, portA, portB, 0f, anyDistance)

        MarketState.reset()

        val fouled = Player(1_000_000)
        val fouledSpare = fouled.activeShipId
        fouled.buyShip(ShipTypes.BARQUE, "Active", portA)
        fouled.ownedShips.first { it.id == fouledSpare }.condition.fouling = 1f
        FleetSimulation.hire(fouled, captain(), fouledSpare, portA, portB, 0f, anyDistance)

        assertTrue(
            fouled.captains.single().legDurationGameMinutes > clean.captains.single().legDurationGameMinutes
        )
    }

    // --- upkeep on arrival ---

    @Test
    fun `a captain scrubs a badly fouled hull on arrival`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val ship = player.ownedShips.first { it.id == sloopId }
        ship.condition.fouling = 0.9f

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertEquals(0f, ship.condition.fouling, "a fouled hull should have been cleaned in port")
    }

    @Test
    fun `a lightly fouled hull is not worth the cleaning fee`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val ship = player.ownedShips.first { it.id == sloopId }
        ship.condition.fouling = 0.1f

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertEquals(0.1f, ship.condition.fouling)
    }

    @Test
    fun `a captain repairs a badly damaged hull on arrival`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val ship = player.ownedShips.first { it.id == sloopId }
        ship.condition.currentHull = 20

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertEquals(ShipTypes.SLOOP.maxHull, ship.condition.currentHull)
    }

    @Test
    fun `a scratch is not worth a full refit`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val ship = player.ownedShips.first { it.id == sloopId }
        ship.condition.currentHull = ShipTypes.SLOOP.maxHull - 5

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertEquals(ShipTypes.SLOOP.maxHull - 5, ship.condition.currentHull)
    }

    @Test
    fun `upkeep is skipped rather than going into debt`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val ship = player.ownedShips.first { it.id == sloopId }
        ship.condition.fouling = 0.9f
        ship.condition.currentHull = 20
        // Empty hold and empty purse: nothing to sell, nothing to spend.
        captain.cargoGood = null
        captain.cargoUnits = 0
        captain.cargoCostTotal = 0
        player.gold = 0

        FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)

        assertEquals(0, player.gold, "a broke captain must not overdraw the purse")
        assertEquals(0.9f, ship.condition.fouling)
        assertEquals(20, ship.condition.currentHull)
    }

    @Test
    fun `a captain never spends the player's gold on upgrades`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()
        val ship = player.ownedShips.first { it.id == sloopId }

        repeat(6) { FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance) }

        assertEquals(0, ship.condition.cannonLevel)
        assertEquals(0, ship.condition.oarLevel)
        assertEquals(0, ship.condition.sailLevel)
    }

    @Test
    fun `a captain running a route keeps flipping between the two ports`() {
        val (player, sloopId) = playerWithSpareSloop()
        FleetSimulation.hire(player, captain(), sloopId, portA, portB, 0f, anyDistance)
        val captain = player.captains.single()

        val headings = mutableListOf(captain.headingToB)
        repeat(4) {
            FleetSimulation.update(player, captain.arrivesAtGameMinutes, anyDistance)
            headings.add(captain.headingToB)
        }

        assertEquals(listOf(true, false, true, false, true), headings)
        for (i in 1 until headings.size) {
            assertNotEquals(headings[i - 1], headings[i])
        }
    }
}
