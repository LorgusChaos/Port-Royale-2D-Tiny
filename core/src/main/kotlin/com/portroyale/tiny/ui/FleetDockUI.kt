package com.portroyale.tiny.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player

/** Docks sub-screen: every ship the player owns that's physically here right now (idle ones
 *  docked at this port, plus whichever one the player is currently sailing, since they're
 *  standing right here) - rename any of them, and board a different idle one to make it the
 *  ship the player personally sails. Crewing an idle ship with a hired captain instead happens
 *  from the Tavern (see TavernUI) - that's where captains as people live; this screen is just
 *  the ship inventory. Reached from ShipStatusUI; "Back" returns there. */
class FleetDockUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
    private val listPanel = Table()
    private var currentPort = ""

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        currentPort = portName
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

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(4).left().padBottom(12f).row()

        goldLabel = Label("", skin)
        panel.add(goldLabel).colspan(4).left().padBottom(20f).row()

        panel.add(listPanel).colspan(4).left().width(560f).padBottom(16f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(4).left().width(140f)

        root.add(panel)
    }

    private fun refresh() {
        titleLabel.setText("$currentPort - Docks")
        goldLabel.setText("Gold: ${player.gold}")

        listPanel.clear()
        val shipsHere = player.ownedShips.filter { it.id == player.activeShipId || it.dockedAt == currentPort }
        if (shipsHere.isEmpty()) {
            listPanel.add(Label("No ships of yours are here.", skin)).left().row()
            return
        }

        for (ship in shipsHere) {
            val isActive = ship.id == player.activeShipId
            val captain = player.captains.firstOrNull { it.shipId == ship.id }
            val statusText = when {
                isActive -> "Captain: You"
                captain != null -> "Captain: ${captain.name}"
                // Boarding is refused rather than silently dumping the excess overboard (see
                // Player.canBoardShip), so the hold is the one refusal the player can act on -
                // sell or warehouse the difference and the swap opens up.
                !player.cargoFitsOn(ship.shipType) ->
                    "Idle - your ${player.cargoUsed} units of cargo will not fit in this " +
                        "${ship.shipType.cargoCapacity}-unit hold"
                else -> "Idle - no captain (hire or reassign one at the Tavern)"
            }

            val nameField = TextField(ship.name, skin)
            nameField.maxLength = 24
            listPanel.add(nameField).width(200f).pad(4f)

            val saveNameButton = TextButton("Rename", skin)
            saveNameButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    AudioManager.playClick()
                    player.renameShip(ship.id, nameField.text)
                    refresh()
                }
            })
            listPanel.add(saveNameButton).width(90f).pad(4f)

            listPanel.add(Label("(${ship.shipType.name})", skin)).width(90f).pad(4f)

            val boardButton = TextButton(if (isActive) "Sailing" else "Board", skin)
            boardButton.touchable =
                if (!isActive && captain == null && player.canBoardShip(ship.id, currentPort)) Touchable.enabled
                else Touchable.disabled
            boardButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    AudioManager.playClick()
                    player.boardShip(ship.id, currentPort)
                    refresh()
                }
            })
            listPanel.add(boardButton).width(90f).pad(4f).row()

            listPanel.add(Label(statusText, skin)).colspan(4).left().padBottom(10f).row()
        }
    }
}
