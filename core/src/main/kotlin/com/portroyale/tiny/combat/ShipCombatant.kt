package com.portroyale.tiny.combat

/** [goldReward] is what sinking this ship pays out - carried here (rather than looked up from
 *  the AiShip afterwards) so CombatState can award the same tier-scaled prize a fleet captain
 *  gets for the same opponent, instead of a flat random sum unrelated to what was fought. Zero
 *  for the player's own stat block, which is never the thing being sunk for a prize. */
data class ShipCombatant(
    val name: String,
    val maxHull: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val spritePath: String,
    val goldReward: Int = 0
)
