package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player

// Bounding boxes (image-space, y-down, in the 1536x1024 port_hub_frame.png design canvas) of the
// hollow slots left in the frame art for each destination's icon+nameboard - measured by eye
// against the source art. The frame's own furniture (nameplate, close button, parchment panel)
// lives in PortFrame.
private val MARKET_BOX = FrameBox(233f, 213f, 577f, 542f)
private val SHIPYARD_BOX = FrameBox(600f, 213f, 955f, 542f)
private val WAREHOUSE_BOX = FrameBox(980f, 213f, 1312f, 542f)
private val FORTRESS_BOX = FrameBox(363f, 533f, 712f, 862f)
private val TAVERN_BOX = FrameBox(788f, 533f, 1180f, 862f)

/** Port arrival hub: the city menu shown first on arrival, laid out directly over the painted
 *  shopfront [PortFrame] rather than a generic Scene2D panel - five hollow icon slots and a
 *  nameplate banner. Market/Shipyard/Warehouse sub-screens each return here; leaving the hub
 *  entirely (back to sea) is the nameplate's close (X).
 */
class PortHubUI(
    private val player: Player,
    private val onOpenMarket: () -> Unit,
    private val onOpenShipyard: () -> Unit,
    private val onOpenFortress: () -> Unit,
    private val onOpenWarehouse: () -> Unit,
    private val onOpenTavern: () -> Unit,
    private val onSetSail: () -> Unit
) {
    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val frame = PortFrame(skin) {
        AudioManager.playClick()
        onSetSail()
    }

    init {
        stage.addActor(frame.group)
        buildLayout()
        layoutFrame()
    }

    fun open(portName: String) {
        frame.setTitle(portName)
        frame.setStatus(
            gold = player.gold,
            cargo = player.cargoUsed to player.cargoCapacity,
            suffix = player.ship.name,
        )
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

    private fun buildLayout() {
        addIconSlot(IconFactory.market, "Market", MARKET_BOX) {
            AudioManager.playMarketEnter()
            onOpenMarket()
        }
        addIconSlot(IconFactory.shipyard, "Shipyard", SHIPYARD_BOX) {
            AudioManager.playShipyardEnter()
            onOpenShipyard()
        }
        addIconSlot(IconFactory.warehouse, "Warehouse", WAREHOUSE_BOX) {
            AudioManager.playClick()
            onOpenWarehouse()
        }
        addIconSlot(IconFactory.fortress, "Commandant", FORTRESS_BOX) {
            AudioManager.playClick()
            onOpenFortress()
        }
        addIconSlot(IconFactory.tavern, "Tavern", TAVERN_BOX) {
            AudioManager.playClick()
            onOpenTavern()
        }
    }

    /** One destination icon: the actual clickable art (fit to [box] by its own aspect ratio and
     *  bottom-aligned within it, since every icon's baked-in wooden nameboard sits at its own
     *  bottom edge and they should all appear to share one shelf line) plus its name overlaid on
     *  that nameboard, instead of a separate label below a generic button background. Icon +
     *  label live inside one [Group] ("slot") so the hover grow-effect scales both together -
     *  scaling only the icon Image left the label geometrically stuck in place, visibly
     *  detaching it from the icon's own board on hover. */
    private fun addIconSlot(icon: Texture, label: String, box: FrameBox, onClick: () -> Unit) {
        val aspect = icon.width.toFloat() / icon.height.toFloat()
        var iconW = box.width
        var iconH = iconW / aspect
        if (iconH > box.height) {
            iconH = box.height
            iconW = iconH * aspect
        }
        val iconX = box.x0 + (box.width - iconW) / 2f
        val iconY = PortFrame.DESIGN_H - box.y1

        val slot = Group()
        slot.setTransform(true)
        slot.setBounds(iconX, iconY, iconW, iconH)
        slot.setOrigin(iconW / 2f, iconH / 2f)
        slot.touchable = Touchable.enabled

        val image = Image(TextureRegionDrawable(TextureRegion(icon)))
        image.setBounds(0f, 0f, iconW, iconH)
        slot.addActor(image)

        val nameLabel = frame.label(fontScale = 0.68f)
        nameLabel.setText(label)
        nameLabel.setBounds(0f, iconH * 0.015f, iconW, iconH * 0.16f)
        slot.addActor(nameLabel)

        slot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = onClick()
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                slot.setScale(1.06f)
            }
            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                slot.setScale(1f)
            }
        })
        frame.group.addActor(slot)
    }
}
