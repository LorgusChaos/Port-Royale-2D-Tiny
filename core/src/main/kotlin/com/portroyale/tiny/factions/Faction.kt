package com.portroyale.tiny.factions

import com.badlogic.gdx.graphics.Color

/** The four colonial powers actually present in the Caribbean this game's ports are drawn from -
 *  pirates are deliberately not a Faction (no home ports, no reputation, always hostile). */
enum class Faction(val displayName: String, val tint: Color) {
    SPAIN("Spain", Color.valueOf("D4B84A")),
    ENGLAND("England", Color.valueOf("C23B3B")),
    FRANCE("France", Color.valueOf("A8B8E0")),
    NETHERLANDS("Netherlands", Color.valueOf("E08A32"))
}

enum class ReputationTier(val label: String) {
    HOSTILE("Hostile"),
    UNFRIENDLY("Unfriendly"),
    NEUTRAL("Neutral"),
    FRIENDLY("Friendly"),
    ALLIED("Allied")
}

const val REPUTATION_MIN = -100
const val REPUTATION_MAX = 100
const val REPUTATION_HOSTILE_THRESHOLD = -50

/** Gold of sale revenue worth one point of reputation with the faction that owns the port sold
 *  in. A placeholder until M15's balance pass - only its ratio to [RIVAL_PENALTY_RATIO] carries
 *  design weight. */
const val GOLD_PER_REPUTATION_POINT = 2000f

/**
 * How much gentler the same sale is on the three factions NOT being sold to: each loses
 * `1/RIVAL_PENALTY_RATIO` of what the buyer gains.
 *
 * Not an eyeballed number. Writing `p` for the share of your sales revenue a faction receives,
 * its reputation rises exactly while `p > 1 / (1 + r)`, so at most `k < 1 + r` factions can be
 * climbing at once. `r = 3` puts that threshold at a 25% share and the cap at three factions -
 * which is precisely the "Influence" status ceiling (+50 with three of the four, never all
 * four) from Project design/10-game-vision.md §7.2. Raise it to 4 and the ceiling disappears;
 * drop it to 2 and only two factions can ever be kept friendly.
 *
 * M15's balance pass may move [GOLD_PER_REPUTATION_POINT] freely - it only sets the pace. This
 * one sets the shape, and FactionConflictTest guards it.
 */
const val RIVAL_PENALTY_RATIO = 3f

/** How a faction's navy treats the player, by standing - the graded replacement for a single
 *  hostile/not-hostile threshold. [OPPORTUNISTIC] reuses the rule pirates already follow (only
 *  pick a fight you are winning), so an unfriendly power harasses a weak trader without
 *  declaring open season on a well-armed one. */
enum class MilitaryStance { IGNORES, OPPORTUNISTIC, HUNTS }

fun militaryStance(reputation: Int): MilitaryStance = when (reputationTier(reputation)) {
    ReputationTier.HOSTILE -> MilitaryStance.HUNTS
    ReputationTier.UNFRIENDLY -> MilitaryStance.OPPORTUNISTIC
    else -> MilitaryStance.IGNORES
}

fun reputationTier(value: Int): ReputationTier = when {
    value <= REPUTATION_HOSTILE_THRESHOLD -> ReputationTier.HOSTILE
    value <= -15 -> ReputationTier.UNFRIENDLY
    value < 15 -> ReputationTier.NEUTRAL
    value < 50 -> ReputationTier.FRIENDLY
    else -> ReputationTier.ALLIED
}

/** Rough, unbalanced-on-purpose first pass: better standing means better prices, worse standing
 *  means worse prices, symmetric in both directions. */
private fun tierPriceAdjust(value: Int): Float = when (reputationTier(value)) {
    ReputationTier.HOSTILE -> 0.20f
    ReputationTier.UNFRIENDLY -> 0.10f
    ReputationTier.NEUTRAL -> 0f
    ReputationTier.FRIENDLY -> -0.10f
    ReputationTier.ALLIED -> -0.20f
}

fun buyPriceMultiplier(reputation: Int): Float = 1f + tierPriceAdjust(reputation)
fun sellPriceMultiplier(reputation: Int): Float = 1f - tierPriceAdjust(reputation)
