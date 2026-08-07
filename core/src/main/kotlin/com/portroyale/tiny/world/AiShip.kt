package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.ShipTypes

enum class AiShipRole { TRADE, MILITARY, PIRATE }

/** A roaming, non-player ship: trade/military ships belong to a Faction and sail between that
 *  faction's ports; pirates belong to no one and wander between any port. Stats are a flat
 *  per-role template (see [AiShipFactory]), not randomized - a rough first pass, tune later.
 *  [tier] (1-5, matching the ship2_<color>_<tier> sprite set and ShipTypes.ALL's own order) is
 *  what a pirate's fight-or-flee decision compares against the player's own ship tier - see
 *  PortRoyaleGame.isHostile/updateAiShips. */
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
    val tier: Int,
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
 *  point of piracy); military ships are the toughest fight but pay best; pirates roll a random
 *  tier (1-5) each spawn - both their stats/reward AND their sprite (a bigger hull for a bigger
 *  tier) scale with it, and a low-tier pirate that runs into a much better-armed player flees
 *  rather than picking a fight it can't win (see PortRoyaleGame.isHostile).
 *
 *  Sprites come from the same 5-tier ship2_<color>_<tier> set as the player's own ships (see
 *  ShipTypes) - color carries faction identity here (no tint multiply on top, the color IS the
 *  sail), tier carries role weight (trade = a modest tier-2 hull, military = a bigger tier-4
 *  hull, pirates = whatever tier they rolled). [speed] is pulled from that same tier's real
 *  ShipTypes entry, not a separate hand-tuned number - a ship's hull type is what determines how
 *  fast it sails regardless of who owns it (player, a hired captain, or an NPC faction), same
 *  reasoning the player's own effectiveSpeed and FleetSimulation's leg timing both already use.
 *  maxHull/damage/goldReward stay separately tuned per role on purpose (trade = deliberately
 *  weak/easy prey, pirates = a strict tier difficulty ladder) - ShipTypes' own hull numbers
 *  aren't a strict power ladder (a Cutter trades hull for speed), so reusing them there would
 *  undermine the tier-vs-tier fight-or-flee comparison. */
object AiShipFactory {
    private const val TRADE_TIER = 2
    private const val MILITARY_TIER = 4

    private val TRADE_SPRITES = mapOf(
        Faction.SPAIN to "sprites/ships/ship2_cream_2.png",
        Faction.ENGLAND to "sprites/ships/ship2_red_2.png",
        Faction.FRANCE to "sprites/ships/ship2_blue_2.png",
        Faction.NETHERLANDS to "sprites/ships/ship2_green_2.png"
    )
    private val MILITARY_SPRITES = mapOf(
        Faction.SPAIN to "sprites/ships/ship2_cream_4.png",
        Faction.ENGLAND to "sprites/ships/ship2_red_4.png",
        Faction.FRANCE to "sprites/ships/ship2_blue_4.png",
        Faction.NETHERLANDS to "sprites/ships/ship2_green_4.png"
    )
    private val PIRATE_SPRITES = (1..5).associateWith { "sprites/ships/ship2_black_$it.png" }

    /** Every sprite path an AiShip can be created with - PortRoyaleGame preloads exactly these
     *  (plus each purchasable ShipType's own sprite) into its texture map, so this list must stay
     *  in sync with the paths actually used below. */
    val ALL_SPRITES = (TRADE_SPRITES.values + MILITARY_SPRITES.values + PIRATE_SPRITES.values).distinct()

    fun trade(faction: Faction, position: Vector2) = AiShip(
        role = AiShipRole.TRADE,
        faction = faction,
        spritePath = TRADE_SPRITES.getValue(faction),
        tint = Color.WHITE,
        speed = ShipTypes.ALL[TRADE_TIER - 1].speed,
        maxHull = 40,
        minDamage = 3,
        maxDamage = 8,
        goldReward = 120,
        tier = TRADE_TIER,
        position = position
    )

    fun military(faction: Faction, position: Vector2) = AiShip(
        role = AiShipRole.MILITARY,
        faction = faction,
        spritePath = MILITARY_SPRITES.getValue(faction),
        tint = Color.WHITE,
        speed = ShipTypes.ALL[MILITARY_TIER - 1].speed,
        maxHull = 160,
        minDamage = 18,
        maxDamage = 30,
        goldReward = 250,
        tier = MILITARY_TIER,
        position = position
    )

    fun pirate(position: Vector2): AiShip {
        val tier = MathUtils.random(1, 5)
        return AiShip(
            role = AiShipRole.PIRATE,
            faction = null,
            spritePath = PIRATE_SPRITES.getValue(tier),
            tint = Color.WHITE,
            speed = ShipTypes.ALL[tier - 1].speed,
            maxHull = 25 + tier * 25,
            minDamage = 3 + tier * 4,
            maxDamage = 6 + tier * 7,
            goldReward = 40 + tier * 45,
            tier = tier,
            position = position
        )
    }
}
