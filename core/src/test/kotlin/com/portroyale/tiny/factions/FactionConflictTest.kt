package com.portroyale.tiny.factions

import com.portroyale.tiny.economy.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * M14's faction conflict. The central claim is not any one number but the equilibrium the two
 * constants produce together: writing `p` for the share of sales revenue a faction receives, its
 * standing climbs exactly while `p > 1/(1+r)`, so at most `k < 1+r` factions can be rising at
 * once. With `r = 3` that is three of the four - the "Influence" ceiling. These tests pin the
 * equilibrium down, so a later balance pass can move `K` freely but cannot silently break the
 * ceiling by touching `r`. See the KDoc on [RIVAL_PENALTY_RATIO] for the derivation.
 */
class FactionConflictTest {

    private val sale = GOLD_PER_REPUTATION_POINT.toInt() // one full point to the seller's faction

    /**
     * Trades [rounds] times over, each round splitting a modest amount of revenue between the
     * factions by [shares] (summing to 1), and returns the resulting standing.
     *
     * Interleaved on purpose. Handing each faction its entire share in one enormous sale would
     * measure nothing: the -100..+100 clamp swallows the intermediate values, and whichever
     * faction happens to be sold to last simply wins. What the equilibrium describes is the
     * direction standing drifts under ordinary, alternating trade, which is how the game is
     * actually played.
     */
    private fun tradeWithShares(
        shares: Map<Faction, Float>,
        rounds: Int = 250,
        revenuePerRound: Int = GOLD_PER_REPUTATION_POINT.toInt() * 4
    ): Map<Faction, Int> {
        val player = Player()
        repeat(rounds) {
            for ((faction, share) in shares) {
                val revenue = (revenuePerRound * share).toInt()
                if (revenue > 0) player.recordSale(faction, revenue)
            }
        }
        return player.reputation.toMap()
    }

    // --- the mechanism ---

    @Test
    fun `selling raises the port's faction and lowers the other three`() {
        val player = Player()
        player.recordSale(Faction.SPAIN, sale * 10)

        assertEquals(10, player.reputation[Faction.SPAIN])
        for (faction in Faction.entries - Faction.SPAIN) {
            assertEquals(-3, player.reputation[faction], "$faction should fall by a third as much")
        }
    }

    @Test
    fun `the penalty is exactly the gain divided by the rival ratio`() {
        val player = Player()
        player.recordSale(Faction.ENGLAND, sale * 30)

        val gain = player.reputation.getValue(Faction.ENGLAND)
        val loss = -player.reputation.getValue(Faction.SPAIN)
        assertEquals(RIVAL_PENALTY_RATIO, gain.toFloat() / loss, 0.05f)
    }

    @Test
    fun `buying nothing and selling nothing moves no one`() {
        val player = Player()
        player.recordSale(Faction.SPAIN, 0)
        player.recordSale(Faction.SPAIN, -500)
        assertTrue(player.reputation.values.all { it == 0 })
    }

    @Test
    fun `a sale with no owning faction still costs everyone`() {
        // Defensive: every port has a faction today, but a null must not silently be a no-op
        // that lets someone trade with nobody and lose nothing.
        val player = Player()
        player.recordSale(null, sale * 9)
        assertTrue(player.reputation.values.all { it < 0 })
    }

    // --- fractional carry ---

    @Test
    fun `small sales accumulate instead of rounding away to nothing`() {
        val player = Player()
        repeat(10) { player.recordSale(Faction.SPAIN, sale / 10) }
        assertEquals(1, player.reputation[Faction.SPAIN], "ten tenth-point sales make one point")
    }

    @Test
    fun `a single sale far below one point still counts toward the next`() {
        val player = Player()
        player.recordSale(Faction.SPAIN, sale / 4)
        assertEquals(0, player.reputation[Faction.SPAIN])
        assertTrue(player.reputationRemainder.getValue(Faction.SPAIN) > 0f, "the quarter point is owed")
    }

    @Test
    fun `the carried remainder always stays below a whole point`() {
        val player = Player()
        repeat(50) { player.recordSale(Faction.FRANCE, sale + 137) }
        for (value in player.reputationRemainder.values) {
            assertTrue(value > -1f && value < 1f, "remainder $value should have been credited")
        }
    }

    // --- the equilibrium that produces the Influence ceiling ---

    @Test
    fun `a faction given more than the threshold share climbs`() {
        val threshold = 1f / (1f + RIVAL_PENALTY_RATIO)
        val above = threshold + 0.10f
        val rest = (1f - above) / 3f
        val result = tradeWithShares(
            mapOf(
                Faction.SPAIN to above,
                Faction.ENGLAND to rest,
                Faction.FRANCE to rest,
                Faction.NETHERLANDS to rest
            )
        )
        assertTrue(result.getValue(Faction.SPAIN) > 0, "a favoured power should climb")
    }

    @Test
    fun `a faction given less than the threshold share sinks`() {
        val threshold = 1f / (1f + RIVAL_PENALTY_RATIO)
        val below = threshold - 0.10f
        val rest = (1f - below) / 3f
        val result = tradeWithShares(
            mapOf(
                Faction.SPAIN to below,
                Faction.ENGLAND to rest,
                Faction.FRANCE to rest,
                Faction.NETHERLANDS to rest
            )
        )
        assertTrue(result.getValue(Faction.SPAIN) < 0, "an under-served power should sink")
    }

    @Test
    fun `trading with everyone equally leaves everyone where they started`() {
        // The design's whole point: an even-handed trader earns no one's friendship.
        val result = tradeWithShares(Faction.entries.associateWith { 0.25f })
        for ((faction, value) in result) {
            assertTrue(value in -3..3, "$faction drifted to $value on perfectly even trade")
        }
    }

    @Test
    fun `three factions can be kept friendly at once but the fourth pays for it`() {
        // This IS the Influence ceiling from 10-game-vision.md 7.2, falling out of r = 3.
        val result = tradeWithShares(
            mapOf(
                Faction.SPAIN to 0.32f,
                Faction.ENGLAND to 0.32f,
                Faction.FRANCE to 0.32f,
                Faction.NETHERLANDS to 0.04f
            )
        )
        val climbing = result.filterValues { it >= 50 }.keys
        assertEquals(setOf(Faction.SPAIN, Faction.ENGLAND, Faction.FRANCE), climbing)
        assertTrue(result.getValue(Faction.NETHERLANDS) < 0, "the neglected power must pay for it")
    }

    @Test
    fun `all four cannot be kept climbing no matter how the trade is split`() {
        // The ceiling has to be structural, not a matter of finding the right split.
        val splits = listOf(
            mapOf(Faction.SPAIN to 0.25f, Faction.ENGLAND to 0.25f, Faction.FRANCE to 0.25f, Faction.NETHERLANDS to 0.25f),
            mapOf(Faction.SPAIN to 0.4f, Faction.ENGLAND to 0.3f, Faction.FRANCE to 0.2f, Faction.NETHERLANDS to 0.1f),
            mapOf(Faction.SPAIN to 0.7f, Faction.ENGLAND to 0.1f, Faction.FRANCE to 0.1f, Faction.NETHERLANDS to 0.1f),
            mapOf(Faction.SPAIN to 0.28f, Faction.ENGLAND to 0.28f, Faction.FRANCE to 0.28f, Faction.NETHERLANDS to 0.16f)
        )
        for (split in splits) {
            val result = tradeWithShares(split)
            val friendly = result.filterValues { it >= 50 }.keys
            assertTrue(friendly.size <= 3, "split $split reached ${friendly.size} allied factions")
        }
    }

    @Test
    fun `standing is clamped at both ends however lopsided the trading gets`() {
        val player = Player()
        repeat(200) { player.recordSale(Faction.SPAIN, sale * 10) }
        assertEquals(REPUTATION_MAX, player.reputation[Faction.SPAIN])
        assertEquals(REPUTATION_MIN, player.reputation[Faction.ENGLAND])
    }

    // --- graded aggression ---

    @Test
    fun `a friendly or indifferent navy leaves the player alone`() {
        assertEquals(MilitaryStance.IGNORES, militaryStance(100))
        assertEquals(MilitaryStance.IGNORES, militaryStance(50))
        assertEquals(MilitaryStance.IGNORES, militaryStance(0))
        assertEquals(MilitaryStance.IGNORES, militaryStance(-14))
    }

    @Test
    fun `an unfriendly navy picks only the fights it is winning`() {
        assertEquals(MilitaryStance.OPPORTUNISTIC, militaryStance(-15))
        assertEquals(MilitaryStance.OPPORTUNISTIC, militaryStance(-49))
    }

    @Test
    fun `a hostile navy hunts regardless`() {
        assertEquals(MilitaryStance.HUNTS, militaryStance(REPUTATION_HOSTILE_THRESHOLD))
        assertEquals(MilitaryStance.HUNTS, militaryStance(REPUTATION_MIN))
    }

    @Test
    fun `the stance only ever hardens as standing falls`() {
        var previous = MilitaryStance.IGNORES
        for (value in REPUTATION_MAX downTo REPUTATION_MIN) {
            val stance = militaryStance(value)
            assertTrue(stance.ordinal >= previous.ordinal, "stance softened at $value")
            previous = stance
        }
    }

    @Test
    fun `every stance is reachable and they line up with the tiers`() {
        assertEquals(
            setOf(MilitaryStance.IGNORES, MilitaryStance.OPPORTUNISTIC, MilitaryStance.HUNTS),
            (REPUTATION_MIN..REPUTATION_MAX).map { militaryStance(it) }.toSet()
        )
        // The notches drawn on the HUD bars are the same boundaries the stance changes at.
        assertNotEquals(militaryStance(-15), militaryStance(-14))
        assertNotEquals(militaryStance(-50), militaryStance(-49))
    }
}
