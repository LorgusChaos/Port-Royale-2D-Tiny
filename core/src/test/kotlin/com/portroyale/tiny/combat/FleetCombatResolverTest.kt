package com.portroyale.tiny.combat

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.world.AiShip
import com.portroyale.tiny.world.AiShipRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A captain's fight resolves in one call with no screen, so unlike the player's own combat these
 * outcomes can be pinned down exactly: the caution threshold decides whether a round fires at all,
 * and it is compared against a hull fraction that only ever falls - so a captain that starts below
 * its threshold can never fire, and one that starts above it against a harmless enemy never drops.
 */
class FleetCombatResolverTest {

    private fun enemy(maxHull: Int = 50, minDamage: Int = 5, maxDamage: Int = 10) = AiShip(
        role = AiShipRole.PIRATE,
        faction = null,
        spritePath = "e.png",
        tint = Color.WHITE,
        speed = 10f,
        maxHull = maxHull,
        minDamage = minDamage,
        maxDamage = maxDamage,
        goldReward = 100,
        tier = 3,
        position = Vector2.Zero.cpy()
    )

    private fun resolve(
        maxHull: Int = 100,
        startHull: Int = 100,
        minDamage: Int = 20,
        maxDamage: Int = 30,
        maneuverability: Int = 70,
        caution: Float = 0.2f,
        enemy: AiShip = enemy()
    ) = FleetCombatResolver.resolve(maxHull, startHull, minDamage, maxDamage, maneuverability, caution, enemy)

    @Test
    fun `a bold captain finishes off a harmless enemy`() {
        val outcome = resolve(caution = 0.2f, enemy = enemy(maxHull = 1, minDamage = 0, maxDamage = 0))
        assertEquals(FleetCombatResult.VICTORY, outcome.result)
    }

    @Test
    fun `a victory pays out salvage within the shared reward band`() {
        val outcome = resolve(caution = 0.2f, enemy = enemy(maxHull = 1, minDamage = 0, maxDamage = 0))
        assertTrue(
            outcome.goldReward in MIN_VICTORY_REWARD..MAX_VICTORY_REWARD,
            "salvage was ${outcome.goldReward}"
        )
    }

    @Test
    fun `an untouched winner comes home at full hull`() {
        val outcome = resolve(startHull = 100, caution = 0.2f, enemy = enemy(maxHull = 1, minDamage = 0, maxDamage = 0))
        assertEquals(FleetCombatResult.VICTORY, outcome.result)
        assertEquals(100, outcome.remainingHull)
    }

    @Test
    fun `a captain already below their nerve never fires a shot`() {
        // caution 0.9 means breaking off below 72% hull; starting at 50% the captain is already
        // past that, and hull only ever falls - so victory is unreachable by construction.
        repeat(50) {
            val outcome = resolve(
                maxHull = 100,
                startHull = 50,
                caution = 0.9f,
                enemy = enemy(maxHull = 1, minDamage = 0, maxDamage = 0)
            )
            assertTrue(
                outcome.result != FleetCombatResult.VICTORY,
                "a captain who never fires cannot sink anything"
            )
            assertEquals(0, outcome.goldReward)
        }
    }

    @Test
    fun `a cautious captain that gets clear keeps whatever hull it had left`() {
        val outcome = resolve(
            maxHull = 100,
            startHull = 50,
            maneuverability = 200,
            caution = 0.9f,
            enemy = enemy(maxHull = 1, minDamage = 0, maxDamage = 0)
        )
        assertEquals(FleetCombatResult.FLED, outcome.result)
        assertEquals(50, outcome.remainingHull)
    }

    @Test
    fun `an overwhelming enemy sinks the captain`() {
        val outcome = resolve(
            caution = 0.2f,
            maneuverability = 0,
            enemy = enemy(maxHull = 100_000, minDamage = 10_000, maxDamage = 10_000)
        )
        assertEquals(FleetCombatResult.DEFEAT, outcome.result)
        assertEquals(0, outcome.remainingHull)
        assertEquals(0, outcome.goldReward)
    }

    @Test
    fun `a stalemate is called off rather than looping forever`() {
        // Neither side can hurt the other; the round cap must break out with a break-off.
        val outcome = resolve(
            minDamage = 0,
            maxDamage = 0,
            caution = 0.2f,
            enemy = enemy(maxHull = 100_000, minDamage = 0, maxDamage = 0)
        )
        assertEquals(FleetCombatResult.FLED, outcome.result)
        assertEquals(100, outcome.remainingHull)
        assertEquals(0, outcome.goldReward)
    }

    @Test
    fun `a bolder captain wins fights a cautious one walks away from`() {
        val harmless = { enemy(maxHull = 30, minDamage = 0, maxDamage = 0) }
        val boldWins = (1..50).count {
            resolve(caution = 0.2f, enemy = harmless()).result == FleetCombatResult.VICTORY
        }
        val timidWins = (1..50).count {
            resolve(startHull = 40, caution = 0.9f, enemy = harmless()).result == FleetCombatResult.VICTORY
        }
        assertEquals(50, boldWins, "a bold captain against a harmless enemy should always win")
        assertEquals(0, timidWins, "a captain below their nerve threshold never engages")
    }

    @Test
    fun `a sunk ship is never reported as still afloat`() {
        repeat(200) {
            val outcome = resolve(
                startHull = 20,
                maneuverability = 0,
                caution = 0.2f,
                enemy = enemy(maxHull = 100_000, minDamage = 5, maxDamage = 15)
            )
            if (outcome.result == FleetCombatResult.DEFEAT) {
                assertEquals(0, outcome.remainingHull)
            } else {
                assertTrue(outcome.remainingHull > 0, "a surviving captain must have hull left")
            }
        }
    }

    @Test
    fun `remaining hull never exceeds what the captain started with`() {
        repeat(200) {
            val outcome = resolve(startHull = 80, caution = 0.5f)
            assertTrue(outcome.remainingHull <= 80, "hull went up during a fight")
            assertTrue(outcome.remainingHull >= 0)
        }
    }

    @Test
    fun `only a victory ever pays out`() {
        repeat(200) {
            val outcome = resolve(startHull = 60, caution = 0.6f)
            if (outcome.result != FleetCombatResult.VICTORY) {
                assertEquals(0, outcome.goldReward, "${outcome.result} should not pay salvage")
            }
        }
    }

    @Test
    fun `a wrecked ship entering a fight is still given a fighting chance`() {
        // startHull is floored at 1 so a 0-hull ship is not instantly and silently destroyed
        // before a single round is played.
        val outcome = resolve(
            maxHull = 1,
            startHull = 0,
            caution = 0.2f,
            enemy = enemy(maxHull = 1, minDamage = 0, maxDamage = 0)
        )
        assertEquals(FleetCombatResult.VICTORY, outcome.result)
        assertEquals(1, outcome.remainingHull)
    }
}
