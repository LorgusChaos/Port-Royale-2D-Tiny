package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.factions.Faction

enum class AiShipRole { TRADE, MILITARY, PIRATE }

/** A roaming, non-player ship: trade/military ships belong to a Faction and sail between that
 *  faction's ports; pirates belong to no one and wander between any port. Stats are a flat
 *  per-role template (see [AiShipFactory]), not randomized - a rough first pass, tune later. */
class AiShip(
    val role: AiShipRole,
    val faction: Faction?,
    val spritePath: String,
    val tint: Color,
    val speed: Float,
    val maxHull: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val goldReward: Int,
    val position: Vector2
) {
    var headingDeg: Float = 0f
    var path: List<Vector2> = emptyList()
    var pathIndex: Int = 0

    val displayName: String
        get() = when (role) {
            AiShipRole.PIRATE -> "Pirate Ship"
            AiShipRole.TRADE -> "${faction?.displayName} Trader"
            AiShipRole.MILITARY -> "${faction?.displayName} Warship"
        }
}

/** Stat templates per role. Trade ships are weak and worth a modest reward (easy prey, the
 *  point of piracy); military ships are the toughest fight but pay best; pirates sit in between,
 *  matching the old fixed "Pirate Sloop" encounter this replaces. */
object AiShipFactory {
    const val TRADE_SPRITE = "sprites/ships/ship_01.png"
    const val MILITARY_SPRITE = "sprites/ships/ship_07.png"
    const val PIRATE_SPRITE = "sprites/ships/ship_02.png"

    /** Every sprite path an AiShip can be created with - PortRoyaleGame preloads exactly these
     *  (plus each purchasable ShipType's own sprite) into its texture map, so this list must stay
     *  in sync with the paths actually used below. */
    val ALL_SPRITES = listOf(TRADE_SPRITE, MILITARY_SPRITE, PIRATE_SPRITE)

    fun trade(faction: Faction, position: Vector2) = AiShip(
        role = AiShipRole.TRADE,
        faction = faction,
        spritePath = TRADE_SPRITE,
        tint = faction.tint,
        speed = 22f,
        maxHull = 40,
        minDamage = 3,
        maxDamage = 8,
        goldReward = 120,
        position = position
    )

    fun military(faction: Faction, position: Vector2) = AiShip(
        role = AiShipRole.MILITARY,
        faction = faction,
        // A different (bulkier-looking) silhouette from the trade sprite so the two roles read
        // apart at a glance even under the same faction tint - ship_13 looked pre-damaged (a
        // heavily torn-sail reskin from the same pack), which was never intentional.
        spritePath = MILITARY_SPRITE,
        tint = faction.tint,
        speed = 30f,
        maxHull = 160,
        minDamage = 18,
        maxDamage = 30,
        goldReward = 250,
        position = position
    )

    fun pirate(position: Vector2) = AiShip(
        role = AiShipRole.PIRATE,
        faction = null,
        spritePath = PIRATE_SPRITE,
        tint = Color.valueOf("8A2E2E"),
        speed = 38f,
        maxHull = 70,
        minDamage = 10,
        maxDamage = 20,
        goldReward = 100,
        position = position
    )
}
