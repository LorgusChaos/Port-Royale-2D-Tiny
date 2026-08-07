package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.FleetSimulation
import com.portroyale.tiny.economy.OwnedShip
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.Ports

/** Tavern sub-screen: crew an idle ship (see FleetDockUI - ships are bought/boarded/named at the
 *  Docks, but crewed with people here) with either a freshly-rolled candidate (3 offered at a
 *  time, paid) or an already-hired captain who's currently idle (free - they're already on the
 *  payroll). Hiring is only possible with at least one idle ship in this port. Also lists every
 *  active captain with their last report and a Recall button. The player only sets the ship and
 *  the two ports - which good to carry is the captain's own call (see FleetSimulation.attemptBuy),
 *  so there's no good picker here. Reached from PortHubUI; "Back" returns there. See
 *  economy/Captain.kt, FleetSimulation, and Project design/02-fleet-and-captains.md. */
class TavernUI(
    private val player: Player,
    private val distanceBetween: (String, String) -> Float,
    private val onBack: () -> Unit
) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
    private lateinit var hireHeader: Table
    private lateinit var shipButton: TextButton
    private lateinit var routeButton: TextButton
    private lateinit var noShipLabel: Label
    private val candidatesPanel = Table()
    private val activePanel = Table()
    private var currentPort = ""
    private var selectedShipId: Int? = null
    private var selectedPortB = ""
    private var freshCandidates = listOf<Captain>()

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        currentPort = portName
        if (selectedPortB.isEmpty() || selectedPortB == currentPort) {
            selectedPortB = Ports.ALL.map { it.name }.first { it != currentPort }
        }
        freshCandidates = List(3) { Captain.rollCandidate() }
        refresh()
    }

    fun render(delta: Float) {
        stage.viewport.apply()
        stage.act(delta)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        UiScale.applyResize(stage, width, height)
    }

    fun dispose() {
        stage.dispose()
        skin.dispose()
    }

    private fun idleShipsHere(): List<OwnedShip> =
        player.ownedShips.filter { it.id != player.activeShipId && it.dockedAt == currentPort && FleetSimulation.isShipFree(player, it.id) }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(2).left().padBottom(12f).row()

        goldLabel = Label("", skin)
        panel.add(goldLabel).colspan(2).left().padBottom(20f).row()

        noShipLabel = Label("No idle ship here - buy or board one at the Docks first.", skin)
        panel.add(noShipLabel).colspan(2).left().padBottom(12f).row()

        hireHeader = Table()
        hireHeader.add(Label("Crew:", skin)).left().padRight(6f)
        shipButton = TextButton("", skin)
        shipButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                val ships = idleShipsHere()
                if (ships.isEmpty()) return
                val idx = ships.indexOfFirst { it.id == selectedShipId }
                selectedShipId = ships[(idx + 1) % ships.size].id
                refresh()
            }
        })
        hireHeader.add(shipButton).width(150f).pad(4f)

        hireHeader.add(Label("Route:", skin)).left().padRight(6f)
        routeButton = TextButton("", skin)
        routeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                val others = Ports.ALL.map { it.name }.filter { it != currentPort }
                val idx = others.indexOf(selectedPortB)
                selectedPortB = others[(idx + 1) % others.size]
                refresh()
            }
        })
        hireHeader.add(routeButton).width(150f).pad(4f).row()
        panel.add(hireHeader).colspan(2).left().padBottom(12f).row()

        panel.add(candidatesPanel).colspan(2).left().width(600f).padBottom(16f).row()

        panel.add(Label("Your captains:", skin)).colspan(2).left().padTop(8f).padBottom(4f).row()
        panel.add(activePanel).colspan(2).left().width(600f).padBottom(16f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(2).left().width(140f)

        root.add(panel)
    }

    private fun refresh() {
        titleLabel.setText("$currentPort - Tavern")
        goldLabel.setText("Gold: ${player.gold}")

        val ships = idleShipsHere()
        val hasShip = ships.isNotEmpty()
        if (selectedShipId == null || ships.none { it.id == selectedShipId }) {
            selectedShipId = ships.firstOrNull()?.id
        }
        noShipLabel.isVisible = !hasShip
        hireHeader.isVisible = hasShip
        candidatesPanel.isVisible = hasShip

        if (hasShip) {
            val ship = ships.first { it.id == selectedShipId }
            shipButton.setText(ship.name)
            routeButton.setText("$currentPort -> $selectedPortB")

            candidatesPanel.clear()
            val idleExisting = player.captains.filter { it.isIdle }
            for (candidate in freshCandidates) {
                candidatesPanel.add(candidateCard(candidate, isFresh = true)).top().pad(6f)
            }
            for (existing in idleExisting) {
                candidatesPanel.add(candidateCard(existing, isFresh = false)).top().pad(6f)
            }
        }

        activePanel.clear()
        val active = player.captains.filter { !it.isIdle }
        if (active.isEmpty()) {
            activePanel.add(Label("No captains out on a route.", skin)).left().row()
        }
        for (captain in active) {
            val ship = player.ownedShips.find { it.id == captain.shipId }
            val nameLabel = Label(
                "${captain.name} (${ship?.name ?: "?"}) - ${captain.portA} <-> ${captain.portB}",
                skin
            )
            nameLabel.setFontScale(1.0f)
            activePanel.add(nameLabel).left().colspan(2).padTop(6f).row()

            val statusLabel = Label("${captain.lastReport}  Lifetime profit: ${captain.totalProfit}g", skin)
            statusLabel.setWrap(true)
            activePanel.add(statusLabel).left().width(480f).padBottom(4f)

            val recallButton = TextButton("Recall", skin)
            recallButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    AudioManager.playClick()
                    FleetSimulation.recall(player, captain)
                    refresh()
                }
            })
            activePanel.add(recallButton).width(90f).padBottom(4f).row()
        }
    }

    private fun candidateCard(captain: Captain, isFresh: Boolean): Table {
        val card = Table()
        card.background = skin.getDrawable("field-frame")
        card.pad(8f)

        val portrait = Image(TextureRegionDrawable(TextureRegion(IconFactory.portraitFor(captain.name.hashCode()))))
        card.add(portrait).size(48f, 48f).padBottom(4f).row()

        card.add(Label(captain.name, skin)).padBottom(2f).row()
        val pct = (captain.tradeSense * 100).toInt()
        val sign = if (pct >= 0) "+" else ""
        card.add(Label("Trade sense: $sign$pct%", skin)).padBottom(2f).row()
        val cautionDesc = when {
            captain.caution < 0.4f -> "bold"
            captain.caution < 0.65f -> "steady"
            else -> "careful"
        }
        card.add(Label("Caution: $cautionDesc", skin)).padBottom(6f).row()

        val cost = Captain.hireCost(player.captains.size)
        val hireButton = TextButton(if (isFresh) "Hire (${cost}g)" else "Assign", skin)
        hireButton.touchable = if (!isFresh || player.gold >= cost) Touchable.enabled else Touchable.disabled
        hireButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val shipId = selectedShipId ?: return
                val ok = if (isFresh) {
                    FleetSimulation.hire(player, captain, shipId, currentPort, selectedPortB, GameClock.totalGameMinutes, distanceBetween)
                } else {
                    FleetSimulation.assign(player, captain, shipId, currentPort, selectedPortB, GameClock.totalGameMinutes, distanceBetween)
                }
                if (ok) {
                    AudioManager.playCoins()
                    if (isFresh) freshCandidates = List(3) { Captain.rollCandidate() }
                    refresh()
                }
            }
        })
        card.add(hireButton).width(110f)

        return card
    }
}
