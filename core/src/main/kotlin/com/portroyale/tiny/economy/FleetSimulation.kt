package com.portroyale.tiny.economy

import com.portroyale.tiny.factions.buyPriceMultiplier
import com.portroyale.tiny.factions.sellPriceMultiplier
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.Ports

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

    /** Interrupts a captain's voyage: their ship docks at whichever route port they most
     *  recently left (the best approximation available, since a leg in progress has no tracked
     *  real position), any cargo aboard is written off, and the captain goes idle - still on the
     *  roster, stats and lifetime profit intact, ready to crew a different ship later. */
    fun recall(player: Player, captain: Captain) {
        val shipId = captain.shipId ?: return
        val lastDeparted = if (captain.headingToB) captain.portA else captain.portB
        player.ownedShips.find { it.id == shipId }?.dockedAt = lastDeparted
        captain.shipId = null
        captain.cargoUnits = 0
        captain.cargoGood = null
        captain.cargoCostTotal = 0
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

    /** Advances every active captain's route by however much game-time just passed, resolving
     *  any leg that finished (arrival + trade - see resolveArrival). Danger is NOT handled here
     *  any more: a captain's ship has a real on-screen position (see PortRoyaleGame.
     *  fleetShipTransform), so whether they run into trouble is resolved by real proximity to a
     *  real hostile AI ship, the same system the player's own encounters already use - see
     *  PortRoyaleGame.updateFleetEncounters/loseCaptain. This function only ever advances a
     *  captain who's still alive to advance. */
    fun update(player: Player, currentGameMinutes: Float, distanceBetween: (String, String) -> Float) {
        val active = player.captains.filter { !it.isIdle }
        for (captain in active) {
            if (currentGameMinutes < captain.arrivesAtGameMinutes) continue
            val ship = player.ownedShips.find { it.id == captain.shipId } ?: continue
            resolveArrival(player, captain, ship)
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

    /** Starts the next leg (toward whichever port [Captain.headingToB] now points at), stamping
     *  how long it will really take given this ship's speed and the actual sea distance - not a
     *  flat duration, so a Sloop and a Galleon (or any future speed modifier) visibly move at
     *  different, honest rates instead of every ship sharing one arbitrary timer regardless of
     *  type or route length. Mirrors player.effectiveSpeed's own units (world-units per real
     *  second) so a fleet ship covers ground at the same rate the player's own ship would. */
    private fun departFor(
        captain: Captain,
        ship: OwnedShip,
        currentGameMinutes: Float,
        distanceBetween: (String, String) -> Float
    ) {
        val origin = if (captain.headingToB) captain.portA else captain.portB
        val dest = if (captain.headingToB) captain.portB else captain.portA
        val distance = distanceBetween(origin, dest)
        val realSeconds = distance / ship.shipType.speed
        val minutes = GameClock.realSecondsToGameMinutes(realSeconds).coerceAtLeast(1f)
        captain.legDurationGameMinutes = minutes
        captain.arrivesAtGameMinutes = currentGameMinutes + minutes
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
        val rep = reputationAt(player, port)
        val base = MarketState.sellPrice(port, good)
        val unitPrice = (base * (1f + captain.tradeSense) * sellPriceMultiplier(rep)).toInt().coerceAtLeast(1)
        val total = unitPrice * captain.cargoUnits
        if (total < captain.cargoCostTotal) {
            captain.lastReport =
                "${captain.name} is holding ${captain.cargoUnits}x ${good.displayName} at $port, waiting for a better price."
            return
        }
        player.gold += total
        val profit = total - captain.cargoCostTotal
        captain.totalProfit += profit
        repeat(captain.cargoUnits) { MarketState.onSell(port, good) }
        captain.lastReport = "${captain.name} sold ${captain.cargoUnits}x ${good.displayName} at $port for ${total}g (+${profit}g profit)."
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
        var bestUnitBuy = 1
        var bestMarginPerUnit = 0

        for (good in Good.entries) {
            val unitBuy = (MarketState.buyPrice(port, good) * (1f + captain.tradeSense) * buyMultiplier)
                .toInt().coerceAtLeast(1)
            val unitSell = (MarketState.sellPrice(destPort, good) * (1f + captain.tradeSense) * sellMultiplier)
                .toInt().coerceAtLeast(1)
            val margin = unitSell - unitBuy
            if (margin > bestMarginPerUnit) {
                bestMarginPerUnit = margin
                bestGood = good
                bestUnitBuy = unitBuy
            }
        }

        val good = bestGood
        if (good == null) {
            captain.lastReport = "${captain.name} found nothing worth buying at $port right now - sailing out empty."
            return
        }
        val affordable = player.gold / bestUnitBuy
        val qty = minOf(ship.shipType.cargoCapacity, affordable)
        if (qty <= 0) {
            captain.lastReport = "${captain.name} couldn't afford to stock up at $port - sailing out empty."
            return
        }
        val total = bestUnitBuy * qty
        player.gold -= total
        repeat(qty) { MarketState.onBuy(port, good) }
        captain.cargoGood = good
        captain.cargoUnits = qty
        captain.cargoCostTotal = total
        captain.lastReport = "${captain.name} bought ${qty}x ${good.displayName} at $port for ${total}g."
    }

    private fun reputationAt(player: Player, port: String): Int {
        val faction = Ports.ALL.firstOrNull { it.name == port }?.faction ?: return 0
        return player.reputation[faction] ?: 0
    }
}
