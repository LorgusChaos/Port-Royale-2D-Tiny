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
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

/** Ship-buying sub-screen: one row of cards (thumbnail, name, price/stats, Buy), reached from
 *  ShipStatusUI rather than the other way around - status/maintenance is the primary shipyard
 *  screen now, this is where you go specifically to buy an additional hull. "Back" returns to
 *  Ship Status, not the hub. A single wide row (rather than stacking rows) keeps total height low
 *  enough to fit without scrolling even with five-plus ship classes. Buying doesn't replace the
 *  ship the player is currently sailing - it adds a new one to the fleet (see Player.ownedShips,
 *  FleetDockUI) and boards it, prompting for a name first. */
class ShipyardUI(
    private val player: Player,
    private val onBack: () -> Unit
) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private val shipTextures = mutableMapOf<String, Texture>()
    private lateinit var goldLabel: Label
    private val shipStatusLabels = mutableMapOf<ShipType, Label>()
    private lateinit var shipGrid: Table
    private lateinit var namingOverlay: Table
    private lateinit var namingPrompt: Label
    private lateinit var nameField: TextField
    private var currentPort = ""
    private var pendingShipType: ShipType? = null

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        currentPort = portName
        pendingShipType = null
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
        shipTextures.values.forEach { it.dispose() }
    }

    private fun textureFor(path: String): Texture = shipTextures.getOrPut(path) { Texture(Gdx.files.internal(path)) }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        val titleLabel = Label("Shipyard - Ships for Sale", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).left().padBottom(12f).row()

        goldLabel = Label("", skin)
        panel.add(goldLabel).left().padBottom(20f).row()

        shipGrid = Table()
        for (shipType in ShipTypes.ALL) {
            shipGrid.add(buildShipCard(shipType)).top().pad(6f)
        }
        panel.add(shipGrid).padBottom(12f).row()

        namingOverlay = Table()
        namingPrompt = Label("", skin)
        namingOverlay.add(namingPrompt).colspan(2).padBottom(10f).row()
        nameField = TextField("", skin)
        nameField.maxLength = 24
        namingOverlay.add(nameField).colspan(2).width(260f).padBottom(14f).row()
        val confirmButton = TextButton("Confirm", skin)
        confirmButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val shipType = pendingShipType ?: return
                AudioManager.playCoins()
                player.buyShip(shipType, nameField.text, currentPort)
                pendingShipType = null
                refresh()
            }
        })
        namingOverlay.add(confirmButton).width(120f).pad(4f)
        val cancelButton = TextButton("Cancel", skin)
        cancelButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                pendingShipType = null
                refresh()
            }
        })
        namingOverlay.add(cancelButton).width(120f).pad(4f).row()
        panel.add(namingOverlay).padBottom(12f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).left().width(140f).padTop(8f)

        root.add(panel)
    }

    /** One ship's thumbnail/name/stats/Buy button, stacked compactly so two fit side by side. */
    private fun buildShipCard(shipType: ShipType): Table {
        val card = Table()
        card.background = skin.getDrawable("field-frame")

        val thumb = Image(TextureRegionDrawable(TextureRegion(textureFor(shipType.spritePath))))
        thumb.setScaling(Scaling.fit)
        thumb.color = shipType.tint
        card.add(thumb).size(46f, 78f).colspan(2).padTop(6f).row()

        card.add(Label(shipType.name, skin)).colspan(2).padTop(4f).row()

        val statusLabel = Label("", skin)
        shipStatusLabels[shipType] = statusLabel
        statusLabel.setWrap(true)
        statusLabel.setAlignment(com.badlogic.gdx.utils.Align.center)
        card.add(statusLabel).colspan(2).width(120f).pad(4f).row()

        val buyButton = TextButton("Buy", skin)
        buyButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (player.canBuyShip(shipType)) {
                    val owned = player.ownedShips.count { it.shipType == shipType }
                    pendingShipType = shipType
                    nameField.text = if (owned == 0) shipType.name else "${shipType.name} ${owned + 1}"
                    refresh()
                    stage.keyboardFocus = nameField
                }
            }
        })
        card.add(buyButton).colspan(2).width(90f).padBottom(6f).row()

        return card
    }

    private fun refresh() {
        goldLabel.setText("Gold: ${player.gold}")
        val naming = pendingShipType != null
        shipGrid.isVisible = !naming
        namingOverlay.isVisible = naming
        val pending = pendingShipType
        if (pending != null) namingPrompt.setText("Name your new ${pending.name}:")
        for ((shipType, label) in shipStatusLabels) {
            label.setText(
                if (shipType == player.ship) {
                    "sailing now\ncargo ${shipType.cargoCapacity}, hull ${shipType.maxHull}"
                } else {
                    "${shipType.price}g\ncargo ${shipType.cargoCapacity}, hull ${shipType.maxHull}"
                }
            )
        }
    }
}
