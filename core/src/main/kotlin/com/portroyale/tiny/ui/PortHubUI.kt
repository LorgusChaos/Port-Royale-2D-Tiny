package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player

/** Port arrival hub: the city menu shown first on arrival, with icon buttons for the market,
 *  shipyard, and a fortress/commandant stub reserved for future quest content, plus the way
 *  back out to sea. Market/Shipyard sub-screens each have their own "Back" button that returns
 *  here rather than to the open sea. */
class PortHubUI(
    private val player: Player,
    private val onOpenMarket: () -> Unit,
    private val onOpenShipyard: () -> Unit,
    private val onOpenFortress: () -> Unit,
    private val onSetSail: () -> Unit
) {
    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
    private lateinit var cargoLabel: Label

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        titleLabel.setText(portName)
        goldLabel.setText("Gold: ${player.gold}")
        cargoLabel.setText("Cargo hold: ${player.cargoUsed} / ${player.cargoCapacity}  (${player.ship.name})")
    }

    fun render(delta: Float) {
        stage.viewport.apply()
        stage.act(delta)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
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
        titleLabel.setFontScale(1.5f)
        panel.add(titleLabel).padBottom(10f).row()

        goldLabel = Label("", skin)
        panel.add(goldLabel).left().row()
        cargoLabel = Label("", skin)
        panel.add(cargoLabel).left().padBottom(28f).row()

        val destinations = Table()
        destinations.add(iconButton(IconFactory.market, "Market") {
            AudioManager.playMarketEnter()
            onOpenMarket()
        }).size(150f, 130f).pad(10f)
        destinations.add(iconButton(IconFactory.shipyard, "Shipyard") {
            AudioManager.playShipyardEnter()
            onOpenShipyard()
        }).size(150f, 130f).pad(10f)
        destinations.add(iconButton(IconFactory.fortress, "Commandant") {
            AudioManager.playClick()
            onOpenFortress()
        }).size(150f, 130f).pad(10f)
        panel.add(destinations).padBottom(30f).row()

        val setSailButton = TextButton("Set Sail", skin)
        setSailButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onSetSail()
            }
        })
        panel.add(setSailButton).width(160f)

        root.add(panel)
    }

    private fun iconButton(icon: Texture, label: String, onClick: () -> Unit): Table {
        val button = Table()
        button.background = skin.getDrawable("button-up")
        button.pad(14f)

        val image = Image(TextureRegionDrawable(TextureRegion(icon)))
        button.add(image).size(64f, 64f).row()

        val lbl = Label(label, skin)
        lbl.setAlignment(Align.center)
        button.add(lbl).width(120f).padTop(8f)

        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onClick()
            }

            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                button.background = skin.getDrawable("button-down")
            }

            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                button.background = skin.getDrawable("button-up")
            }
        })
        return button
    }
}
