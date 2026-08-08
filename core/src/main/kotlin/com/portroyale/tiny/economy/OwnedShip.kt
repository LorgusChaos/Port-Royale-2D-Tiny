package com.portroyale.tiny.economy

import com.portroyale.tiny.ships.ShipCondition
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.effectiveMaxDamage
import com.portroyale.tiny.ships.effectiveManeuverability
import com.portroyale.tiny.ships.effectiveMinDamage
import com.portroyale.tiny.ships.effectiveSpeed

/** One hull the player owns - see Player.ownedShips. Distinct from [ShipType] (which is just the
 *  class/stats every ship of that class shares): a player can own several ships of the same
 *  ShipType, each with its own name and location. The ship the player is currently sailing
 *  (Player.ship/condition/cargo) is ALSO one of these - see Player.activeShipId - kept as a
 *  separate cached field on Player rather than derived on every read, so the many existing call
 *  sites that already read player.ship didn't need to change. [dockedAt] is only meaningful while
 *  this ship is idle (not the active one, not out with a captain) - see FleetDockUI.
 *
 *  [condition] is this hull's own wear/upgrades (see M11 decision sheet) - a captain's ship
 *  fouls and takes damage for real, exactly like the player's active ship, using the same
 *  ShipCondition class and the same effectiveX formulas (see ships/ShipCondition.kt) so the two
 *  can never drift apart. Only accumulates fouling while a captain is actually out sailing it -
 *  see PortRoyaleGame.updateFleetEncounters/FleetSimulation. */
class OwnedShip(
    val id: Int,
    var name: String,
    val shipType: ShipType,
    var dockedAt: String,
    val condition: ShipCondition = ShipCondition(shipType.maxHull)
) {
    val effectiveSpeed: Float get() = effectiveSpeed(shipType, condition)
    val effectiveManeuverability: Int get() = effectiveManeuverability(shipType, condition)
    val effectiveMinDamage: Int get() = effectiveMinDamage(shipType, condition)
    val effectiveMaxDamage: Int get() = effectiveMaxDamage(shipType, condition)
}
