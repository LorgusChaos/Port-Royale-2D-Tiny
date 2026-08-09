package com.portroyale.tiny.economy

import com.portroyale.tiny.factions.buyPriceMultiplier
import com.portroyale.tiny.factions.sellPriceMultiplier
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.Ports

private const val MAINTENANCE_FOULING_THRESHOLD = 0.5f
private const val MAINTENANCE_HULL_FRACTION_THRESHOLD = 0.2f

/**
 * Everything about what a hired captain actually DOES each leg of their route, plus the hire/
 * assign/recall lifecycle around it - kept separate from both Player (plain state) and
 * PortRoyaleGame (orchestration) so "what happens on arrival" has one obvious home. See Captain,
 * OwnedShip, and Project design/02-fleet-and-captains.md.
 *
 * The captain is a small autonomous trader, not a player-programmed shuttle: the player only
 * picks the ship and the two ports (see hire/assign); which good to carry each leg, and whether
 * a price is even worth selling at, is decided here (see attemptBuy/attemptSell) using the same
 * market prices/reputation multipliers the player themselves would get.
 */
object FleetSimulation {

    /** Commits a rolled candidate (see Captain.rollCandidate/TavernUI) to a specific idle ship
     *  and route, paying the hire cost and stocking the ship's hold for the first A->B leg.
     *  Returns false (spending nothing) if the player can't afford it or the ship isn't actually
     *  free. [distanceBetween] is a sea-route distance lookup (see PortRoyaleGame.fleetSeaDistance)
     *  - injected rather than imported so this stays free of any map/pathfinding dependency. */
    fun hire(
        player: Player,
        candidate: Captain,
        shipId: Int,
        portA: String,
        portB: String,
        currentGameMinutes: Float,
        distanceBetween: (String, String) -> Float
    ): Boolean {
        val cost = Captain.hireCost(player.captains.size)
        val ship = player.ownedShips.find { it.id == shipId } ?: return false
        if (player.gold < cost || !isShipFree(player, ship.id)) return false
        player.gold -= cost
        candidate.shipId = shipId
        candidate.portA = portA
        candidate.portB = portB
        candidate.headingToB = true
        player.captains.add(candidate)
        attemptBuy(player, candidate, ship, portA, portB)
        departFor(candidate, ship, currentGameMinutes, distanceBetween)
        return true
    }

    /** Re-crews an already-hired but currently idle captain (see recall) onto a different idle
     *  ship with a new route - no additional cost, they're already on the payroll. */
    fun assign(
        player: Player,
        captain: Captain,
        shipId: Int,
        portA: String,
        portB: String,
        currentGameMinutes: Float,
        distanceBetween: (String, String) -> Float
    ): Boolean {
        val ship = player.ownedShips.find { it.id == shipId } ?: return false
        if (!isShipFree(player, ship.id)) return false
        captain.shipId = shipId
        captain.portA = portA
        captain.portB = portB
        captain.headingToB = true
        attemptBuy(player, captain, ship, portA, portB)
        departFor(captain, ship, currentGameMinutes, distanceBetween)
        return true
    }

    /** Interrupts a captain's voyage: their ship docks at the shelter port if they were actually
     *  sitting in one, otherwise whichever route port they most recently left (the best
     *  approximation available for a leg genuinely in progress) - any cargo aboard is written
     *  off, and the captain goes idle, still on the roster, stats and lifetime profit intact,
     *  ready to crew a different ship later. */
    fun recall(player: Player, captain: Captain) {
        val shipId = captain.shipId ?: return
        val dockPort = if (captain.shelterPort.isNotEmpty()) {
            captain.shelterPort
        } else if (captain.headingToB) {
            captain.portA
        } else {
            captain.portB
        }
        player.ownedShips.find { it.id == shipId }?.dockedAt = dockPort
        captain.shipId = null
        captain.cargoUnits = 0
        captain.cargoGood = null
        captain.cargoCostTotal = 0
        captain.sheltering = false
        captain.shelterPort = ""
        captain.lastReport = "Recalled - idle, awaiting a new ship."
    }

    fun isShipFree(player: Player, shipId: Int): Boolean =
        shipId != player.activeShipId && player.captains.none { it.shipId == shipId }

    /** True only while a captain is inbound to their fixed home port (portA) - the one leg
     *  endpoint that never changes. Redirecting portB is only safe in that window: arrival
     *  detection for the CURRENT leg is keyed off whichever port is being read at the moment it
     *  completes (see resolveArrival), so changing portB mid-leg while still outbound would make
     *  the captain "arrive" somewhere the rendered path never actually went. */
    fun canRedirect(captain: Captain): Boolean = !captain.headingToB

    /** Advances every active captain's route by however much game-time just passed. Three cases
     *  per captain, in order: still travelling TO a shelter port (see [departForShelter]) - once
     *  the clock says they've arrived, they just start waiting, no redeparture; already waiting
     *  at shelter (see [Captain.sheltering]) - PortRoyaleGame decides when it's safe and calls
     *  [resumeFromShelter], this function does nothing for them meanwhile; normal route leg -
     *  resolve the arrival (trade) and depart for the next one. Danger itself is NOT handled
     *  here: a captain's ship has a real on-screen position (see PortRoyaleGame.
     *  fleetShipTransform), so whether they run into trouble, and whether they see it coming
     *  early enough to avoid it, is resolved by real proximity to real AI ships - see
     *  PortRoyaleGame.updateFleetEncounters/loseCaptain. */
    fun update(player: Player, currentGameMinutes: Float, distanceBetween: (String, String) -> Float) {
        val active = player.captains.filter { !it.isIdle }
        for (captain in active) {
            if (captain.shelterPort.isNotEmpty() && !captain.sheltering) {
                if (currentGameMinutes < captain.arrivesAtGameMinutes) continue
                val ship = player.ownedShips.find { it.id == captain.shipId } ?: continue
                performMaintenance(player, ship)
                captain.sheltering = true
                captain.lastReport = "${captain.name} is holding at ${captain.shelterPort}, waiting for the danger to pass."
                continue
            }
            if (captain.sheltering) continue
            if (currentGameMinutes < captain.arrivesAtGameMinutes) continue
            val ship = player.ownedShips.find { it.id == captain.shipId } ?: continue
            resolveArrival(player, captain, ship)
            performMaintenance(player, ship)
            departFor(captain, ship, currentGameMinutes, distanceBetween)
        }
    }

    /** A captain's ship (and cargo, and the captain themselves) didn't survive an encounter - see
     *  PortRoyaleGame.updateFleetEncounters, the only caller. Permanent: no rescue/rollback, same
     *  as the player's own losing a ship in combat - see Project design/03-combat-threat-model.md. */
    fun loseCaptain(player: Player, captain: Captain) {
        player.ownedShips.removeAll { it.id == captain.shipId }
        player.captains.remove(captain)
    }

    /** Sends a captain running for [shelterPortName] instead of their normal route - see
     *  M11's decision sheet. [legMinutes] is precomputed by the caller (PortRoyaleGame) from the
     *  captain's actual current position, since FleetSimulation has no map/position access of
     *  its own. No trade happens at the shelter port - see [update]'s shelter-arrival branch. */
    fun departForShelter(captain: Captain, shelterPortName: String, legMinutes: Float, currentGameMinutes: Float) {
        captain.shelterPort = shelterPortName
        captain.sheltering = false
        captain.legDurationGameMinutes = legMinutes.coerceAtLeast(1f)
        captain.arrivesAtGameMinutes = currentGameMinutes + captain.legDurationGameMinutes
        captain.lastReport = "${captain.name} makes a run for $shelterPortName to wait out trouble."
    }

    /** Resumes the original route from the shelter port once PortRoyaleGame decides it's safe -
     *  a normal leg toward whichever of portA/portB the captain still hasn't reached, just
     *  starting from [Captain.shelterPort] instead of the port they'd normally depart from. */
    fun resumeFromShelter(player: Player, captain: Captain, currentGameMinutes: Float, distanceBetween: (String, String) -> Float) {
        val ship = player.ownedShips.find { it.id == captain.shipId } ?: return
        val dest = if (captain.headingToB) captain.portB else captain.portA
        val fromPort = captain.shelterPort
        val distance = distanceBetween(fromPort, dest)
        val minutes = GameClock.realSecondsToGameMinutes(distance / ship.effectiveSpeed).coerceAtLeast(1f)
        captain.shelterPort = ""
        captain.sheltering = false
        captain.legDurationGameMinutes = minutes
        captain.arrivesAtGameMinutes = currentGameMinutes + minutes
        captain.lastReport = "${captain.name} resumes course from $fromPort toward $dest."
    }

    /** Redirects the CURRENT leg's timing without changing where it ends up (portA/portB/
     *  headingToB untouched) - used when a captain steers around a nearby threat instead of
     *  sailing straight at it (see PortRoyaleGame's detour logic): the destination is the same,
     *  the route there is longer, so the arrival clock needs to agree with the longer path
     *  actually being rendered, or the two would silently disagree about when they've arrived. */
    fun redirectCurrentLeg(captain: Captain, legMinutes: Float, currentGameMinutes: Float) {
        captain.legDurationGameMinutes = legMinutes.coerceAtLeast(1f)
        captain.arrivesAtGameMinutes = currentGameMinutes + captain.legDurationGameMinutes
    }

    /** Starts the next leg (toward whichever port [Captain.headingToB] now points at), stamping
     *  how long it will really take given this ship's OWN current speed (fouling and sail
     *  upgrades included - see OwnedShip.effectiveSpeed) and the actual sea distance - not a flat
     *  duration, so a Sloop and a Galleon (or a fouled hull overdue for a scrub) visibly move at
     *  different, honest rates instead of every ship sharing one arbitrary timer. Mirrors
     *  player.effectiveSpeed's own units (world-units per real second) so a fleet ship covers
     *  ground at the same rate the player's own ship would, condition for condition. */
    private fun departFor(
        captain: Captain,
        ship: OwnedShip,
        currentGameMinutes: Float,
        distanceBetween: (String, String) -> Float
    ) {
        val origin = if (captain.headingToB) captain.portA else captain.portB
        val dest = if (captain.headingToB) captain.portB else captain.portA
        val distance = distanceBetween(origin, dest)
        val realSeconds = distance / ship.effectiveSpeed
        val minutes = GameClock.realSecondsToGameMinutes(realSeconds).coerceAtLeast(1f)
        captain.legDurationGameMinutes = minutes
        captain.arrivesAtGameMinutes = currentGameMinutes + minutes
    }

    /** Checked on every port arrival, including a shelter stop - a captain looks after their own
     *  ship exactly like a player who was paying attention would, paying from the shared purse
     *  only when it's actually worth it (see the two thresholds above) and only for upkeep
     *  (repair/cleaning), never upgrades - those stay a deliberate investment the player makes,
     *  not something a bot spends on unprompted. Skips silently if the wallet's short - tries
     *  again next port, same as it does when it can't afford to restock cargo. */
    private fun performMaintenance(player: Player, ship: OwnedShip) {
        val condition = ship.condition
        if (condition.fouling > MAINTENANCE_FOULING_THRESHOLD) {
            val cost = condition.cleanCost()
            if (player.gold >= cost) {
                player.gold -= cost
                condition.fouling = 0f
            }
        }
        val maxHull = ship.shipType.maxHull
        if ((maxHull - condition.currentHull).toFloat() / maxHull > MAINTENANCE_HULL_FRACTION_THRESHOLD) {
            val cost = condition.repairCost(maxHull)
            if (player.gold >= cost) {
                player.gold -= cost
                condition.currentHull = maxHull
            }
        }
    }

    /** Resolves whatever just happened on arrival, then flips [Captain.headingToB] so the next
     *  [departFor] heads back the other way: sell the hold if carrying one (only at a profit -
     *  see attemptSell), then, if now empty, decide what (if anything) is worth buying for the
     *  next leg. */
    private fun resolveArrival(player: Player, captain: Captain, ship: OwnedShip) {
        val arrivedPort = if (captain.headingToB) captain.portB else captain.portA
        val nextPort = if (captain.headingToB) captain.portA else captain.portB
        attemptSell(player, captain, arrivedPort)
        if (captain.cargoUnits == 0) {
            attemptBuy(player, captain, ship, arrivedPort, nextPort)
        }
        captain.headingToB = !captain.headingToB
    }

    /** Sells the current hold at [port] - but only if it clears at least what was paid for it.
     *  A captain who can't get a fair price simply keeps carrying the cargo onward (see
     *  resolveArrival: cargoUnits staying > 0 skips the buy step too), trying the other end of
     *  the route next - never locking in a loss just because the leg finished. */
    private fun attemptSell(player: Player, captain: Captain, port: String) {
        val good = captain.cargoGood
        if (good == null || captain.cargoUnits <= 0) return
        val sellMultiplier = sellPriceMultiplier(reputationAt(player, port))

        // A hold big enough to move the market cannot all be sold at the price on the board:
        // every unit floods the port a little further and knocks the next one down. So the
        // decision is made on what the sale would ACTUALLY return, not on one price times the
        // unit count - otherwise a captain commits to a sale that looks profitable and lands a
        // loss, which is exactly what "never lock in a loss" is meant to rule out.
        val proceeds = MarketState.projectedSellPrices(port, good, captain.cargoUnits)
            .sumOf { basePrice -> applyCaptainSellTerms(basePrice, captain, sellMultiplier) }
        if (proceeds < captain.cargoCostTotal) {
            captain.lastReport =
                "${captain.name} is holding ${captain.cargoUnits}x ${good.displayName} at $port, waiting for a better price."
            return
        }

        repeat(captain.cargoUnits) { MarketState.onSell(port, good) }
        player.gold += proceeds
        val profit = proceeds - captain.cargoCostTotal
        captain.totalProfit += profit
        captain.lastReport =
            "${captain.name} sold ${captain.cargoUnits}x ${good.displayName} at $port for ${proceeds}g (+${profit}g profit)."
        captain.cargoUnits = 0
        captain.cargoGood = null
        captain.cargoCostTotal = 0
    }

    /** Picks whichever good looks most profitable to buy at [port] for resale at [destPort],
     *  using today's prices at both ends as the best guess available - a captain can't see the
     *  future, only today's board (see design-pillars' asymmetry of information). Buys nothing,
     *  and sails out empty, if no good clears a profit at all - never buys into a loss on
     *  purpose (the actual loss guard is [attemptSell]; this just avoids the obviously bad case). */
    private fun attemptBuy(player: Player, captain: Captain, ship: OwnedShip, port: String, destPort: String) {
        val buyMultiplier = buyPriceMultiplier(reputationAt(player, port))
        val sellMultiplier = sellPriceMultiplier(reputationAt(player, destPort))

        var bestGood: Good? = null
        var bestMarginPerUnit = 0

        for (good in Good.entries) {
            val margin = unitSellPrice(destPort, good, captain, sellMultiplier) -
                unitBuyPrice(port, good, captain, buyMultiplier)
            if (margin > bestMarginPerUnit) {
                bestMarginPerUnit = margin
                bestGood = good
            }
        }

        val good = bestGood
        if (good == null) {
            captain.lastReport = "${captain.name} found nothing worth buying at $port right now - sailing out empty."
            return
        }

        // Bought a unit at a time, at what each one actually costs once the previous one has
        // pushed the price up - the same rising price the player pays buying the same volume
        // through the market screen. Buying a whole hold at the pre-trade price meant a captain
        // could drain a port's stock to the floor and still pay the untouched price for all of it.
        //
        // The load is also sized to what the far end can actually absorb: each unit is checked
        // against what the Nth sale over there would fetch (the destination's price falls as the
        // hold is unloaded into it, see MarketState.projectedSellPrices), and buying stops at the
        // first unit that would not pay for itself. A captain who ignored that would fill the
        // hold on a margin that only exists for the first few units, then arrive unable to sell
        // the rest without a loss.
        val destPrices = MarketState.projectedSellPrices(destPort, good, ship.shipType.cargoCapacity)
        var units = 0
        var spent = 0
        while (units < ship.shipType.cargoCapacity) {
            val unitBuy = unitBuyPrice(port, good, captain, buyMultiplier)
            if (player.gold - spent < unitBuy) break
            if (applyCaptainSellTerms(destPrices[units], captain, sellMultiplier) <= unitBuy) break
            spent += unitBuy
            units++
            MarketState.onBuy(port, good)
        }

        if (units == 0) {
            captain.lastReport = "${captain.name} couldn't afford to stock up at $port - sailing out empty."
            return
        }
        player.gold -= spent
        captain.cargoGood = good
        captain.cargoUnits = units
        captain.cargoCostTotal = spent
        captain.lastReport = "${captain.name} bought ${units}x ${good.displayName} at $port for ${spent}g."
    }

    /** A captain's own price for one unit. [Captain.tradeSense] pulls the buying price DOWN and
     *  the selling price UP by the same fraction, so a sharper captain is better at both ends of
     *  the deal - the reading the stat's name promises. (It used to raise both, which still made
     *  a high-tradeSense captain more profitable overall, since the margin scaled with it, but
     *  by having them overpay on the way in - the opposite of trading well.) */
    private fun unitBuyPrice(port: String, good: Good, captain: Captain, repMultiplier: Float): Int =
        (MarketState.buyPrice(port, good) * (1f - captain.tradeSense) * repMultiplier).toInt().coerceAtLeast(1)

    private fun unitSellPrice(port: String, good: Good, captain: Captain, repMultiplier: Float): Int =
        applyCaptainSellTerms(MarketState.sellPrice(port, good), captain, repMultiplier)

    /** The captain's own take on a board price they are selling at - shared with the projected
     *  prices in [MarketState.projectedSellPrices] so a quote and a real sale are priced the
     *  same way. */
    private fun applyCaptainSellTerms(basePrice: Int, captain: Captain, repMultiplier: Float): Int =
        (basePrice * (1f + captain.tradeSense) * repMultiplier).toInt().coerceAtLeast(1)

    private fun reputationAt(player: Player, port: String): Int {
        val faction = Ports.ALL.firstOrNull { it.name == port }?.faction ?: return 0
        return player.reputation[faction] ?: 0
    }
}
