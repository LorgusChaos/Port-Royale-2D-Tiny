package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.factions.Faction

// --- how big each painted piece is drawn, in world units -----------------------------------------
// The HUD Stage is a ScreenViewport scaled by UiScale, which means a 16:9 window is 1280x720 world
// units at both 1280x720 and 1920x1080 - so these numbers are a real design canvas, not a guess
// that only holds at one size. Each piece is sized by WIDTH alone; its height follows from the
// picture's own aspect, so nothing is ever stretched.
private const val MARGIN = 10f
private const val STACK_GAP = 6f
private const val MINIMAP_W = 340f
private const val SHIP_PANEL_W = 202f
private const val REPUTATION_W = 300f
private const val GOLD_W = 231f
/** Sized so the calendar's own board matches the purse's board in height, which is what makes the
 *  two read as the same piece of furniture. Measured, not guessed: the purse's board is 212px of a
 *  975px-wide picture, the calendar's is 354px of a 984px-wide one, so at GOLD_W the purse's board
 *  stands 50 units tall and the calendar has to be 140 wide to match it. The time controls below
 *  the board come out small as a result - they are part of the same picture and shrink with it. */
private const val CALENDAR_W = 210f
private const val BUTTON_W = 62f

// --- boxes measured on the artwork itself (its own pixels, y down from the top-left) -------------
// Everything a label or a bar fill has to land on was read off the files over a coordinate grid
// rather than eyeballed in the running game; HudPiece.box converts these to world coordinates at
// whatever scale the piece ended up being drawn at.
private val GOLD_TEXT = floatArrayOf(235f, 50f, 690f, 105f)
// The calendar and the two time controls are one picture now, so the controls are hit areas over
// cells that are already painted in it rather than separate buttons on chains.
private val CALENDAR_TEXT = floatArrayOf(130f, 90f, 720f, 150f)
private val CALENDAR_SPEED_TEXT = floatArrayOf(340f, 385f, 320f, 130f)
private val CALENDAR_PAUSE_CELL = floatArrayOf(145f, 370f, 175f, 160f)
private val CALENDAR_SPEED_CELL = floatArrayOf(680f, 370f, 175f, 160f)
private val MINIMAP_WINDOW = floatArrayOf(201f, 164f, 574f, 393f)

private val SHIP_PORTRAIT = floatArrayOf(108f, 101f, 288f, 288f)
private val SHIP_NAME_BOX = floatArrayOf(450f, 46f, 475f, 46f)
private val SHIP_CAPTAIN_BOX = floatArrayOf(450f, 123f, 475f, 46f)
private const val SHIP_BAR_X = 562f
private const val SHIP_BAR_W = 332f
private const val SHIP_BAR_H = 34f
private val SHIP_BAR_Y = floatArrayOf(216f, 301f, 388f)

private const val REP_BAR_X = 215f
private const val REP_BAR_W = 665f
private const val REP_BAR_H = 30f
private val REP_BAR_Y = floatArrayOf(80f, 165f, 250f, 335f)

private val HULL_COLOUR: Color = Color.valueOf("B23A33")
private val SAILS_COLOUR: Color = Color.valueOf("3E7BB6")
private val CARGO_COLOUR: Color = Color.valueOf("5E8C3A")

/** How many ship panels fit between the foot of the purse and the bottom of the window.
 *  Deliberately computed rather than fixed: the fleet grows without limit and the screen does not.
 *  The left edge holds about five at 16:9 - the right-hand column, where these started, held two,
 *  which is the whole reason they moved. */
private fun shipPanelCapacity(columnTop: Float, columnBottom: Float): Int {
    val room = columnTop - columnBottom - STACK_GAP
    val each = SHIP_PANEL_W * (477f / 979f) + STACK_GAP
    return ((room + STACK_GAP) / each).toInt().coerceIn(0, MAX_SHIP_PANELS)
}

private const val MAX_SHIP_PANELS = 6

/** One line of the fleet column: whose ship it is and what state it is in. [onClick] is what the
 *  panel does when tapped - snap the camera to that ship - and is null for the player's own. */
data class ShipStatus(
    val shipName: String,
    val captainName: String,
    val hull: Float,
    val sails: Float,
    val cargo: Float,
    val onClick: (() -> Unit)? = null,
)

/**
 * The painted chrome shown while sailing: the purse and its lantern top-left with the fleet's ship
 * panels hanging in a column beneath them, the calendar and the time controls top-centre, and on
 * the right the chart frame around the minimap with the standings of the four colonial powers at
 * the foot of that edge.
 *
 * It is the sailing screen's own furniture and nothing else's: [setVisible] hides the lot the
 * moment a port window opens, which is what keeps the HUD from fighting the painted shopfronts
 * (see PortFrame). The minimap itself is NOT drawn here - PortRoyaleGame renders it with its own
 * camera straight into the hole in the chart frame, and asks [minimapWindow] where that hole
 * currently is, in screen pixels.
 *
 * A dedicated Stage sharing input with sailing (see PortRoyaleGame's
 * sailingWithToastInputProcessor), same pattern as EventToastUI.
 */
class HudOverlayUI(
    private val onOpenProperty: () -> Unit,
    private val onCycleGameSpeed: () -> Unit,
    private val onTogglePause: (Boolean) -> Unit,
    /** Where on the screen, in pixels with the origin at the bottom-left, the player tapped the
     *  chart. The HUD does not know what the chart is showing - PortRoyaleGame draws the map into
     *  that hole and so is the only one who can turn a point in it back into a place in the world. */
    private val onMinimapClick: (Float, Float) -> Unit,
) {

    val stage = Stage(UiScale.viewport())

    private val root = Group()

    private val goldPlate = HudPiece("plate_gold")
    private val calendarPlate = HudPiece("plate_calendar")
    private val minimapFrame = HudPiece("frame_minimap")
    private val reputationPanel = HudPiece("panel_reputation")

    private val goldLabel = label(PortFrame.BODY_COLOR, Align.left)
    private val dateLabel = label(PortFrame.BODY_COLOR, Align.center)
    private val speedLabel = label(PortFrame.TEXT_COLOR, Align.center)
    private val debugLabel = label(PortFrame.TEXT_COLOR, Align.bottomLeft).apply { setFontScale(0.5f) }

    /** Plain lettering on purpose, and temporary: the loan deadline has no painted plate of its
     *  own yet. It still has to be here - it is the only warning the player gets before a loan
     *  goes overdue - so it sits above the standings as text until its own element is drawn. */
    private val debtLabel = label(PortFrame.TEXT_COLOR, Align.right)

    /** The pause and fast-forward cells are painted into the calendar panel, so what the game adds
     *  is an invisible hit area over each - and, for pause, a veil over its cell, since a painted
     *  button that cannot change its own picture has no other way to look held down. */
    private var paused = false
    private val pauseVeil = HudArt.fill(Color(0f, 0f, 0f, 0.42f))
    private val pauseCell = hitArea { onTogglePause(!paused) }
    private val speedCell = hitArea { onCycleGameSpeed() }
    private val propertyButton = button("button_property") { onOpenProperty() }

    /** Over the hole in the chart, above the picture, so a tap on the map does not fall through to
     *  the sea behind it and set a course instead. */
    private val minimapHit = Actor()

    private val reputationFills = Faction.entries.associateWith { HudArt.fill(it.tint) }
    private val shipPanels = List(MAX_SHIP_PANELS) { ShipPanel() }

    private var ships: List<ShipStatus> = emptyList()
    private var reputation: Map<Faction, Int> = emptyMap()

    // Where each plate's lettering goes, kept so a changed value can be re-fitted without a
    // relayout: "1x" and "20x" are not the same width, and neither are 0 and 154,230 gold.
    private val goldBox = Rectangle()
    private val dateBox = Rectangle()
    private val speedBox = Rectangle()

    /** One ship's panel: the painted plate plus the three things the game writes onto it. The
     *  widgets are built once and reused - a panel that rebuilt its actors every frame would drop
     *  clicks, since a click spans two frames and Scene2D points its touch focus at an instance. */
    private inner class ShipPanel {
        val piece = HudPiece("ship_panel")
        val name = label(PortFrame.BODY_COLOR, Align.center)
        val captain = label(PortFrame.BODY_COLOR, Align.center)
        val bars = listOf(HudArt.fill(HULL_COLOUR), HudArt.fill(SAILS_COLOUR), HudArt.fill(CARGO_COLOUR))
        var onClick: (() -> Unit)? = null

        init {
            piece.image.touchable = Touchable.enabled
            piece.image.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onClick?.invoke()
                }
            })
        }

        val actors get() = listOf(piece.image) + bars + listOf(name, captain)

        fun visible(value: Boolean) = actors.forEach { it.isVisible = value }

        fun show(status: ShipStatus, left: Float, top: Float) {
            piece.place(left, top, SHIP_PANEL_W)
            name.setText(status.shipName)
            captain.setText(status.captainName)
            onClick = status.onClick
            piece.image.touchable = if (status.onClick == null) Touchable.disabled else Touchable.enabled
            fitLabel(name, piece.box(SHIP_NAME_BOX[0], SHIP_NAME_BOX[1], SHIP_NAME_BOX[2], SHIP_NAME_BOX[3]))
            fitLabel(captain, piece.box(SHIP_CAPTAIN_BOX[0], SHIP_CAPTAIN_BOX[1], SHIP_CAPTAIN_BOX[2], SHIP_CAPTAIN_BOX[3]))
            val values = listOf(status.hull, status.sails, status.cargo)
            for (i in bars.indices) {
                val trough = piece.box(SHIP_BAR_X, SHIP_BAR_Y[i], SHIP_BAR_W, SHIP_BAR_H)
                bars[i].setBounds(trough.x, trough.y, trough.width * values[i].coerceIn(0f, 1f), trough.height)
            }
            visible(true)
        }
    }

    init {
        root.addActor(goldPlate.image)
        root.addActor(goldLabel)
        root.addActor(calendarPlate.image)
        root.addActor(dateLabel)
        root.addActor(speedLabel)
        root.addActor(pauseVeil)
        root.addActor(pauseCell)
        root.addActor(speedCell)
        pauseVeil.isVisible = false
        root.addActor(minimapFrame.image)
        minimapHit.touchable = Touchable.enabled
        minimapHit.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val unitsPerPixel = (stage.viewport as ScreenViewport).unitsPerPixel
                onMinimapClick((minimapHit.x + x) / unitsPerPixel, (minimapHit.y + y) / unitsPerPixel)
            }
        })
        root.addActor(minimapHit)
        for (panel in shipPanels) panel.actors.forEach { root.addActor(it) }
        root.addActor(reputationPanel.image)
        reputationFills.values.forEach { root.addActor(it) }
        root.addActor(propertyButton)
        root.addActor(debtLabel)
        root.addActor(debugLabel)
        debugLabel.isVisible = false
        stage.addActor(root)
        layout()
    }

    // --- what the game feeds in -------------------------------------------------------------

    fun setDate(text: String) {
        dateLabel.setText(text)
        fitLabel(dateLabel, dateBox)
    }

    fun setGold(amount: Int) {
        goldLabel.setText(amount.toString())
        fitLabel(goldLabel, goldBox, Align.left)
    }

    fun setGameSpeed(multiplier: Float) {
        val text = if (multiplier == multiplier.toInt().toFloat()) "${multiplier.toInt()}x" else "${multiplier}x"
        speedLabel.setText(text)
        fitLabel(speedLabel, speedBox)
    }

    fun setPaused(value: Boolean) {
        paused = value
        pauseVeil.isVisible = value
    }

    fun setReputation(values: Map<Faction, Int>) {
        reputation = values
        layoutReputation()
    }

    /** The player's own ship first, then one line per captain at sea. Anything past what the
     *  column has room for is dropped rather than drawn off the bottom of the screen. */
    fun setShips(list: List<ShipStatus>) {
        ships = list
        layoutShips()
    }

    /** [fraction] is how much of the loan's term has run; the lettering reddens as it fills. */
    fun setDebt(visible: Boolean, fraction: Float, countdownText: String) {
        debtLabel.isVisible = visible
        if (!visible) return
        debtLabel.setText("Loan due: $countdownText")
        debtLabel.style.fontColor = PortFrame.TEXT_COLOR.cpy().lerp(Color.valueOf("D4453A"), fraction.coerceIn(0f, 1f))
    }

    fun setDebugText(text: String?) {
        debugLabel.isVisible = text != null
        if (text != null) debugLabel.setText(text)
    }

    /** Hidden whole while a port window is open - the HUD is the sailing screen's furniture, and
     *  the date and purse it carries are on the port frame's nameplate anyway. */
    fun setVisible(visible: Boolean) {
        root.isVisible = visible
    }

    /** Where the hole in the chart frame currently is, in SCREEN pixels with the origin at the
     *  bottom-left - which is what glViewport wants. The HUD's Stage works in world units scaled
     *  by UiScale, so this is the one place the two spaces have to be reconciled. */
    fun minimapWindow(): Rectangle {
        val unitsPerPixel = (stage.viewport as ScreenViewport).unitsPerPixel
        val box = minimapFrame.box(MINIMAP_WINDOW[0], MINIMAP_WINDOW[1], MINIMAP_WINDOW[2], MINIMAP_WINDOW[3])
        return Rectangle(
            box.x / unitsPerPixel,
            box.y / unitsPerPixel,
            box.width / unitsPerPixel,
            box.height / unitsPerPixel,
        )
    }

    // --- placement ---------------------------------------------------------------------------

    private fun layout() {
        val worldWidth = stage.viewport.worldWidth
        val worldHeight = stage.viewport.worldHeight

        goldPlate.place(MARGIN, worldHeight - MARGIN, GOLD_W)
        goldBox.set(goldPlate.box(GOLD_TEXT[0], GOLD_TEXT[1], GOLD_TEXT[2], GOLD_TEXT[3]))
        fitLabel(goldLabel, goldBox, Align.left)

        calendarPlate.place((worldWidth - CALENDAR_W) / 2f, worldHeight - MARGIN, CALENDAR_W)
        dateBox.set(calendarPlate.box(CALENDAR_TEXT[0], CALENDAR_TEXT[1], CALENDAR_TEXT[2], CALENDAR_TEXT[3]))
        fitLabel(dateLabel, dateBox)
        speedBox.set(calendarPlate.box(CALENDAR_SPEED_TEXT[0], CALENDAR_SPEED_TEXT[1], CALENDAR_SPEED_TEXT[2], CALENDAR_SPEED_TEXT[3]))
        fitLabel(speedLabel, speedBox)
        cover(pauseCell, calendarPlate.box(CALENDAR_PAUSE_CELL[0], CALENDAR_PAUSE_CELL[1], CALENDAR_PAUSE_CELL[2], CALENDAR_PAUSE_CELL[3]))
        cover(pauseVeil, calendarPlate.box(CALENDAR_PAUSE_CELL[0], CALENDAR_PAUSE_CELL[1], CALENDAR_PAUSE_CELL[2], CALENDAR_PAUSE_CELL[3]))
        cover(speedCell, calendarPlate.box(CALENDAR_SPEED_CELL[0], CALENDAR_SPEED_CELL[1], CALENDAR_SPEED_CELL[2], CALENDAR_SPEED_CELL[3]))

        minimapFrame.place(worldWidth - MARGIN - MINIMAP_W, worldHeight - MARGIN, MINIMAP_W)
        cover(minimapHit, minimapFrame.box(MINIMAP_WINDOW[0], MINIMAP_WINDOW[1], MINIMAP_WINDOW[2], MINIMAP_WINDOW[3]))

        reputationPanel.place(worldWidth - MARGIN - REPUTATION_W, MARGIN + reputationPanel.heightAt(REPUTATION_W), REPUTATION_W)
        val propertyHeight = BUTTON_W * (512f / 490f)
        propertyButton.setBounds(
            reputationPanel.x - STACK_GAP - BUTTON_W,
            reputationPanel.y,
            BUTTON_W,
            propertyHeight,
        )

        debtLabel.setBounds(
            reputationPanel.x,
            reputationPanel.y + reputationPanel.height + STACK_GAP,
            reputationPanel.width,
            26f,
        )
        // Tall enough for every line it prints and anchored to the bottom, so the overlay grows
        // upward out of the corner instead of off the bottom edge.
        debugLabel.setBounds(MARGIN, MARGIN, worldWidth * 0.6f, worldHeight * 0.7f)

        layoutShips()
        layoutReputation()
    }

    private fun layoutShips() {
        // Down the left edge, hanging under the purse and its lantern. The right-hand column had
        // the chart at the top and the standings at the foot of it, which left room for two
        // panels; this side has the whole height to itself.
        val capacity = shipPanelCapacity(goldPlate.y, MARGIN)
        var top = goldPlate.y - STACK_GAP
        for ((index, panel) in shipPanels.withIndex()) {
            val status = ships.getOrNull(index)
            if (status == null || index >= capacity) {
                panel.visible(false)
                continue
            }
            panel.show(status, MARGIN, top)
            top -= panel.piece.height + STACK_GAP
        }
    }

    private fun layoutReputation() {
        for ((index, faction) in Faction.entries.withIndex()) {
            val trough = reputationPanel.box(REP_BAR_X, REP_BAR_Y[index], REP_BAR_W, REP_BAR_H)
            val value = (reputation[faction] ?: 0).coerceIn(-100, 100)
            // Standing runs -100..+100 and is read against the middle of the trough, so the fill
            // grows out of the centre rather than up from the left end.
            val centre = trough.x + trough.width / 2f
            val half = trough.width / 2f * (kotlin.math.abs(value) / 100f)
            val fill = reputationFills.getValue(faction)
            fill.setBounds(if (value >= 0) centre else centre - half, trough.y, half, trough.height)
        }
    }

    // --- plumbing ----------------------------------------------------------------------------

    private fun HudPiece.heightAt(width: Float) = width * assetH / assetW

    private fun cover(actor: Actor, box: Rectangle) = actor.setBounds(box.x, box.y, box.width, box.height)

    /** A rectangle that catches clicks and draws nothing - for a control that is already painted
     *  into the picture underneath it. */
    private fun hitArea(onClick: () -> Unit): Actor {
        val actor = Actor()
        actor.touchable = Touchable.enabled
        actor.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = onClick()
        })
        return actor
    }

    /** Put a label inside a box measured on the artwork, shrinking the text to fit BOTH ways - a
     *  six-figure purse and a long ship name have to stay on their plates, and a name strip on a
     *  panel drawn 280 units wide is only thirteen units tall, so height is the binding
     *  constraint far more often than width. */
    private fun fitLabel(label: Label, box: Rectangle, align: Int = Align.center) {
        label.setFontScale(1f)
        val natural = label.prefWidth
        val tall = label.prefHeight
        val scale = minOf(
            if (natural > 0f) box.width / natural else 1f,
            if (tall > 0f) box.height / tall else 1f,
            1f,
        )
        label.setFontScale(scale)
        label.setAlignment(align)
        label.setBounds(box.x, box.y, box.width, box.height)
    }

    private fun label(colour: Color, align: Int): Label {
        val style = Label.LabelStyle(font, Color.WHITE)
        val label = Label("", style)
        label.color = Color.WHITE
        label.style.fontColor = colour
        label.setAlignment(align)
        return label
    }

    private fun button(name: String, toggle: Boolean = false, onChange: (Boolean) -> Unit): Button {
        val up = TextureRegionDrawable(TextureRegion(HudArt.texture("${name}_normal")))
        val down = TextureRegionDrawable(TextureRegion(HudArt.texture("${name}_pressed")))
        val style = Button.ButtonStyle(up, down, null)
        if (toggle) style.checked = down
        val button = Button(style)
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onChange(button.isChecked)
            }
        })
        return button
    }

    fun render(delta: Float) {
        stage.viewport.apply()
        stage.act(delta)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        UiScale.applyResize(stage, width, height)
        layout()
    }

    fun dispose() {
        stage.dispose()
        sharedFont?.dispose()
        sharedFont = null
    }

    private companion object {
        private var sharedFont: BitmapFont? = null

        /** Baked white so a per-label fontColor can tint it - the skin's own font is baked black
         *  and no colour lightens it back up. See UiSkinFactory.generateFont. */
        val font: BitmapFont
            get() = sharedFont
                ?: UiSkinFactory.generateFont(32, Color.WHITE, borderRatio = 0.06f).also { sharedFont = it }
    }
}
