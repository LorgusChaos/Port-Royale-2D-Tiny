package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.FleetSimulation
import com.portroyale.tiny.economy.OwnedShip
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.Ports

// PortFrameArt.TAVERN leaves 832x577 of clear parchment - the widest panel of the four and the
// shortest, since the shelves eat into the top and bottom rather than the sides. The budget:
// 48 for the crew line, 210 for a row of candidate cards, 36 for the caption, 44 for the way out,
// and what is left (about 240) for the captains already at sea.
private const val CARD_W = 156f
/** A card is 156 wide and the panel's own lettering is sized for a full-width table row, so every
 *  line on a card is scaled down and kept short enough not to run into the card beside it. Scene2D
 *  does not clip a Label to its cell: text that does not fit simply draws over its neighbour, which
 *  is exactly what three candidates' names did. */
private const val CARD_TEXT_SCALE = 0.72f
private const val HEADER_TEXT_SCALE = 0.82f
private const val CARD_PAD = 5f
private const val CARDS_PER_ROW = 5
private const val PORTRAIT = 56f
private const val HEADER_H = 48f
private const val CAPTION_H = 36f
private const val BUTTON_H = 42f
private const val REPORT_W = 690f
private const val RECALL_W = 120f

/** Tavern sub-screen: crew an idle ship (see FleetDockUI - ships are bought/boarded/named at the
 *  Docks, but crewed with people here) with either a freshly-rolled candidate (3 offered at a
 *  time, paid) or an already-hired captain who's currently idle (free - they're already on the
 *  payroll). Hiring is only possible with at least one idle ship in this port. Also lists every
 *  active captain with their last report and a Recall button. The player only sets the ship and
 *  the two ports - which good to carry is the captain's own call (see FleetSimulation.attemptBuy),
 *  so there's no good picker here.
 *
 *  Drawn inside the tavern's own painted shopfront. That picture is the one frame of the four with
 *  no (X) painted on it - a lantern hangs where the others carry one - so this screen keeps a
 *  "Back" button of its own at the foot of the panel (see [PortFrameArt.hasPaintedClose]).
 *  See economy/Captain.kt, FleetSimulation, and Project design/02-fleet-and-captains.md. */
class TavernUI(
    private val player: Player,
    private val distanceBetween: (String, String) -> Float,
    private val onBack: () -> Unit
) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val frame = PortFrame(skin, PortFrameArt.TAVERN) { leave() }

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
        stage.addActor(frame.group)
        buildLayout()
        layoutFrame()
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
        layoutFrame()
    }

    fun dispose() {
        stage.dispose()
        skin.dispose()
    }

    private fun layoutFrame() = frame.layout(stage.viewport.worldWidth, stage.viewport.worldHeight)

    private fun leave() {
        AudioManager.playClick()
        onBack()
    }

    private fun idleShipsHere(): List<OwnedShip> =
        player.ownedShips.filter { it.id != player.activeShipId && it.dockedAt == currentPort && FleetSimulation.isShipFree(player, it.id) }

    private fun buildLayout() {
        val panel = Table()
        panel.top().left()
        // Both of these are filled and emptied by refresh(); without an alignment of their own a
        // Table centres its contents, which floats a single line of text into the middle of the
        // parchment instead of lining it up under the caption above it.
        candidatesPanel.left()
        activePanel.left()

        noShipLabel = frame.bodyLabel()
        noShipLabel.setText("No idle ship here - buy or board one at the Docks first.")
        panel.add(noShipLabel).growX().height(HEADER_H).left().row()

        hireHeader = Table()
        hireHeader.add(frame.bodyLabel().apply { setText("Crew:") }).padRight(8f)
        shipButton = frame.button("", fontScale = HEADER_TEXT_SCALE) {
            AudioManager.playClick()
            val ships = idleShipsHere()
            if (ships.isNotEmpty()) {
                val idx = ships.indexOfFirst { it.id == selectedShipId }
                selectedShipId = ships[(idx + 1) % ships.size].id
                refresh()
            }
        }
        hireHeader.add(shipButton).width(180f).height(BUTTON_H).padRight(18f)
        hireHeader.add(frame.bodyLabel().apply { setText("Route:") }).padRight(8f)
        routeButton = frame.button("", fontScale = HEADER_TEXT_SCALE) {
            AudioManager.playClick()
            val others = Ports.ALL.map { it.name }.filter { it != currentPort }
            val idx = others.indexOf(selectedPortB)
            selectedPortB = others[(idx + 1) % others.size]
            refresh()
        }
        hireHeader.add(routeButton).width(320f).height(BUTTON_H)
        panel.add(hireHeader).height(HEADER_H).left().row()

        panel.add(candidatesPanel).growX().left().padTop(6f).row()

        panel.add(frame.bodyLabel().apply { setText("Your captains:") })
            .height(CAPTION_H).left().padTop(10f).row()
        panel.add(activePanel).growX().left().row()

        // The way back lives at the foot of the panel because this frame has no painted (X).
        panel.add().grow().row()
        panel.add(frame.button("Back") { leave() }).width(180f).height(BUTTON_H).left()

        frame.content.add(panel).grow()
    }

    private fun refresh() {
        frame.setTitle("Tavern")
        frame.setStatus(currentPort, player.gold)

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
            val offered = freshCandidates.map { it to true } + player.captains.filter { it.isIdle }.map { it to false }
            // Wrapped by hand: Scene2D has no flow layout, and three fresh candidates plus a
            // growing bench of idle captains would otherwise run off the side of the parchment.
            for ((index, entry) in offered.withIndex()) {
                candidatesPanel.add(candidateCard(entry.first, isFresh = entry.second))
                    .top().width(CARD_W).pad(CARD_PAD)
                if (index % CARDS_PER_ROW == CARDS_PER_ROW - 1) candidatesPanel.row()
            }
        }

        activePanel.clear()
        val active = player.captains.filter { !it.isIdle }
        if (active.isEmpty()) {
            activePanel.add(frame.bodyLabel().apply { setText("No captains out on a route.") })
                .left().colspan(2).row()
        }
        for (captain in active) {
            val ship = player.ownedShips.find { it.id == captain.shipId }
            activePanel.add(frame.bodyLabel().apply {
                setText("${captain.name} (${ship?.name ?: "?"}) - ${captain.portA} <-> ${captain.portB}")
            }).left().colspan(2).padTop(8f).row()

            val statusLabel = frame.bodyLabel()
            statusLabel.setText("${captain.lastReport}  Lifetime profit: ${captain.totalProfit}g")
            statusLabel.setWrap(true)
            activePanel.add(statusLabel).left().width(REPORT_W).padBottom(4f)

            activePanel.add(frame.button("Recall") {
                AudioManager.playClick()
                FleetSimulation.recall(player, captain)
                refresh()
            }).width(RECALL_W).height(BUTTON_H).padBottom(4f).row()
        }
    }

    /** One line on a candidate's card: scaled down to the card's width and allowed to wrap, so a
     *  long name takes two lines of its own card instead of a stripe across its neighbours'. */
    private fun cardLabel(text: String) = frame.bodyLabel(Align.center).apply {
        setText(text)
        setFontScale(CARD_TEXT_SCALE)
        setWrap(true)
    }

    private fun candidateCard(captain: Captain, isFresh: Boolean): Table {
        val card = Table()
        card.top()

        card.add(Image(TextureRegionDrawable(TextureRegion(IconFactory.portraitFor(captain.name.hashCode())))))
            .size(PORTRAIT).padBottom(6f).row()
        card.add(cardLabel(captain.name)).width(CARD_W - 12f).padBottom(2f).row()

        val pct = (captain.tradeSense * 100).toInt()
        val sign = if (pct >= 0) "+" else ""
        card.add(cardLabel("Trade $sign$pct%")).width(CARD_W - 12f).padBottom(2f).row()
        val cautionDesc = when {
            captain.caution < 0.4f -> "bold"
            captain.caution < 0.65f -> "steady"
            else -> "careful"
        }
        card.add(cardLabel(cautionDesc)).width(CARD_W - 12f).padBottom(8f).row()

        val cost = Captain.hireCost(player.captains.size)
        val hireButton = frame.button(if (isFresh) "Hire ${cost}g" else "Assign", fontScale = CARD_TEXT_SCALE) {
            val shipId = selectedShipId ?: return@button
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
        hireButton.touchable = if (!isFresh || player.gold >= cost) Touchable.enabled else Touchable.disabled
        hireButton.color.a = if (!isFresh || player.gold >= cost) 1f else 0.45f
        card.add(hireButton).width(CARD_W - 16f).height(BUTTON_H).row()

        return card
    }
}
