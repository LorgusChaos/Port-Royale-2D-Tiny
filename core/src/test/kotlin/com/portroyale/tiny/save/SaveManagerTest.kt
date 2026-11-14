package com.portroyale.tiny.save

import com.portroyale.tiny.economy.DEFAULT_LEG_MINUTES
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.ShipTypes
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.economy.Player
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers SaveManager's pure resolve* half - turning the flat JSON POJOs back into real game
 * objects - and the file itself, written and read through plain java.io in a temporary folder
 * (only finding the saves folder needs Gdx.files). These are the functions where a silent mistake
 * corrupts a player's save instead of crashing, which is exactly why they are worth pinning down.
 *
 * Backward compatibility with older save formats is a first-class concern here: every "a save
 * written before X" test is guarding a real upgrade path.
 */
class SaveManagerTest {

    // --- ship types ---

    @Test
    fun `a saved ship name resolves back to that class`() {
        for (shipType in ShipTypes.ALL) {
            assertSame(shipType, SaveManager.resolveShipType(shipType.name))
        }
    }

    @Test
    fun `an unknown ship name falls back to the starting sloop`() {
        // A renamed or removed class must not leave the player with no ship at all.
        assertSame(ShipTypes.SLOOP, SaveManager.resolveShipType("Man o' War"))
        assertSame(ShipTypes.SLOOP, SaveManager.resolveShipType(""))
    }

    @Test
    fun `a wreck cannot be restored from a save`() {
        // A saved longboat must come back a longboat, under either the current name or the one
        // old saves carry. It used to come back a Sloop - lose a battle, reload, and you had a
        // free ship; with a ten-minute autosave the defeat undid itself unprompted.
        assertSame(ShipTypes.LONGBOAT, SaveManager.resolveShipType("Longboat"))
        assertSame(ShipTypes.LONGBOAT, SaveManager.resolveShipType("Wreck"))
    }

    // --- cargo and reputation ---

    @Test
    fun `every good comes back from a save, present or not`() {
        val resolved = SaveManager.resolveCargo(mapOf("SUGAR" to 5, "SILVER" to 2))
        assertEquals(Good.entries.size, resolved.size)
        assertEquals(5, resolved[Good.SUGAR])
        assertEquals(2, resolved[Good.SILVER])
        assertEquals(0, resolved[Good.RUM], "a good missing from the save loads as empty")
    }

    @Test
    fun `a good added after a save was written loads as empty rather than missing`() {
        val resolved = SaveManager.resolveCargo(emptyMap())
        assertEquals(Good.entries.size, resolved.size)
        assertTrue(resolved.values.all { it == 0 })
    }

    @Test
    fun `an unknown good in the save is ignored`() {
        val resolved = SaveManager.resolveCargo(mapOf("GUNPOWDER" to 99, "RUM" to 3))
        assertEquals(Good.entries.size, resolved.size)
        assertEquals(3, resolved[Good.RUM])
    }

    @Test
    fun `every faction's standing comes back from a save`() {
        val resolved = SaveManager.resolveReputation(mapOf("SPAIN" to -40))
        assertEquals(Faction.entries.size, resolved.size)
        assertEquals(-40, resolved[Faction.SPAIN])
        assertEquals(0, resolved[Faction.ENGLAND])
    }

    @Test
    fun `the sub-point reputation owed from past sales survives a save`() {
        val resolved = SaveManager.resolveReputationRemainder(mapOf("SPAIN" to 0.75f, "FRANCE" to -0.5f))
        assertEquals(0.75f, resolved[Faction.SPAIN])
        assertEquals(-0.5f, resolved[Faction.FRANCE])
        assertNull(resolved[Faction.ENGLAND], "an untouched faction owes nothing")
    }

    @Test
    fun `a save from before M14 simply owes nothing`() {
        assertTrue(SaveManager.resolveReputationRemainder(emptyMap()).isEmpty())
    }

    @Test
    fun `a whole point sitting in the remainder is discarded, not credited`() {
        // Only a fraction belongs here; anything else is a corrupted or hand-edited save, and
        // honouring it would hand out reputation that was never earned.
        val resolved = SaveManager.resolveReputationRemainder(
            mapOf("SPAIN" to 40f, "ENGLAND" to -1f, "FRANCE" to 0.99f)
        )
        assertNull(resolved[Faction.SPAIN])
        assertNull(resolved[Faction.ENGLAND])
        assertEquals(0.99f, resolved[Faction.FRANCE])
    }

    // --- warehouses ---

    @Test
    fun `a warehouse comes back with its level, gold and goods`() {
        val data = GameSaveData().apply {
            warehouseLevels["Havana"] = 3
            warehouseGold["Havana"] = 2500
            warehouseGoods["Havana|SUGAR"] = 40
            warehouseGoods["Havana|SILVER"] = 7
        }

        val warehouse = SaveManager.resolveWarehouses(data)["Havana"]
        assertNotNull(warehouse)
        assertEquals(3, warehouse.level)
        assertEquals(2500, warehouse.gold)
        assertEquals(40, warehouse.goods[Good.SUGAR])
        assertEquals(7, warehouse.goods[Good.SILVER])
        assertEquals(0, warehouse.goods[Good.RUM])
    }

    @Test
    fun `warehouses in different ports stay separate`() {
        val data = GameSaveData().apply {
            warehouseLevels["Havana"] = 1
            warehouseLevels["Nassau"] = 2
            warehouseGoods["Havana|SUGAR"] = 10
            warehouseGoods["Nassau|SUGAR"] = 20
        }

        val resolved = SaveManager.resolveWarehouses(data)
        assertEquals(2, resolved.size)
        assertEquals(10, resolved["Havana"]!!.goods[Good.SUGAR])
        assertEquals(20, resolved["Nassau"]!!.goods[Good.SUGAR])
    }

    @Test
    fun `a port name containing a space survives the composite goods key`() {
        val data = GameSaveData().apply { warehouseGoods["Port Royal|RUM"] = 12 }
        assertEquals(12, SaveManager.resolveWarehouses(data)["Port Royal"]!!.goods[Good.RUM])
    }

    @Test
    fun `stored gold alone is enough to rebuild a warehouse`() {
        // Guards against a half-written save dropping the level entry.
        val data = GameSaveData().apply { warehouseGold["Havana"] = 900 }
        val warehouse = SaveManager.resolveWarehouses(data)["Havana"]
        assertNotNull(warehouse)
        assertEquals(900, warehouse.gold)
        assertEquals(1, warehouse.level, "a rebuilt warehouse defaults to level 1")
    }

    @Test
    fun `malformed warehouse entries are skipped instead of crashing the load`() {
        val data = GameSaveData().apply {
            warehouseGoods["no-separator"] = 5
            warehouseGoods["Havana|NOT_A_GOOD"] = 5
            warehouseGoods["Havana|SUGAR"] = 5
        }
        val resolved = SaveManager.resolveWarehouses(data)
        assertEquals(5, resolved["Havana"]!!.goods[Good.SUGAR])
        assertNull(resolved["no-separator"])
    }

    @Test
    fun `a save with no warehouses resolves to none`() {
        assertTrue(SaveManager.resolveWarehouses(GameSaveData()).isEmpty())
    }

    // --- loans ---

    @Test
    fun `an outstanding loan comes back intact`() {
        val data = GameSaveData().apply {
            hasLoan = true
            loanPrincipal = 3000
            loanRepayAmount = 4200
            loanTakenAtGameMinutes = 100f
            loanDeadlineGameMinutes = 14500f
            loanFactionName = "FRANCE"
            loanOverdue = true
        }

        val loan = SaveManager.resolveLoan(data)
        assertNotNull(loan)
        assertEquals(Faction.FRANCE, loan.faction)
        assertEquals(3000, loan.principal)
        assertEquals(4200, loan.repayAmount)
        assertEquals(100f, loan.takenAtGameMinutes)
        assertEquals(14500f, loan.deadlineGameMinutes)
        assertTrue(loan.overdue)
    }

    @Test
    fun `a debt-free save resolves to no loan`() {
        assertNull(SaveManager.resolveLoan(GameSaveData()))
    }

    @Test
    fun `a loan from an unknown faction is dropped rather than crashing`() {
        val data = GameSaveData().apply {
            hasLoan = true
            loanFactionName = "PORTUGAL"
        }
        assertNull(SaveManager.resolveLoan(data))
    }

    // --- captains ---

    @Test
    fun `a captain out on a route comes back mid-voyage`() {
        val data = GameSaveData().apply {
            captains.add(
                CaptainSaveEntry().apply {
                    name = "Isabela Vane"
                    tradeSense = 0.05f
                    caution = 0.7f
                    shipId = 4
                    portA = "Havana"
                    portB = "Nassau"
                    cargoGoodName = "RUM"
                    headingToB = false
                    cargoUnits = 30
                    cargoCostTotal = 3300
                    arrivesAtGameMinutes = 900f
                    legDurationGameMinutes = 120f
                    lastReport = "Bought 30x Rum"
                    totalProfit = 7700
                    sheltering = true
                    shelterPort = "Port Royal"
                }
            )
        }

        val captain = SaveManager.resolveCaptains(data).single()
        assertEquals("Isabela Vane", captain.name)
        assertEquals(0.05f, captain.tradeSense)
        assertEquals(0.7f, captain.caution)
        assertEquals(4, captain.shipId)
        assertEquals("Havana", captain.portA)
        assertEquals("Nassau", captain.portB)
        assertEquals(Good.RUM, captain.cargoGood)
        assertEquals(false, captain.headingToB)
        assertEquals(30, captain.cargoUnits)
        assertEquals(3300, captain.cargoCostTotal)
        assertEquals(900f, captain.arrivesAtGameMinutes)
        assertEquals(120f, captain.legDurationGameMinutes)
        assertEquals(7700, captain.totalProfit)
        assertTrue(captain.sheltering)
        assertEquals("Port Royal", captain.shelterPort)
    }

    @Test
    fun `an idle captain's missing ship is stored as minus one, not zero`() {
        // Ship ids start at 1, so 0 would silently look like a real ship.
        val data = GameSaveData().apply {
            captains.add(CaptainSaveEntry().apply { shipId = -1 })
        }
        val captain = SaveManager.resolveCaptains(data).single()
        assertNull(captain.shipId)
        assertTrue(captain.isIdle)
    }

    @Test
    fun `an empty hold comes back as no cargo rather than a bogus good`() {
        val data = GameSaveData().apply {
            captains.add(CaptainSaveEntry().apply { cargoGoodName = "" })
        }
        assertNull(SaveManager.resolveCaptains(data).single().cargoGood)
    }

    @Test
    fun `a hold whose good no longer exists is emptied, not left as a phantom`() {
        // A captain with cargoUnits > 0 and no cargoGood is finished: attemptSell bails on the
        // null good and attemptBuy is skipped because the hold reads as full, so they sail their
        // route forever without ever trading again. The units have to go with the good.
        val data = GameSaveData().apply {
            captains.add(
                CaptainSaveEntry().apply {
                    name = "Ghost"
                    shipId = 1
                    cargoGoodName = "GUNPOWDER"
                    cargoUnits = 30
                    cargoCostTotal = 4000
                }
            )
        }

        val captain = SaveManager.resolveCaptains(data).single()

        assertNull(captain.cargoGood)
        assertEquals(0, captain.cargoUnits, "phantom cargo would strand this captain permanently")
        assertEquals(0, captain.cargoCostTotal)
    }

    @Test
    fun `a hold with units but a blank good name is emptied too`() {
        val data = GameSaveData().apply {
            captains.add(CaptainSaveEntry().apply { cargoGoodName = ""; cargoUnits = 12; cargoCostTotal = 900 })
        }
        val captain = SaveManager.resolveCaptains(data).single()
        assertEquals(0, captain.cargoUnits)
        assertEquals(0, captain.cargoCostTotal)
    }

    @Test
    fun `a save written before leg durations existed gets a usable one`() {
        // legProgress divides by this, so a zero would put a NaN on the map.
        val data = GameSaveData().apply {
            captains.add(CaptainSaveEntry().apply { legDurationGameMinutes = 0f })
        }
        assertEquals(DEFAULT_LEG_MINUTES, SaveManager.resolveCaptains(data).single().legDurationGameMinutes)
    }

    @Test
    fun `a save with no captains resolves to an empty roster`() {
        assertTrue(SaveManager.resolveCaptains(GameSaveData()).isEmpty())
    }

    // --- owned ships ---

    @Test
    fun `an owned ship comes back with its own wear and upgrades`() {
        val data = GameSaveData().apply {
            ownedShips.add(
                OwnedShipSaveEntry().apply {
                    id = 4
                    name = "Sea Hawk"
                    shipTypeName = "Brigantine"
                    dockedAt = "Nassau"
                    currentHull = 90
                    fouling = 0.4f
                    cannonLevel = 2
                    oarLevel = 1
                    sailLevel = 3
                }
            )
        }

        val ship = SaveManager.resolveOwnedShips(data).single()
        assertEquals(4, ship.id)
        assertEquals("Sea Hawk", ship.name)
        assertSame(ShipTypes.BRIGANTINE, ship.shipType)
        assertEquals("Nassau", ship.dockedAt)
        assertEquals(90, ship.condition.currentHull)
        assertEquals(0.4f, ship.condition.fouling)
        assertEquals(2, ship.condition.cannonLevel)
        assertEquals(1, ship.condition.oarLevel)
        assertEquals(3, ship.condition.sailLevel)
    }

    @Test
    fun `a save written before per-ship hull tracking loads at full health`() {
        // currentHull 0 means "never saved", not "already sunk".
        val data = GameSaveData().apply {
            ownedShips.add(OwnedShipSaveEntry().apply { shipTypeName = "Galleon"; currentHull = 0 })
        }
        assertEquals(ShipTypes.GALLEON.maxHull, SaveManager.resolveOwnedShips(data).single().condition.currentHull)
    }

    @Test
    fun `each restored ship carries its own condition, not a shared one`() {
        val data = GameSaveData().apply {
            ownedShips.add(OwnedShipSaveEntry().apply { id = 1; shipTypeName = "Sloop"; fouling = 0.2f })
            ownedShips.add(OwnedShipSaveEntry().apply { id = 2; shipTypeName = "Sloop"; fouling = 0.8f })
        }
        val ships = SaveManager.resolveOwnedShips(data)
        assertEquals(0.2f, ships[0].condition.fouling)
        assertEquals(0.8f, ships[1].condition.fouling)
    }

    @Test
    fun `a restored ship's effective stats follow from its own condition`() {
        val data = GameSaveData().apply {
            ownedShips.add(OwnedShipSaveEntry().apply { shipTypeName = "Sloop"; sailLevel = 2 })
        }
        val ship = SaveManager.resolveOwnedShips(data).single()
        assertTrue(ship.effectiveSpeed > ShipTypes.SLOOP.speed, "sail upgrades should have survived the load")
    }

    @Test
    fun `a save with no fleet resolves to an empty roster`() {
        assertTrue(SaveManager.resolveOwnedShips(GameSaveData()).isEmpty())
    }

    // --- typed save names ---
    // The typed name becomes a file path, so anything that would leave the saves folder, break
    // the write on Windows, or overwrite the autosave must not survive (REVIEW.md, finding 16).

    private fun assertSavedAs(expected: String, typed: String) =
        assertEquals(SaveNameCheck.Ok(expected), SaveManager.checkSaveName(typed), "typed \"$typed\"")

    private fun assertRejected(typed: String) =
        assertTrue(SaveManager.checkSaveName(typed) is SaveNameCheck.Rejected, "typed \"$typed\" should be rejected")

    @Test
    fun `an ordinary save name is kept exactly as typed`() {
        assertSavedAs("Before the storm", "Before the storm")
        assertSavedAs("run_2-final", "run_2-final")
    }

    @Test
    fun `a cyrillic save name is kept exactly as typed`() {
        assertSavedAs("Перед штормом", "Перед штормом")
    }

    @Test
    fun `a save name cannot climb out of the saves folder`() {
        assertSavedAs("settings", "../settings")
        assertRejected("..")
    }

    @Test
    fun `a save name cannot reach into a subfolder`() {
        assertSavedAs("ab", "a/b")
        assertSavedAs("ab", "a\\b")
    }

    @Test
    fun `characters Windows forbids in file names are dropped`() {
        assertSavedAs("ab", "a:b")
        assertSavedAs("1230", "12:30")
        assertSavedAs("what", "what?")
        assertSavedAs("abc", "a*b|c")
        assertSavedAs("quoted", "\"<quoted>\"")
        assertRejected("???")
    }

    @Test
    fun `names Windows reserves for devices are rejected in any case`() {
        for (typed in listOf("CON", "con", "PRN", "AUX", "NUL", "COM1", "com9", "LPT1", "LPT9", "CON."))
            assertRejected(typed)
    }

    @Test
    fun `a name that only looks like a device name is fine`() {
        assertSavedAs("CONVOY", "CONVOY")
        assertSavedAs("COM10", "COM10")
    }

    @Test
    fun `a blank save name is rejected`() {
        assertRejected("")
        assertRejected("    ")
    }

    @Test
    fun `a manual save cannot take the autosave's name`() {
        assertRejected("autosave")
        assertRejected("AutoSave")
        assertRejected(" autosave ")
    }

    @Test
    fun `a save name never ends in a dot or a space`() {
        // Windows silently strips both, so "name." would land on top of "name".
        assertSavedAs("name", "name.")
        assertSavedAs("name", "name .")
        assertSavedAs("name", "  name  ")
    }

    // --- defaults ---

    @Test
    fun `a blank save shape has sane defaults`() {
        val data = GameSaveData()
        assertEquals(10000, data.gold)
        assertEquals("Sloop", data.shipTypeName)
        assertEquals(1, data.activeShipId)
        assertFalse(data.hasLoan)
        assertEquals(0f, data.gameClockMinutes)
        assertTrue(data.captains.isEmpty())
        assertTrue(data.ownedShips.isEmpty())
    }

    // --- the file: written whole or not at all, and a damaged one said so (REVIEW.md, finding 19) ---

    private fun tempDir(): File = kotlin.io.path.createTempDirectory("prt-save-test").toFile().also { it.deleteOnExit() }

    private fun saveText(captainName: String): String =
        SaveManager.toJson(SaveManager.snapshot(captainName, Player(), Vector2(10f, 20f), null))

    private fun loadedName(result: LoadResult): String {
        assertTrue(result is LoadResult.Loaded, "not loaded: ${(result as? LoadResult.Failed)?.reason}")
        return result.data.captainName
    }

    @Test
    fun `a save leaves no half-written file behind and reads back`() {
        val dir = tempDir()
        val file = File(dir, "voyage.json")

        SaveManager.writeSaveFile(file, saveText("Anne"))

        assertEquals(listOf("voyage.json"), dir.list()!!.sorted())
        assertEquals("Anne", loadedName(SaveManager.readSaveFile(file)))
    }

    @Test
    fun `saving over a save keeps the one before it as a backup`() {
        val dir = tempDir()
        val file = File(dir, "voyage.json")

        SaveManager.writeSaveFile(file, saveText("First"))
        SaveManager.writeSaveFile(file, saveText("Second"))

        assertEquals("Second", loadedName(SaveManager.readSaveFile(file)))
        assertEquals(listOf("voyage.json", "voyage.json.bak"), dir.list()!!.sorted())
        assertEquals("First", SaveManager.fromJson(File(dir, "voyage.json.bak").readText(java.nio.charset.Charset.defaultCharset())).captainName)
    }

    @Test
    fun `a cut-off save with nothing to fall back on is refused, not thrown`() {
        val file = File(tempDir(), "voyage.json")
        file.writeText(saveText("Anne").take(40))

        val result = SaveManager.readSaveFile(file)

        assertTrue(result is LoadResult.Failed, "a cut-off file loaded")
    }

    @Test
    fun `a cut-off save falls back to the copy before it and says so`() {
        val file = File(tempDir(), "voyage.json")
        SaveManager.writeSaveFile(file, saveText("First"))
        SaveManager.writeSaveFile(file, saveText("Second"))
        file.writeText(saveText("Second").take(40))

        val result = SaveManager.readSaveFile(file)

        assertEquals("First", loadedName(result))
        assertTrue((result as LoadResult.Loaded).fromBackup, "the fallback was not owned up to")
    }

    // --- the castaway is part of the save (REVIEW.md, finding 12) ---

    @Test
    fun `a passenger's destination survives a save and a load`() {
        val data = SaveManager.fromJson(SaveManager.toJson(SaveManager.snapshot("T", Player(), Vector2(), "Havana")))
        assertEquals("Havana", SaveManager.resolvePassenger(data.passengerPort, listOf("Havana", "Nassau")))
    }

    @Test
    fun `a save written before passengers were saved has nobody aboard`() {
        assertNull(SaveManager.resolvePassenger(GameSaveData().passengerPort, listOf("Havana")))
        assertNull(SaveManager.resolvePassenger(SaveManager.snapshot("T", Player(), Vector2(), null).passengerPort, listOf("Havana")))
    }

    @Test
    fun `a passenger bound for a port the game no longer has is not aboard`() {
        assertNull(SaveManager.resolvePassenger("Atlantis", listOf("Havana", "Nassau")))
    }

    // --- the active hull comes back afloat and within bounds (REVIEW.md, finding 14) ---

    @Test
    fun `an active hull saved at zero or below loads afloat`() {
        assertEquals(1, SaveManager.resolveActiveHull(0, 100))
        assertEquals(1, SaveManager.resolveActiveHull(-5, 100))
    }

    @Test
    fun `an active hull saved above the ship's maximum loads at the maximum`() {
        assertEquals(100, SaveManager.resolveActiveHull(250, 100))
    }

    @Test
    fun `an active hull within bounds loads as saved`() {
        assertEquals(42, SaveManager.resolveActiveHull(42, 100))
    }

    // --- an edited or damaged save stays within bounds (REVIEW.md, finding 38) ---

    @Test
    fun `a docked ship's condition outside its bounds comes back within them`() {
        val data = GameSaveData()
        data.ownedShips.add(OwnedShipSaveEntry().apply {
            id = 2; name = "Runner"; shipTypeName = "Cutter"; dockedAt = "Havana"
            currentHull = 9999; fouling = 7f; cannonLevel = 99; oarLevel = -3; sailLevel = 50
        })

        val ship = SaveManager.resolveOwnedShips(data).single()

        assertEquals(ShipTypes.CUTTER.maxHull, ship.condition.currentHull)
        assertEquals(1f, ship.condition.fouling)
        assertEquals(com.portroyale.tiny.ships.MAX_UPGRADE_LEVEL, ship.condition.cannonLevel)
        assertEquals(0, ship.condition.oarLevel)
        assertEquals(com.portroyale.tiny.ships.MAX_UPGRADE_LEVEL, ship.condition.sailLevel)
    }

    @Test
    fun `an active ship id that is not in the fleet falls back to the first ship`() {
        val data = GameSaveData()
        data.activeShipId = 9
        for (shipId in listOf(3, 5)) {
            data.ownedShips.add(OwnedShipSaveEntry().apply { id = shipId; name = "S$shipId"; shipTypeName = "Sloop"; dockedAt = "Havana" })
        }

        assertEquals(3, SaveManager.resolveActiveShipId(data, SaveManager.resolveOwnedShips(data)))
    }

    @Test
    fun `an active ship id that is in the fleet is kept`() {
        val data = GameSaveData()
        data.activeShipId = 5
        for (shipId in listOf(3, 5)) {
            data.ownedShips.add(OwnedShipSaveEntry().apply { id = shipId; name = "S$shipId"; shipTypeName = "Sloop"; dockedAt = "Havana" })
        }

        assertEquals(5, SaveManager.resolveActiveShipId(data, SaveManager.resolveOwnedShips(data)))
    }
}
