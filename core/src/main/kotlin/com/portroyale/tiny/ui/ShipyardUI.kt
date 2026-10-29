package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

// PortFrameArt.SHIPYARD leaves 729x665 of clear parchment - narrower than the market's panel
// (the half-built hull takes the right-hand third of the picture) but taller. Five hulls at
// CARD_W + CARD_PAD each span 710 of the 729; the height goes into a bigger thumbnail, since one
// row of cards would otherwise leave most of the panel empty.
private const val CARD_W = 132f
private const val CARD_PAD = 5f
private const val THUMB_W = 84f
private const val THUMB_H = 142f
private const val HEADER_H = 44f
private const val BUTTON_H = 42f
/** Three lines, always: the stats are broken by hand rather than wrapped. A card is only 132
 *  wide, and letting "cargo 160, hull 220" find its own line breaks gave a different line count
 *  per ship - which left every Buy button at a different height, and cut the last line off the
 *  longest card. */
private const val STATS_H = 132f

/** Ship-buying sub-screen: one row of cards (thumbnail, name, price/stats, Buy), reached from
 *  ShipStatusUI rather than the other way around - status/maintenance is the primary shipyard
 *  screen now, this is where you go specifically to buy an additional hull. The frame's painted
 *  (X) returns to Ship Status, not to the hub. Buying doesn't replace the ship the player is
 *  currently sailing - it adds a new one to the fleet (see Player.ownedShips, FleetDockUI) and
 *  boards it, prompting for a name first; that prompt takes over the panel rather than opening a
 *  second window, so the frame stays the whole screen. */
class ShipyardUI(
    private val player: Player,
    private val onBack: () -> Unit
) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val frame = PortFrame(skin, PortFrameArt.SHIPYARD) { leave() }

    private val shipStatusLabels = mutableMapOf<ShipType, Label>()
    private lateinit var shipGrid: Table
    private lateinit var namingOverlay: Table
    private lateinit var namingPrompt: Label
    private lateinit var nameField: TextField
    private var currentPort = ""
    private var pendingShipType: ShipType? = null

    init {
        stage.addActor(frame.group)
        buildLayout()
        layoutFrame()
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

    private fun buildLayout() {
        val panel = Table()
        panel.top()

        panel.add(frame.bodyLabel(Align.center).apply { setText("Ships for sale") })
            .growX().height(HEADER_H).row()

        shipGrid = Table()
        for (shipType in ShipTypes.ALL) {
            shipGrid.add(buildShipCard(shipType)).top().width(CARD_W).pad(CARD_PAD)
        }

        namingOverlay = buildNamingOverlay()

        // One cell, two panels: a hidden Table still takes up its cell, so stacking them keeps
        // the naming prompt in the middle of the parchment instead of below an empty gap.
        panel.add(Stack(shipGrid, namingOverlay)).grow()

        frame.content.add(panel).grow()
    }

    private fun buildNamingOverlay(): Table {
        val overlay = Table()
        overlay.center()
        namingPrompt = frame.bodyLabel(Align.center)
        overlay.add(namingPrompt).colspan(2).padBottom(14f).row()
        nameField = TextField("", skin)
        nameField.maxLength = 24
        overlay.add(nameField).colspan(2).width(320f).height(BUTTON_H).padBottom(18f).row()
        overlay.add(frame.button("Confirm") {
            val shipType = pendingShipType ?: return@button
            AudioManager.playCoins()
            player.buyShip(shipType, nameField.text, currentPort)
            pendingShipType = null
            refresh()
        }).width(150f).height(BUTTON_H).pad(6f)
        overlay.add(frame.button("Cancel") {
            AudioManager.playClick()
            pendingShipType = null
            refresh()
        }).width(150f).height(BUTTON_H).pad(6f)
        return overlay
    }

    /** One ship's thumbnail/name/stats/Buy button, stacked compactly so all five fit in a row. */
    private fun buildShipCard(shipType: ShipType): Table {
        val card = Table()
        card.top()

        val thumb = Image(TextureRegionDrawable(TextureRegion(ShipArt.texture(shipType.spritePath))))
        thumb.setScaling(Scaling.fit)
        thumb.color = shipType.tint
        card.add(thumb).size(THUMB_W, THUMB_H).padTop(6f).row()

        card.add(frame.bodyLabel(Align.center).apply { setText(shipType.name) }).padTop(8f).row()

        val statusLabel = frame.bodyLabel(Align.top or Align.center)
        shipStatusLabels[shipType] = statusLabel
        card.add(statusLabel).width(CARD_W - 8f).height(STATS_H).padTop(6f).row()

        card.add(frame.button("Buy") {
            if (player.canBuyShip(shipType)) {
                val owned = player.ownedShips.count { it.shipType == shipType }
                pendingShipType = shipType
                nameField.text = if (owned == 0) shipType.name else "${shipType.name} ${owned + 1}"
                refresh()
                stage.keyboardFocus = nameField
            }
        }).width(CARD_W - 16f).height(BUTTON_H).row()

        return card
    }

    private fun refresh() {
        frame.setTitle("Shipyard")
        frame.setStatus(currentPort, player.gold)

        val naming = pendingShipType != null
        shipGrid.isVisible = !naming
        namingOverlay.isVisible = naming
        val pending = pendingShipType
        if (pending != null) namingPrompt.setText("Name your new ${pending.name}:")
        for ((shipType, label) in shipStatusLabels) {
            val headline = if (shipType == player.ship) "sailing now" else "${shipType.price}g"
            label.setText("$headline\ncargo ${shipType.cargoCapacity}\nhull ${shipType.maxHull}")
        }
    }
}
