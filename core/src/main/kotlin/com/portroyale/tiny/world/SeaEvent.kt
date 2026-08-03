package com.portroyale.tiny.world

import com.badlogic.gdx.math.Vector2

/** Genre-classic random encounters, deliberately light on mechanics: a flavor-only bottle, a
 *  gold or goods chest, and a castaway who needs a lift to a specific port. */
enum class SeaEventType { BOTTLE, CHEST_GOLD, CHEST_GOODS, CASTAWAY }

/** One triggerable location - invisible on the map, consumed on contact with the player's ship
 *  and not re-rolled until [SeaEventSlot] respawns it elsewhere (see PortRoyaleGame). */
class SeaEventPoint(val position: Vector2, val type: SeaEventType)

/** One "slot" in the fixed-size pool of concurrent sea events: either holds a live, uncollected
 *  [point], or is on cooldown ([respawnTimer] counting down) before a new point is rolled
 *  somewhere else in the water. */
class SeaEventSlot {
    var point: SeaEventPoint? = null
    var respawnTimer: Float = 0f
}
