package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align

/** Bounding box in the frame art's own 1536x1024 image space, y down from the top-left corner. */
data class FrameBox(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    val width get() = x1 - x0
    val height get() = y1 - y0
}

/**
 * Which painted shopfront a screen is drawn inside.
 *
 * All four pictures are the same 1536x1024 canvas and carry the nameplate and the close button as
 * the same painted element in the same place, so those boxes are shared. What differs is how much
 * clear parchment is left once each frame's own props have eaten into the panel - the market's
 * stalls, the shipyard's half-built hull, the tavern's shelves.
 *
 * [content] was measured off the files rather than by eye: flood the parchment outward from the
 * centre of the panel (which takes in the vignette a flat colour threshold misses), then take the
 * largest rectangle that fits inside it. tools/art/art.py cut the frames out of their black
 * backgrounds; the hub's box predates that and is left at its hand-measured value, since the hub
 * and warehouse screens are already laid out against it.
 */
enum class PortFrameArt(
    val path: String,
    val content: FrameBox,
    /** Whether the picture has an (X) painted at [PortFrame.CLOSE_BUTTON_BOX]. A screen on a
     *  frame without one has to offer its own way back - an invisible hit area with nothing
     *  drawn in it is not a button, it is a trap. */
    val hasPaintedClose: Boolean = true,
) {
    HUB("sprites/ui/port_hub_frame.png", FrameBox(312f, 232f, 1186f, 862f)),
    MARKET("sprites/ui/port_market_frame.png", FrameBox(347f, 238f, 1175f, 859f)),
    SHIPYARD("sprites/ui/port_shipyard_frame.png", FrameBox(355f, 230f, 1084f, 895f)),
    TAVERN("sprites/ui/port_tavern_frame.png", FrameBox(369f, 282f, 1201f, 859f), hasPaintedClose = false),
}

/**
 * The painted shopfront every in-port screen is drawn inside: wooden frame, parchment panel,
 * nameplate banner and the close (X) baked into the art, all laid out on the picture's own fixed
 * 1536x1024 canvas and scaled as one Group to whatever the window is.
 *
 * It exists because the port hub looked like a place and the sub-screens looked like dialogs. The
 * boxes below were measured against the source art - the nameplate and the X by eye, [CONTENT_BOX]
 * as the largest rectangle of clear parchment (the lantern, the anchor and the crates painted into
 * the frame eat into the corners, so the usable panel is smaller than the parchment).
 *
 * The texture and the font are shared by every screen that uses a frame and disposed once, by
 * PortRoyaleGame, the same way [ShipArt] is - not by the screens that borrow them.
 */
class PortFrame(
    private val skin: Skin,
    private val art: PortFrameArt = PortFrameArt.HUB,
    private val onClose: () -> Unit,
) {

    /** The design canvas. Screens add their own actors here for anything outside [content]. */
    val group = Group()

    /** The parchment panel. A screen fills this in; the frame never touches its contents. */
    val content = Table()

    private val titleLabel = label(TITLE_SCALE)

    /** The second line of the nameplate. A row rather than a label, because the purse and the hold
     *  are shown as their painted icons instead of the words "Gold" and "Cargo" - which a single
     *  Label cannot do. */
    private val subtitleRow = Table()

    init {
        group.setTransform(true)
        group.setSize(DESIGN_W, DESIGN_H)

        val background = Image(TextureRegionDrawable(TextureRegion(texture(art.path))))
        background.setSize(DESIGN_W, DESIGN_H)
        group.addActor(background)

        titleLabel.place(NAMEPLATE_TITLE_BOX)
        group.addActor(titleLabel)
        subtitleRow.place(NAMEPLATE_SUBTITLE_BOX)
        subtitleRow.center()
        subtitleRow.touchable = Touchable.disabled
        group.addActor(subtitleRow)

        content.place(art.content)
        content.top().left()
        // The panel itself is only a place to put things; clicks belong to whatever is in it.
        content.touchable = Touchable.childrenOnly
        group.addActor(content)

        if (art.hasPaintedClose) {
            val close = Actor()
            close.place(CLOSE_BUTTON_BOX)
            close.touchable = Touchable.enabled
            close.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) = onClose()
            })
            group.addActor(close)
        }
    }

    /** The same exit the painted (X) takes, for a frame that has none - see
     *  [PortFrameArt.hasPaintedClose]. */
    fun close() = onClose()

    fun setTitle(text: String) = titleLabel.setText(text)

    /** The nameplate's second line: an optional lead-in (usually the port), the purse, the hold,
     *  and an optional tail - each part skipped when it is not given. The purse and the hold come
     *  out as icons; everything else is text. */
    fun setStatus(prefix: String = "", gold: Int? = null, cargo: Pair<Int, Int>? = null, suffix: String = "") {
        subtitleRow.clear()
        val iconHeight = NAMEPLATE_SUBTITLE_BOX.height * 0.88f
        if (prefix.isNotEmpty()) subtitleRow.add(statusText(prefix)).padRight(STATUS_GAP)
        if (gold != null) {
            addIcon(IconFactory.coin, iconHeight)
            subtitleRow.add(statusText(gold.toString())).padRight(STATUS_GAP)
        }
        if (cargo != null) {
            addIcon(IconFactory.cargo, iconHeight)
            subtitleRow.add(statusText("${cargo.first}/${cargo.second}")).padRight(STATUS_GAP)
        }
        if (suffix.isNotEmpty()) subtitleRow.add(statusText(suffix))
    }

    /** Sized by height, width following the picture's own aspect - the purse is wide and the hold
     *  is tall, and squaring either one would squash it. */
    private fun addIcon(texture: Texture, height: Float) {
        val width = height * texture.width / texture.height
        subtitleRow.add(Image(TextureRegionDrawable(TextureRegion(texture)))).size(width, height).padRight(8f)
    }

    private fun statusText(text: String) = label(SUBTITLE_SCALE).apply { setText(text) }

    /** Scales and centres the fixed design canvas to fit the actual window, aspect preserved,
     *  with a small margin so the frame never touches the screen edge. */
    fun layout(worldWidth: Float, worldHeight: Float) {
        val scale = minOf(worldWidth / DESIGN_W, worldHeight / DESIGN_H) * FIT_MARGIN
        group.setScale(scale)
        group.setPosition((worldWidth - DESIGN_W * scale) / 2f, (worldHeight - DESIGN_H * scale) / 2f)
    }

    /** A cream label in the frame's own font, which is baked white so it can be tinted - the
     *  shared skin font is baked solid black and no fontColor can lighten it back up. */
    fun label(fontScale: Float, align: Int = Align.center): Label {
        val label = Label("", Label.LabelStyle(font, TEXT_COLOR))
        label.setFontScale(fontScale)
        label.setAlignment(align)
        return label
    }

    /** A line of the panel's own text: dark on the parchment, where the nameplate's cream would
     *  have almost no contrast, and in its own smaller font rather than the nameplate's scaled
     *  down - that one carries a heavy fake-bold outline (see UiSkinFactory.generateFont) which,
     *  at the size a table row needs, swallows the letter it is supposed to thicken. */
    fun bodyLabel(align: Int = Align.left): Label {
        val label = Label("", Label.LabelStyle(bodyFont, Color.WHITE))
        label.setAlignment(align)
        return label
    }

    /** A button in the frame's language: the skin's wooden chrome and the panel's lettering. */
    fun button(text: String, fontScale: Float = 1f, onClick: () -> Unit): TextButton {
        val style = TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle::class.java))
        style.font = buttonFont
        style.fontColor = TEXT_COLOR
        val button = TextButton(text, style)
        button.label.setFontScale(fontScale)
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = onClick()
        })
        return button
    }

    /** A button whose face is a painted plate from sprites/ui/hud/ instead of the skin's wooden
     *  chrome. The plates came without a pressed picture, so the held state is the same plate
     *  dimmed - and the lettering is the panel's dark body font, because a plate is parchment and
     *  the cream that reads on dark wood disappears on it. */
    fun plateButton(text: String, plate: String, fontScale: Float = 1f, onClick: () -> Unit): TextButton {
        val face = TextureRegionDrawable(TextureRegion(HudArt.texture(plate)))
        val style = TextButton.TextButtonStyle(face, face.tint(Color(0.80f, 0.76f, 0.70f, 1f)), null, bodyFont)
        style.fontColor = Color.WHITE
        val button = TextButton(text, style)
        button.label.setFontScale(fontScale)
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = onClick()
        })
        return button
    }

    /** y-down image-space box to Scene2D's y-up actor bounds. */
    private fun Actor.place(box: FrameBox) = setBounds(box.x0, DESIGN_H - box.y1, box.width, box.height)

    companion object {
        const val DESIGN_W = 1536f
        const val DESIGN_H = 1024f
        private const val FIT_MARGIN = 0.94f

        /** Space between the nameplate's parts - port, purse, hold. */
        private const val STATUS_GAP = 26f

        const val TITLE_SCALE = 1.05f
        const val SUBTITLE_SCALE = 0.58f
        /** Body text is generated at this size on the design canvas (which is then scaled to the
         *  window as a whole), not scaled down from the nameplate font. */
        private const val BODY_FONT_SIZE = 34

        val NAMEPLATE_TITLE_BOX = FrameBox(510f, 42f, 1040f, 108f)
        val NAMEPLATE_SUBTITLE_BOX = FrameBox(540f, 106f, 1010f, 165f)
        val CLOSE_BUTTON_BOX = FrameBox(1432f, 58f, 1508f, 134f)

        /** Nameplate lettering: cream, on dark wood. */
        val TEXT_COLOR: Color = Color.valueOf("F2E4BE")

        /** Panel lettering: dark brown, on light parchment. */
        val BODY_COLOR: Color = Color.valueOf("2E1C0C")

        private val sharedTextures = mutableMapOf<String, Texture>()
        private var sharedFont: BitmapFont? = null
        private var sharedBodyFont: BitmapFont? = null
        private var sharedButtonFont: BitmapFont? = null

        private fun texture(path: String): Texture = sharedTextures.getOrPut(path) {
            Texture(Gdx.files.internal(path), true).apply {
                setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            }
        }

        /** White-baked with a thin border, so per-label tinting works and the lettering keeps its
         *  outline against the parchment. See UiSkinFactory.generateFont. */
        private val font: BitmapFont
            get() = sharedFont ?: UiSkinFactory.generateFont(60, Color.WHITE, borderRatio = 0.12f).also { sharedFont = it }

        /** Baked dark (and so tinted with WHITE, which leaves the baked colour alone) rather than
         *  baked white and tinted: the panel is light parchment, and the same cream that carries
         *  the nameplate on dark wood is nearly invisible on it. Thin outline only - the heavy one
         *  is for text standing on busy art. */
        private val bodyFont: BitmapFont
            get() = sharedBodyFont
                ?: UiSkinFactory.generateFont(BODY_FONT_SIZE, BODY_COLOR, borderRatio = 0.02f).also { sharedBodyFont = it }

        /** The same size as the panel's text but baked white, for lettering that sits on the
         *  buttons' dark wood instead of on parchment - where cream reads and dark brown does not. */
        private val buttonFont: BitmapFont
            get() = sharedButtonFont
                ?: UiSkinFactory.generateFont(BODY_FONT_SIZE, Color.WHITE, borderRatio = 0.05f).also { sharedButtonFont = it }

        /** Called once from PortRoyaleGame.dispose(), never by a screen. */
        fun dispose() {
            sharedTextures.values.forEach { it.dispose() }
            sharedTextures.clear()
            sharedFont?.dispose()
            sharedFont = null
            sharedBodyFont?.dispose()
            sharedBodyFont = null
            sharedButtonFont?.dispose()
            sharedButtonFont = null
        }
    }
}
