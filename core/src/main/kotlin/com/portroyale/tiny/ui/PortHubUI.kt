package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player

private const val DESIGN_W = 1536f
private const val DESIGN_H = 1024f

// Bounding boxes (image-space, y-down, in the 1536x1024 port_hub_frame.png design canvas) of the
// hollow slots left in the frame art for each destination's icon+nameboard, and of the blank
// nameplate banner/close button baked into the frame - measured by eye against the source art,
// see the frame-extraction note on [PortHubUI.frameTexture].
private data class Box(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    val width get() = x1 - x0
    val height get() = y1 - y0
}

private val NAMEPLATE_TITLE_BOX = Box(510f, 42f, 1040f, 108f)
private val NAMEPLATE_SUBTITLE_BOX = Box(540f, 106f, 1010f, 165f)
private val CLOSE_BUTTON_BOX = Box(1432f, 58f, 1508f, 134f)
private val MARKET_BOX = Box(233f, 213f, 577f, 542f)
private val SHIPYARD_BOX = Box(600f, 213f, 955f, 542f)
private val WAREHOUSE_BOX = Box(980f, 213f, 1312f, 542f)
private val FORTRESS_BOX = Box(363f, 533f, 712f, 862f)
private val TAVERN_BOX = Box(788f, 533f, 1180f, 862f)

/** Port arrival hub: the city menu shown first on arrival, laid out directly over a fixed-design
 *  background image (a wooden shopfront frame with 5 hollow icon slots and a blank nameplate
 *  banner, AI-generated art) instead of a generic Scene2D Table/skin panel. [frame] is built once
 *  at the image's native 1536x1024 resolution and uniformly scaled+centered to fit the actual
 *  window in [layoutFrame] - see the ShapeRenderer/UiScale gotchas in CLAUDE.md for why a fixed
 *  design canvas plus a single Group.setScale is simpler here than trying to hand-scale every
 *  child position per resize. Market/Shipyard/Warehouse sub-screens each have their own "Back"
 *  button that returns here; leaving the hub entirely (back to sea) is the nameplate's close (X).
 */
class PortHubUI(
    private val player: Player,
    private val onOpenMarket: () -> Unit,
    private val onOpenShipyard: () -> Unit,
    private val onOpenFortress: () -> Unit,
    private val onOpenWarehouse: () -> Unit,
    private val onSetSail: () -> Unit
) {
    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val frame = Group()
    private lateinit var titleLabel: Label
    private lateinit var statusLabel: Label

    // Independent font (not the shared skin font) so it can be baked white and tinted cream via
    // each Label's own style - the shared skin font is baked solid black, which no amount of
    // per-Label fontColor tinting can lighten back up. Per-Label setFontScale (title bigger than
    // the icon nameboard labels) is safe to share across Labels since Scene2D's Label re-applies
    // its own scale at layout time - the documented shared-BitmapFontData gotcha only bites raw,
    // non-Label font.draw() calls (e.g. the map's port labels), not Label widgets.
    private val overlayFont: BitmapFont = UiSkinFactory.generateFont(60, Color.WHITE, borderRatio = 0.12f)
    private val overlayTextColor = Color.valueOf("F2E4BE")

    private val frameTexture: Texture by lazy {
        Texture(Gdx.files.internal("sprites/ui/port_hub_frame.png"), true).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        }
    }

    init {
        frame.setTransform(true)
        frame.setSize(DESIGN_W, DESIGN_H)
        stage.addActor(frame)
        buildLayout()
        layoutFrame()
    }

    fun open(portName: String) {
        titleLabel.setText(portName)
        statusLabel.setText(
            "Gold: ${player.gold}   -   Cargo: ${player.cargoUsed}/${player.cargoCapacity} (${player.ship.name})"
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
        overlayFont.dispose()
    }

    /** Scales+centers the fixed 1536x1024 design canvas to fit whatever the actual window is,
     *  preserving its aspect ratio (a small margin so the frame never touches the screen edge). */
    private fun layoutFrame() {
        val vw = stage.viewport.worldWidth
        val vh = stage.viewport.worldHeight
        val scale = minOf(vw / DESIGN_W, vh / DESIGN_H) * 0.94f
        frame.setScale(scale)
        frame.setPosition((vw - DESIGN_W * scale) / 2f, (vh - DESIGN_H * scale) / 2f)
    }

    private fun buildLayout() {
        val bg = Image(TextureRegionDrawable(TextureRegion(frameTexture)))
        bg.setSize(DESIGN_W, DESIGN_H)
        frame.addActor(bg)

        titleLabel = overlayLabel(fontScale = 1.05f)
        titleLabel.setBounds(NAMEPLATE_TITLE_BOX)
        frame.addActor(titleLabel)

        statusLabel = overlayLabel(fontScale = 0.58f)
        statusLabel.setBounds(NAMEPLATE_SUBTITLE_BOX)
        frame.addActor(statusLabel)

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
        }

        frame.addActor(closeButtonHotzone())
    }

    private fun overlayLabel(fontScale: Float): Label {
        val label = Label("", Label.LabelStyle(overlayFont, overlayTextColor))
        label.setFontScale(fontScale)
        label.setAlignment(Align.center)
        return label
    }

    /** sceneY for a top-left-origin, y-down [Box] on the design canvas is flipped/offset since
     *  Scene2D actors are y-up from their own bottom-left corner. */
    private fun Actor.setBounds(box: Box) = setBounds(box.x0, DESIGN_H - box.y1, box.width, box.height)

    /** One destination icon: the actual clickable art (fit to [box] by its own aspect ratio and
     *  bottom-aligned within it, since every icon's baked-in wooden nameboard sits at its own
     *  bottom edge and they should all appear to share one shelf line) plus its name overlaid on
     *  that nameboard, instead of a separate label below a generic button background. Icon +
     *  label live inside one [Group] ("slot") so the hover grow-effect scales both together -
     *  scaling only the icon Image left the label geometrically stuck in place, visibly
     *  detaching it from the icon's own board on hover. */
    private fun addIconSlot(icon: Texture, label: String, box: Box, onClick: () -> Unit) {
        val aspect = icon.width.toFloat() / icon.height.toFloat()
        var iconW = box.width
        var iconH = iconW / aspect
        if (iconH > box.height) {
            iconH = box.height
            iconW = iconH * aspect
        }
        val iconX = box.x0 + (box.width - iconW) / 2f
        val iconY = DESIGN_H - box.y1

        val slot = Group()
        slot.setTransform(true)
        slot.setBounds(iconX, iconY, iconW, iconH)
        slot.setOrigin(iconW / 2f, iconH / 2f)
        slot.touchable = Touchable.enabled

        val image = Image(TextureRegionDrawable(TextureRegion(icon)))
        image.setBounds(0f, 0f, iconW, iconH)
        slot.addActor(image)

        val nameLabel = overlayLabel(fontScale = 0.68f)
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
        frame.addActor(slot)
    }

    private fun closeButtonHotzone(): Actor {
        val hotzone = Actor()
        hotzone.setBounds(CLOSE_BUTTON_BOX)
        hotzone.touchable = Touchable.enabled
        hotzone.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onSetSail()
            }
        })
        return hotzone
    }
}
