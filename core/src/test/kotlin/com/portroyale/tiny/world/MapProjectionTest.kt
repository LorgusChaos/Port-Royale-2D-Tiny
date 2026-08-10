package com.portroyale.tiny.world

import com.portroyale.tiny.factions.Faction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapProjectionTest {

    // The documented bounding box of the source relief map the projection is calibrated against.
    private val north = 32f
    private val south = 6f
    private val west = -99f
    private val east = -58f

    @Test
    fun `the north-west corner of the source map projects to the top-left of the world`() {
        val corner = MapProjection.project(north, west)
        assertEquals(0f, corner.x, 0.01f)
        assertEquals(MapProjection.MAP_HEIGHT, corner.y, 0.01f)
    }

    @Test
    fun `the south-east corner projects to the bottom-right of the world`() {
        val corner = MapProjection.project(south, east)
        assertEquals(MapProjection.MAP_WIDTH, corner.x, 0.01f)
        assertEquals(0f, corner.y, 0.01f)
    }

    @Test
    fun `the middle of the box lands in the middle of the world`() {
        val middle = MapProjection.project((north + south) / 2f, (west + east) / 2f)
        assertEquals(MapProjection.MAP_WIDTH / 2f, middle.x, 0.01f)
        assertEquals(MapProjection.MAP_HEIGHT / 2f, middle.y, 0.01f)
    }

    @Test
    fun `sailing east increases x`() {
        assertTrue(MapProjection.project(20f, -70f).x > MapProjection.project(20f, -80f).x)
    }

    @Test
    fun `sailing north increases y`() {
        // World-space y points up, the opposite of the source image's top-down pixel rows - the
        // flip that makes ports render right side up.
        assertTrue(MapProjection.project(25f, -70f).y > MapProjection.project(15f, -70f).y)
    }

    @Test
    fun `the projection is linear in both axes`() {
        val a = MapProjection.project(10f, -90f)
        val b = MapProjection.project(20f, -80f)
        val c = MapProjection.project(30f, -70f)
        assertEquals(b.x - a.x, c.x - b.x, 0.01f)
        assertEquals(b.y - a.y, c.y - b.y, 0.01f)
    }

    @Test
    fun `every real port lands inside the world bounds`() {
        for (port in Ports.ALL) {
            val position = MapProjection.project(port.lat, port.lon)
            assertTrue(
                position.x in 0f..MapProjection.MAP_WIDTH,
                "${port.name} projects off the map horizontally (x=${position.x})"
            )
            assertTrue(
                position.y in 0f..MapProjection.MAP_HEIGHT,
                "${port.name} projects off the map vertically (y=${position.y})"
            )
        }
    }

    @Test
    fun `no two ports land on the same spot`() {
        val positions = Ports.ALL.map { MapProjection.project(it.lat, it.lon) }
        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                assertTrue(
                    positions[i].dst(positions[j]) > 1f,
                    "${Ports.ALL[i].name} and ${Ports.ALL[j].name} project to the same point"
                )
            }
        }
    }

    @Test
    fun `real-world geography survives the projection`() {
        // Havana is north-west of Bridgetown in reality; it must be up and to the left on screen.
        val havana = MapProjection.project(23.13f, -82.35f)
        val bridgetown = MapProjection.project(13.1f, -59.62f)
        assertTrue(havana.x < bridgetown.x)
        assertTrue(havana.y > bridgetown.y)
    }
}

class PortsTest {

    @Test
    fun `the map has the full set of trading ports`() {
        assertEquals(28, Ports.ALL.size)
    }

    @Test
    fun `no colonial power is left with too few harbours to court`() {
        // M14 reputation runs off each faction's share of sales revenue, and a power holding one
        // port could only be kept friendly by pushing a quarter of all trade through that single
        // harbour - which made two of the four sink structurally rather than by the player's
        // choice. Three is the floor at which courting a power becomes a routing decision.
        for (faction in Faction.entries) {
            val held = Ports.ALL.count { it.faction == faction }
            assertTrue(held >= 3, "$faction holds only $held port(s)")
        }
    }

    @Test
    fun `ports are far enough apart to be told apart`() {
        // What this guards against is a second port inside the first one's arrival trigger -
        // Kingston, for instance, sits a couple of kilometres from Port Royal and would be the
        // same dot and the same arrival. Three arrival radii is comfortably clear of that.
        // Deliberately not stricter: Curacao and Bonaire are ~50km apart in reality and both
        // genuinely Dutch, and a rounder threshold would have thrown out real geography to no
        // purpose. Labels crowding each other on screen is a rendering problem, and belongs to
        // the map/UI pass in M16 rather than to the port data.
        val minimumSeparation = 48f
        val positions = Ports.ALL.map { it.name to MapProjection.project(it.lat, it.lon) }
        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                val distance = positions[i].second.dst(positions[j].second)
                assertTrue(
                    distance > minimumSeparation,
                    "${positions[i].first} and ${positions[j].first} are only $distance apart"
                )
            }
        }
    }

    @Test
    fun `port names are unique`() {
        assertEquals(Ports.ALL.size, Ports.ALL.map { it.name }.distinct().size)
    }

    @Test
    fun `no port is left unnamed`() {
        assertTrue(Ports.ALL.all { it.name.isNotBlank() })
    }

    @Test
    fun `every port sits inside the map's geographic bounding box`() {
        for (port in Ports.ALL) {
            assertTrue(port.lat in 6f..32f, "${port.name} is outside the map's latitude range")
            assertTrue(port.lon in -99f..-58f, "${port.name} is outside the map's longitude range")
        }
    }

    @Test
    fun `every faction holds at least one port`() {
        // A faction with no ports would have nowhere to trade with, and its AI traders nowhere
        // to sail between.
        val owners = Ports.ALL.map { it.faction }.toSet()
        assertEquals(Faction.entries.toSet(), owners)
    }

    @Test
    fun `spain still holds more of the coastline than anyone else`() {
        val byFaction = Faction.entries.associateWith { faction -> Ports.ALL.count { it.faction == faction } }
        val spanish = byFaction.getValue(Faction.SPAIN)
        for ((faction, held) in byFaction) {
            if (faction == Faction.SPAIN) continue
            assertTrue(spanish > held, "Spain ($spanish) should out-hold $faction ($held)")
        }
    }

    @Test
    fun `at least two ports share a faction so faction traders have a route`() {
        // AI trade/military ships sail between ports of their OWN faction - a faction with a
        // single port would leave them with nowhere to go.
        val routable = Faction.entries.filter { faction -> Ports.ALL.count { it.faction == faction } >= 2 }
        assertTrue(routable.isNotEmpty())
    }
}
