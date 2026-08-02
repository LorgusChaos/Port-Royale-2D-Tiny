package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

/** Shipyard sub-screen: buy a new ship outright (replacing the current one), one row per ship
 *  type with a thumbnail reusing the same sprite shown in-world and in combat. Reached from
 *  PortHubUI; "Back" returns to the hub, not to open sea. */
class ShipyardUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private val shipTextures = mutableMapOf<String, Texture>()
    private lateinit var goldLabel: Label
    private val shipStatusLabels = mutableMapOf<ShipType, Label>()

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open() {
        refresh()
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
        shipTextures.values.forEach { it.dispose() }
    }

    private fun textureFor(path: String): Texture = shipTextures.getOrPut(path) { Texture(Gdx.files.internal(path)) }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        val titleLabel = Label("Shipyard", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(4).left().padBottom(12f).row()

        goldLabel = Label("", skin)
        panel.add(goldLabel).colspan(4).left().padBottom(20f).row()

        for (shipType in ShipTypes.ALL) {
            val thumb = Image(TextureRegionDrawable(TextureRegion(textureFor(shipType.spritePath))))
            thumb.setScaling(Scaling.fit)
            thumb.color = shipType.tint

            val statusLabel = Label("", skin)
            shipStatusLabels[shipType] = statusLabel

            val buyButton = TextButton("Buy", skin)
            buyButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (player.canBuyShip(shipType)) {
                        player.buyShip(shipType)
                        AudioManager.playCoins()
                        refresh()
                    }
                }
            })

            panel.add(thumb).size(46f, 78f).pad(6f)
            panel.add(Label(shipType.name, skin)).left().width(100f).pad(4f)
            panel.add(statusLabel).width(320f).pad(4f)
            panel.add(buyButton).width(70f).pad(4f)
            panel.row()
        }

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(4).left().width(140f).padTop(20f)

        root.add(panel)
    }

    private fun refresh() {
        goldLabel.setText("Gold: ${player.gold}")
        for ((shipType, label) in shipStatusLabels) {
            label.setText(
                if (shipType == player.ship) {
                    "owned - cargo ${shipType.cargoCapacity}, hull ${shipType.maxHull}"
                } else {
                    "${shipType.price}g - cargo ${shipType.cargoCapacity}, hull ${shipType.maxHull}"
                }
            )
        }
    }
}
