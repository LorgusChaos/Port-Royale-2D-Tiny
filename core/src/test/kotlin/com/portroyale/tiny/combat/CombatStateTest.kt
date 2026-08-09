package com.portroyale.tiny.combat

import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Combat rolls against the shared [kotlin.random.Random], which cannot be seeded from here, so
 * these tests assert what must hold for EVERY roll (hull never negative, an ended fight stays
 * ended, damage carries over) and use opponents rigged so only one outcome is reachable - a
 * 0-damage enemy can never win, a 100000-hull enemy can never lose. Where a probability is itself
 * the thing under test, the sample is sized so a false failure is on the order of 1e-13.
 */
class CombatStateTest {

    private val rounds = 500

    private fun playerStats() =
        ShipCombatant("Sloop", ShipTypes.SLOOP.maxHull, ShipTypes.SLOOP.minDamage, ShipTypes.SLOOP.maxDamage, "p.png")

    /** An enemy that cannot damage the player - the only reachable outcome is victory. */
    private fun harmlessEnemy(maxHull: Int = 1) = ShipCombatant("Straw Man", maxHull, 0, 0, "e.png")

    /** An enemy the player cannot sink, hitting hard enough to end things in one connected shot. */
    private fun unbeatableEnemy() = ShipCombatant("Leviathan", 100_000, 1000, 1000, "e.png")

    private fun fight(player: Player = Player(), enemy: ShipCombatant = harmlessEnemy()) =
        CombatState(player, playerStats(), enemy)

    @Test
    fun `a fight opens with both ships undamaged and no result yet`() {
        val player = Player()
        val combat = fight(player)

        assertEquals(CombatOutcome.ONGOING, combat.outcome)
        assertFalse(combat.isOver)
        assertEquals(combat.enemyMaxHull, combat.enemyHull)
        assertEquals(player.condition.currentHull, combat.playerHull)
        assertTrue(combat.log.isNotBlank(), "the player needs to be told what they are facing")
    }

    @Test
    fun `the encounter reports who and what is being fought`() {
        val combat = CombatState(Player(), playerStats(), harmlessEnemy(40))

        assertEquals("Straw Man", combat.enemyName)
        assertEquals(40, combat.enemyMaxHull)
        assertEquals(ShipTypes.SLOOP.maxHull, combat.playerMaxHull)
        assertEquals("e.png", combat.enemySpritePath)
        assertEquals("p.png", combat.playerSpritePath)
    }

    @Test
    fun `a battered ship enters the next fight still battered`() {
        // Hull damage is persistent, not per-encounter - that is what makes repairs matter.
        val player = Player()
        player.condition.currentHull = 33
        assertEquals(33, fight(player).playerHull)
    }

    @Test
    fun `damage taken is written straight back onto the ship`() {
        val player = Player()
        val combat = CombatState(player, playerStats(), unbeatableEnemy())

        repeat(rounds) { if (!combat.isOver) combat.playerAttack() }

        assertEquals(combat.playerHull, player.condition.currentHull)
    }

    @Test
    fun `sinking the enemy wins the fight and pays out salvage`() {
        val player = Player()
        val goldBefore = player.gold
        val combat = CombatState(player, playerStats(), harmlessEnemy())

        repeat(rounds) { if (!combat.isOver) combat.playerAttack() }

        assertEquals(CombatOutcome.VICTORY, combat.outcome)
        assertEquals(0, combat.enemyHull)
        val reward = player.gold - goldBefore
        assertTrue(reward in MIN_VICTORY_REWARD..MAX_VICTORY_REWARD, "salvage was $reward")
    }

    @Test
    fun `losing the hull ends the fight in defeat`() {
        val player = Player()
        val combat = CombatState(player, playerStats(), unbeatableEnemy())

        repeat(rounds) { if (!combat.isOver) combat.playerAttack() }

        assertEquals(CombatOutcome.DEFEAT, combat.outcome)
        assertEquals(0, combat.playerHull)
        assertEquals(0, player.condition.currentHull)
    }

    @Test
    fun `a defeat pays out nothing`() {
        val player = Player()
        val goldBefore = player.gold
        val combat = CombatState(player, playerStats(), unbeatableEnemy())

        repeat(rounds) { if (!combat.isOver) combat.playerAttack() }

        assertEquals(CombatOutcome.DEFEAT, combat.outcome)
        assertEquals(goldBefore, player.gold)
    }

    @Test
    fun `breaking off eventually succeeds and ends the fight`() {
        val combat = CombatState(Player(), playerStats(), harmlessEnemy(maxHull = 100_000))

        repeat(rounds) { if (!combat.isOver) combat.playerFlee() }

        assertEquals(CombatOutcome.FLED, combat.outcome)
    }

    @Test
    fun `running away never sinks the enemy`() {
        val enemy = harmlessEnemy(maxHull = 100_000)
        val combat = CombatState(Player(), playerStats(), enemy)

        repeat(rounds) { if (!combat.isOver) combat.playerFlee() }

        assertEquals(enemy.maxHull, combat.enemyHull)
    }

    @Test
    fun `a failed escape is punished by return fire`() {
        // Every attempt either gets away or hands the enemy a free shot, so across many fights
        // against an enemy that one-shots, some must end in defeat.
        var defeats = 0
        repeat(100) {
            val player = Player()
            player.condition.currentHull = 1
            val combat = CombatState(player, playerStats(), unbeatableEnemy())
            repeat(rounds) { if (!combat.isOver) combat.playerFlee() }
            if (combat.outcome == CombatOutcome.DEFEAT) defeats++
        }
        assertTrue(defeats > 0, "fleeing was never punished in 100 fights")
    }

    @Test
    fun `an ended fight ignores further orders`() {
        val player = Player()
        val combat = CombatState(player, playerStats(), harmlessEnemy())
        repeat(rounds) { if (!combat.isOver) combat.playerAttack() }

        val outcome = combat.outcome
        val log = combat.log
        val gold = player.gold
        val playerHull = combat.playerHull

        combat.playerAttack()
        combat.playerFlee()

        assertEquals(outcome, combat.outcome)
        assertEquals(log, combat.log, "a finished fight should not keep narrating")
        assertEquals(gold, player.gold, "no double salvage")
        assertEquals(playerHull, combat.playerHull)
    }

    @Test
    fun `hulls never go negative on either side`() {
        repeat(20) {
            val combat = CombatState(Player(), playerStats(), ShipCombatant("Even Match", 60, 10, 20, "e.png"))
            for (round in 0 until rounds) {
                if (combat.isOver) break
                if (round % 3 == 0) combat.playerFlee() else combat.playerAttack()
                assertTrue(combat.playerHull >= 0)
                assertTrue(combat.enemyHull >= 0)
            }
        }
    }

    @Test
    fun `every round leaves the player something to read`() {
        val combat = CombatState(Player(), playerStats(), ShipCombatant("Even Match", 60, 10, 20, "e.png"))
        for (round in 0 until rounds) {
            if (combat.isOver) break
            combat.playerAttack()
            assertTrue(combat.log.isNotBlank())
            assertTrue(combat.log.contains("Even Match"), "the log should name the opponent: ${combat.log}")
        }
    }

    @Test
    fun `a nimbler ship gets away more often`() {
        // Flee odds scale with effective maneuverability; a Sloop (70) must beat a Galleon (30).
        val sloop = countFirstRoundEscapes(ShipTypes.SLOOP)
        val galleon = countFirstRoundEscapes(ShipTypes.GALLEON)
        assertTrue(sloop > galleon, "sloop escaped $sloop, galleon $galleon (of $ESCAPE_TRIALS)")
    }

    @Test
    fun `oar upgrades make a ship harder to catch`() {
        val plain = countFirstRoundEscapes(ShipTypes.SLOOP, oarLevel = 0)
        val upgraded = countFirstRoundEscapes(ShipTypes.SLOOP, oarLevel = 3)
        assertTrue(upgraded > plain, "plain escaped $plain, upgraded $upgraded (of $ESCAPE_TRIALS)")
    }

    private fun countFirstRoundEscapes(shipType: ShipType, oarLevel: Int = 0): Int {
        var escapes = 0
        repeat(ESCAPE_TRIALS) {
            val player = Player()
            player.buyShip(shipType, shipType.name, "Havana")
            player.condition.oarLevel = oarLevel
            val combat = CombatState(
                player,
                ShipCombatant(shipType.name, shipType.maxHull, shipType.minDamage, shipType.maxDamage, "p.png"),
                harmlessEnemy(maxHull = 100_000)
            )
            combat.playerFlee()
            if (combat.outcome == CombatOutcome.FLED) escapes++
        }
        return escapes
    }

    @Test
    fun `a better hull really does hit harder`() {
        // playerStats is derived from the current ship in PortRoyaleGame, so the ship ladder has
        // to translate into a combat advantage or buying up means nothing in a fight.
        assertTrue(ShipTypes.GALLEON.minDamage > ShipTypes.SLOOP.minDamage)
        assertTrue(ShipTypes.GALLEON.maxDamage > ShipTypes.SLOOP.maxDamage)
        assertSame(ShipTypes.SLOOP, Player().ship)
    }

    private companion object {
        /** Sized so the maneuverability gaps under test sit ~8 standard errors apart. */
        const val ESCAPE_TRIALS = 3000
    }
}
