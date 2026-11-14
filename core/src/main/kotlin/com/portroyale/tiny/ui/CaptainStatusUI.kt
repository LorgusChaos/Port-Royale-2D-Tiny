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
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.world.Ports

/** Popup opened by clicking a fleet ship's icon on the map while sailing (see
 *  PortRoyaleGame.drawFleetShips/openCaptainStatus) - the ship and captain, current cargo, route,
 *  lifetime profit, a Recall button, and a way to redirect the route's destination. What to carry
 *  is the captain's own call (see FleetSimulation.attemptBuy) - the player only redirects ports,
 *  and only while [FleetSimulation.canRedirect] allows it (see the note on [refresh]): changing
 *  portB while still outbound would make the captain "arrive" somewhere the ship rendered on
 *  screen never actually sailed to. Not tied to a specific port screen - opened over the sailing
 *  view itself, closed back to it. */
class CaptainStatusUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var portraitImage: Image
    private lateinit var infoLabel: Label
    private lateinit var routeButton: TextButton
    private lateinit var confirmRouteButton: TextButton
    private lateinit var routeHint: Label
    private var captain: Captain? = null
    private lateinit var recallButton: TextButton
    private var pendingPortB = ""
    /** Why the last "Confirm New Route" was refused; empty otherwise. */
    private var refusal = ""
    /** True after the first Recall press with cargo aboard; the second press recalls. */
    private var recallArmed = false
    private var refreshTimer = 0f

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(target: Captain) {
        captain = target
        pendingPortB = target.portB
        refusal = ""
        recallArmed = false
        refreshTimer = 0f
        refresh()
    }

    fun render(delta: Float) {
        // The simulation keeps moving under an open window; follow it twice a second.
        refreshTimer += delta
        if (refreshTimer >= REFRESH_INTERVAL) {
            refreshTimer = 0f
            refresh()
        }
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

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        val header = Table()
        portraitImage = Image()
        header.add(portraitImage).size(56f, 56f).padRight(12f)
        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.2f)
        header.add(titleLabel).left()
        panel.add(header).colspan(3).left().padBottom(12f).row()

        infoLabel = Label("", skin)
        infoLabel.setWrap(true)
        panel.add(infoLabel).colspan(3).width(440f).left().padBottom(16f).row()

        panel.add(Label("Redirect to:", skin)).left().padRight(6f)
        routeButton = TextButton("", skin)
        routeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                val current = captain ?: return
                val others = Ports.ALL.map { it.name }.filter { it != current.portA }
                val idx = others.indexOf(pendingPortB)
                pendingPortB = others[(idx + 1) % others.size]
                refresh()
            }
        })
        panel.add(routeButton).width(150f).pad(4f).row()

        confirmRouteButton = TextButton("Confirm New Route", skin)
        confirmRouteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val current = captain ?: return
                AudioManager.playClick()
                refusal = if (FleetSimulation.redirect(current, pendingPortB)) ""
                else "${current.name} is already sailing for ${current.portB} - the route can be changed once they turn for home."
                refresh()
            }
        })
        panel.add(confirmRouteButton).colspan(3).width(220f).left().padBottom(4f).row()

        routeHint = Label("", skin)
        routeHint.setWrap(true)
        panel.add(routeHint).colspan(3).width(440f).left().padBottom(16f).row()

        recallButton = TextButton("Recall", skin)
        recallButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val current = captain ?: return
                AudioManager.playClick()
                if (hasCargo(current) && !recallArmed) {
                    // Cargo aboard would be written off: the first click only asks.
                    recallArmed = true
                    refresh()
                    return
                }
                FleetSimulation.recall(player, current)
                onBack()
            }
        })
        panel.add(recallButton).width(120f).padRight(8f)

        val backButton = TextButton("Close", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).width(120f)

        root.add(panel)
    }

    private fun refresh() {
        val current = captain ?: return
        val ship = player.ownedShips.find { it.id == current.shipId }
        portraitImage.drawable = TextureRegionDrawable(TextureRegion(IconFactory.portraitFor(current.name.hashCode())))
        titleLabel.setText("${current.name} - ${ship?.name ?: "?"}")

        val cargoText = if (current.cargoUnits > 0) "${current.cargoUnits}x ${current.cargoGood?.displayName}" else "empty"
        val heading = if (current.headingToB) "${current.portA} -> ${current.portB}" else "${current.portB} -> ${current.portA}"
        infoLabel.setText(
            "Ship: ${ship?.shipType?.name ?: "?"}\n" +
                "Cargo: $cargoText\n" +
                "Route: ${current.portA} <-> ${current.portB}\n" +
                "Currently sailing: $heading\n" +
                "${current.lastReport}\n" +
                "Lifetime profit: ${current.totalProfit}g"
        )

        routeButton.setText("${current.portA} -> $pendingPortB")

        val canChangeRoute = FleetSimulation.canRedirect(current)
        confirmRouteButton.touchable = if (canChangeRoute) Touchable.enabled else Touchable.disabled

        // The hold may have emptied (sold, or the trip ended) since the first press.
        if (!hasCargo(current)) recallArmed = false
        recallButton.setText(if (recallArmed) "Confirm recall" else "Recall")
        val cargoGood = current.cargoGood
        routeHint.setText(
            when {
                recallArmed && cargoGood != null ->
                    "Recalling ${current.name} now writes off ${current.cargoUnits}x ${cargoGood.displayName} (cost ${current.cargoCostTotal}g)."
                canChangeRoute -> ""
                refusal.isNotEmpty() -> refusal
                else -> "Can't redirect while outbound to ${current.portB} - wait until ${current.name} turns back toward ${current.portA}."
            }
        )
    }

    private fun hasCargo(c: Captain) = c.cargoUnits > 0 && c.cargoGood != null

    private companion object {
        const val REFRESH_INTERVAL = 0.5f
    }
}
