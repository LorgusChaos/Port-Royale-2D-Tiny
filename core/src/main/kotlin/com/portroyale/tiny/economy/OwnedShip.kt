package com.portroyale.tiny.economy

import com.portroyale.tiny.ships.ShipType

/** One hull the player owns - see Player.ownedShips. Distinct from [ShipType] (which is just the
 *  class/stats every ship of that class shares): a player can own several ships of the same
 *  ShipType, each with its own name and location. The ship the player is currently sailing
 *  (Player.ship/condition/cargo) is ALSO one of these - see Player.activeShipId - kept as a
 *  separate cached field on Player rather than derived on every read, so the many existing call
 *  sites that already read player.ship didn't need to change. [dockedAt] is only meaningful while
 *  this ship is idle (not the active one, not out with a captain) - see FleetDockUI. */
class OwnedShip(
    val id: Int,
    var name: String,
    val shipType: ShipType,
    var dockedAt: String
)
