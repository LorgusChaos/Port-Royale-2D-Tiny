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
