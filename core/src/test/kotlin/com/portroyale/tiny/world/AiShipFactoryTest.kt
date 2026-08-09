package com.portroyale.tiny.world

import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.ShipTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiShipFactoryTest {

    private fun somewhere() = Vector2(100f, 100f)

    @Test
    fun `every sprite an AI ship can spawn with is preloaded`() {
        // PortRoyaleGame preloads exactly ALL_SPRITES; a path produced here but missing from that
        // list is a ship that renders as nothing at runtime.
        for (faction in Faction.entries) {
            assertTrue(
                AiShipFactory.trade(faction, somewhere()).spritePath in AiShipFactory.ALL_SPRITES,
                "$faction trader sprite is not preloaded"
            )
            assertTrue(
                AiShipFactory.military(faction, somewhere()).spritePath in AiShipFactory.ALL_SPRITES,
                "$faction warship sprite is not preloaded"
            )
        }
        repeat(300) {
            val pirate = AiShipFactory.pirate(somewhere())
            assertTrue(pirate.spritePath in AiShipFactory.ALL_SPRITES, "pirate tier ${pirate.tier} is not preloaded")
        }
    }

    @Test
    fun `the preload list has no dead entries`() {
        val produced = buildSet {
            Faction.entries.forEach {
                add(AiShipFactory.trade(it, somewhere()).spritePath)
                add(AiShipFactory.military(it, somewhere()).spritePath)
            }
            repeat(300) { add(AiShipFactory.pirate(somewhere()).spritePath) }
        }
        assertEquals(AiShipFactory.ALL_SPRITES.toSet(), produced, "ALL_SPRITES has drifted from what the factory makes")
    }

    @Test
    fun `the preload list holds no duplicates`() {
        assertEquals(AiShipFactory.ALL_SPRITES.size, AiShipFactory.ALL_SPRITES.distinct().size)
    }

    @Test
    fun `each faction's ships fly that faction's colours`() {
        val tradeSprites = Faction.entries.map { AiShipFactory.trade(it, somewhere()).spritePath }
        assertEquals(Faction.entries.size, tradeSprites.distinct().size, "two factions share a trader sprite")
    }

    @Test
    fun `a trader is easy prey and a warship is not`() {
        val trader = AiShipFactory.trade(Faction.SPAIN, somewhere())
        val warship = AiShipFactory.military(Faction.SPAIN, somewhere())

        assertTrue(warship.maxHull > trader.maxHull)
        assertTrue(warship.maxDamage > trader.maxDamage)
        assertTrue(warship.goldReward > trader.goldReward, "the harder fight should pay better")
        assertTrue(warship.tier > trader.tier)
    }

    @Test
    fun `an AI ship sails at the speed of the hull it is drawn as`() {
        // Speed comes from the matching ShipTypes tier, not a separate hand-tuned number.
        val trader = AiShipFactory.trade(Faction.ENGLAND, somewhere())
        assertEquals(ShipTypes.ALL[trader.tier - 1].speed, trader.speed)

        val warship = AiShipFactory.military(Faction.ENGLAND, somewhere())
        assertEquals(ShipTypes.ALL[warship.tier - 1].speed, warship.speed)
    }

    @Test
    fun `pirates roll across the whole tier ladder`() {
        val tiers = (1..500).map { AiShipFactory.pirate(somewhere()).tier }.toSet()
        assertEquals((1..5).toSet(), tiers)
    }

    @Test
    fun `a pirate's stats and payout rise with its tier`() {
        val byTier = mutableMapOf<Int, AiShip>()
        repeat(500) { AiShipFactory.pirate(somewhere()).let { byTier.putIfAbsent(it.tier, it) } }

        val sorted = byTier.toSortedMap().values.toList()
        for (i in 1 until sorted.size) {
            assertTrue(sorted[i].maxHull > sorted[i - 1].maxHull, "tier ${sorted[i].tier} is no tougher")
            assertTrue(sorted[i].maxDamage > sorted[i - 1].maxDamage, "tier ${sorted[i].tier} hits no harder")
            assertTrue(sorted[i].goldReward > sorted[i - 1].goldReward, "tier ${sorted[i].tier} pays no better")
        }
    }

    @Test
    fun `pirates answer to no flag`() {
        repeat(50) { assertNull(AiShipFactory.pirate(somewhere()).faction) }
    }

    @Test
    fun `a ship's name tells the player what they are looking at`() {
        assertEquals("Pirate Ship", AiShipFactory.pirate(somewhere()).displayName)
        assertEquals("Spain Trader", AiShipFactory.trade(Faction.SPAIN, somewhere()).displayName)
        assertEquals("Netherlands Warship", AiShipFactory.military(Faction.NETHERLANDS, somewhere()).displayName)
    }

    @Test
    fun `every AI ship can actually fight and move`() {
        val ships = Faction.entries.flatMap {
            listOf(AiShipFactory.trade(it, somewhere()), AiShipFactory.military(it, somewhere()))
        } + (1..50).map { AiShipFactory.pirate(somewhere()) }

        for (ship in ships) {
            assertTrue(ship.maxHull > 0, "${ship.displayName} has no hull")
            assertTrue(ship.speed > 0f, "${ship.displayName} cannot move")
            assertTrue(ship.minDamage > 0, "${ship.displayName} cannot fight")
            assertTrue(ship.minDamage <= ship.maxDamage, "${ship.displayName} damage range is inverted")
            assertTrue(ship.goldReward > 0, "${ship.displayName} is worth nothing to sink")
            assertTrue(ship.tier in 1..5, "${ship.displayName} has no matching sprite tier")
        }
    }

    @Test
    fun `a newly spawned ship is not chasing anyone`() {
        val ship = AiShipFactory.pirate(somewhere())
        assertFalse(ship.isPursuing)
        assertNull(ship.pursuingCaptain)
        assertEquals(0f, ship.giveUpCooldown, "a fresh ship is free to engage immediately")
        assertTrue(ship.path.isEmpty())
        assertEquals(0, ship.pathIndex)
    }

    @Test
    fun `a spawned ship keeps the position it was placed at`() {
        val position = Vector2(1234f, 5678f)
        assertEquals(position, AiShipFactory.pirate(position).position)
    }
}
