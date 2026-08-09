package com.portroyale.tiny.factions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReputationTest {

    @Test
    fun `the tier boundaries land exactly where the thresholds say`() {
        assertEquals(ReputationTier.HOSTILE, reputationTier(REPUTATION_MIN))
        assertEquals(ReputationTier.HOSTILE, reputationTier(REPUTATION_HOSTILE_THRESHOLD))
        assertEquals(ReputationTier.UNFRIENDLY, reputationTier(REPUTATION_HOSTILE_THRESHOLD + 1))
        assertEquals(ReputationTier.UNFRIENDLY, reputationTier(-15))
        assertEquals(ReputationTier.NEUTRAL, reputationTier(-14))
        assertEquals(ReputationTier.NEUTRAL, reputationTier(0))
        assertEquals(ReputationTier.NEUTRAL, reputationTier(14))
        assertEquals(ReputationTier.FRIENDLY, reputationTier(15))
        assertEquals(ReputationTier.FRIENDLY, reputationTier(49))
        assertEquals(ReputationTier.ALLIED, reputationTier(50))
        assertEquals(ReputationTier.ALLIED, reputationTier(REPUTATION_MAX))
    }

    @Test
    fun `standing never gets worse as reputation rises`() {
        var previous = reputationTier(REPUTATION_MIN).ordinal
        for (value in REPUTATION_MIN..REPUTATION_MAX) {
            val tier = reputationTier(value).ordinal
            assertTrue(tier >= previous, "tier went backwards at reputation $value")
            previous = tier
        }
    }

    @Test
    fun `every tier is reachable somewhere in the range`() {
        val reached = (REPUTATION_MIN..REPUTATION_MAX).map { reputationTier(it) }.toSet()
        assertEquals(ReputationTier.entries.toSet(), reached)
    }

    @Test
    fun `friends charge less and pay more`() {
        assertTrue(buyPriceMultiplier(80) < buyPriceMultiplier(0))
        assertTrue(sellPriceMultiplier(80) > sellPriceMultiplier(0))
    }

    @Test
    fun `enemies charge more and pay less`() {
        assertTrue(buyPriceMultiplier(-80) > buyPriceMultiplier(0))
        assertTrue(sellPriceMultiplier(-80) < sellPriceMultiplier(0))
    }

    @Test
    fun `neutral standing changes nothing about a price`() {
        assertEquals(1f, buyPriceMultiplier(0))
        assertEquals(1f, sellPriceMultiplier(0))
    }

    @Test
    fun `the buy and sell adjustments are mirror images of each other`() {
        for (value in REPUTATION_MIN..REPUTATION_MAX) {
            assertEquals(2f, buyPriceMultiplier(value) + sellPriceMultiplier(value), 0.0001f)
        }
    }

    @Test
    fun `price multipliers stay positive at every standing`() {
        for (value in REPUTATION_MIN..REPUTATION_MAX) {
            assertTrue(buyPriceMultiplier(value) > 0f)
            assertTrue(sellPriceMultiplier(value) > 0f)
        }
    }

    @Test
    fun `an allied port is the best place to trade and a hostile one the worst`() {
        val allied = buyPriceMultiplier(REPUTATION_MAX)
        val hostile = buyPriceMultiplier(REPUTATION_MIN)
        assertEquals(allied, (REPUTATION_MIN..REPUTATION_MAX).minOf { buyPriceMultiplier(it) })
        assertEquals(hostile, (REPUTATION_MIN..REPUTATION_MAX).maxOf { buyPriceMultiplier(it) })
    }

    @Test
    fun `all four colonial powers are present and distinct`() {
        assertEquals(4, Faction.entries.size)
        assertEquals(4, Faction.entries.map { it.displayName }.distinct().size)
        assertEquals(4, Faction.entries.map { it.tint.toString() }.distinct().size)
    }

    @Test
    fun `every tier has a label for the UI`() {
        for (tier in ReputationTier.entries) {
            assertTrue(tier.label.isNotBlank(), "$tier has no label")
        }
    }
}
