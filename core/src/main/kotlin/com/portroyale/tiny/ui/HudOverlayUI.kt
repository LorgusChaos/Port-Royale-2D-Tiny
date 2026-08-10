package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.factions.Faction

private const val ICON_SIZE = 40f
private const val CAPTAIN_ICON_SIZE = 32f
private const val DEBT_BAR_WIDTH = 40
private const val DEBT_BAR_HEIGHT = 8
private val DEBT_BAR_EMPTY = Color.valueOf("2E2A24")
private val DEBT_BAR_FILL = Color.valueOf("A61C1C")

private const val REP_BAR_WIDTH = 104
private const val REP_BAR_HEIGHT = 10
private val REP_BAR_EMPTY = Color.valueOf("2E2A24")
private val REP_BAR_TIER_MARK = Color.valueOf("6B6152")
private val REP_BAR_ZERO_MARK = Color.valueOf("C9BC9C")
/** The tier boundaries from factions/Faction.kt, drawn as the notches a player reads position
 *  against - above them a discount, below them a navy that starts taking an interest. */
private val REP_TIER_MARKS = intArrayOf(-50, -15, 15, 50)

/** Small persistent HUD elements shown while sailing - anchored top-left: the in-game calendar
 *  date and gold balance (pure display, see setDate/setGold); anchored under the minimap
 *  top-right: the Property button (always visible), one small portrait button per active fleet
 *  captain (click to snap the camera to that captain's ship - see setCaptains/PortRoyaleGame.
 *  jumpToCaptain), and the debt indicator (only visible with an outstanding loan, see
 *  PortRoyaleGame's loan handling; the bar fills red as the deadline approaches). A dedicated
 *  Stage sharing input with sailing (see PortRoyaleGame's sailingWithToastInputProcessor), same
 *  pattern as EventToastUI. */
class HudOverlayUI(
    private val onOpenProperty: () -> Unit,
    private val onJumpToCaptain: (Captain) -> Unit,
    private val onCycleGameSpeed: () -> Unit
) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val debtGroup = Table()
    private val debtFillImage = Image()
    private val debtCountdownLabel = Label("", skin)
    private var debtBarTexture: Texture? = null
    private var lastFraction = -1f
    private val dateLabel = Label("", skin)
    private val goldLabel = Label("", skin)
    private val speedLabel = Label("", skin)
    private val captainIconsGroup = Table()
    private var lastCaptains: List<Captain> = emptyList()

    private val reputationGroup = Table()
    private val reputationRows = Table()
    private val reputationToggleLabel = Label("", skin)
    private val reputationBars = mutableMapOf<Faction, Image>()
    private val reputationTextures = mutableMapOf<Faction, Texture>()
    private val lastReputation = mutableMapOf<Faction, Int>()
    private var reputationExpanded = true

    private val debugLabel = Label("", skin)

    init {
        val root = Table()
        root.setFillParent(true)
        root.top().right().padTop(150f).padRight(20f)

        val propertyButton = Table()
        propertyButton.background = skin.getDrawable("button-up")
        propertyButton.add(Image(TextureRegionDrawable(TextureRegion(IconFactory.property))))
            .size(ICON_SIZE, ICON_SIZE).pad(6f)
        propertyButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onOpenProperty()
            }
        })
        root.add(propertyButton).row()

        captainIconsGroup.padTop(10f)
        root.add(captainIconsGroup).row()

        debtGroup.isVisible = false
        val debtIcon = Image(TextureRegionDrawable(TextureRegion(IconFactory.debt)))
        debtGroup.add(debtIcon).size(ICON_SIZE, ICON_SIZE).padTop(10f).row()
        debtGroup.add(debtFillImage).size(DEBT_BAR_WIDTH.toFloat(), DEBT_BAR_HEIGHT.toFloat()).row()
        debtCountdownLabel.setAlignment(Align.center)
        debtCountdownLabel.setFontScale(0.8f)
        debtGroup.add(debtCountdownLabel).padTop(2f)
        root.add(debtGroup)

        stage.addActor(root)

        val infoRoot = Table()
        infoRoot.setFillParent(true)
        infoRoot.top().left().padTop(20f).padLeft(20f)

        dateLabel.setFontScale(1.05f)
        infoRoot.add(dateLabel).left().row()

        val speedButton = Table()
        speedButton.background = skin.getDrawable("button-up")
        speedButton.add(Image(TextureRegionDrawable(TextureRegion(IconFactory.clock)))).size(22f, 22f).pad(4f).padLeft(6f)
        speedLabel.setFontScale(0.95f)
        speedButton.add(speedLabel).padRight(10f)
        speedButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onCycleGameSpeed()
            }
        })
        infoRoot.add(speedButton).left().padTop(6f).row()

        val goldRow = Table()
        goldRow.add(Image(TextureRegionDrawable(TextureRegion(IconFactory.coin)))).size(24f, 24f).padRight(6f)
        goldRow.add(goldLabel)
        infoRoot.add(goldRow).left().padTop(8f).row()

        buildReputationBlock()
        infoRoot.add(reputationGroup).left().padTop(10f)

        stage.addActor(infoRoot)

        val debugRoot = Table()
        debugRoot.setFillParent(true)
        debugRoot.bottom().left().padBottom(16f).padLeft(20f)
        debugLabel.setFontScale(0.8f)
        debugLabel.isVisible = false
        debugRoot.add(debugLabel).left()
        stage.addActor(debugRoot)
    }

    /** Four horizontal bars, one per colonial power, running from -100 on the left through zero
     *  in the middle to +100 on the right, with the tier boundaries notched in. This is the only
     *  place the player can watch a sale push one power up and the other three down at once (see
     *  Player.recordSale), which is what makes M14's trade-off legible instead of a number that
     *  quietly drifts in the Commandant's Office. Collapsible because four permanent bars is a
     *  lot of screen to give up while sailing. */
    private fun buildReputationBlock() {
        reputationToggleLabel.setFontScale(0.9f)
        val toggle = Table()
        toggle.add(reputationToggleLabel).left()
        toggle.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                reputationExpanded = !reputationExpanded
                applyReputationExpansion()
            }
        })
        reputationGroup.add(toggle).left().row()

        for (faction in Faction.entries) {
            val name = Label(faction.displayName, skin)
            name.setFontScale(0.85f)
            val bar = Image()
            reputationBars[faction] = bar
            reputationRows.add(name).left().width(96f).padRight(6f)
            reputationRows.add(bar).size(REP_BAR_WIDTH.toFloat(), REP_BAR_HEIGHT.toFloat()).row()
        }
        reputationGroup.add(reputationRows).left()
        applyReputationExpansion()
    }

    private fun applyReputationExpansion() {
        reputationRows.isVisible = reputationExpanded
        reputationToggleLabel.setText(if (reputationExpanded) "Standing -" else "Standing +")
        reputationRows.cells.forEach { it.setActorHeight(if (reputationExpanded) REP_BAR_HEIGHT.toFloat() else 0f) }
        reputationGroup.invalidateHierarchy()
    }

    /** Rebuilds a bar's texture only when that faction's standing actually moved - this runs
     *  every frame, and a Texture per faction per frame would leak the GPU dry. */
    fun setReputation(reputation: Map<Faction, Int>) {
        for (faction in Faction.entries) {
            val value = (reputation[faction] ?: 0).coerceIn(-100, 100)
            if (lastReputation[faction] == value) continue
            lastReputation[faction] = value
            reputationTextures[faction]?.dispose()
            val texture = buildReputationBarTexture(faction, value)
            reputationTextures[faction] = texture
            reputationBars[faction]?.drawable = TextureRegionDrawable(TextureRegion(texture))
        }
    }

    private fun buildReputationBarTexture(faction: Faction, value: Int): Texture {
        val pixmap = Pixmap(REP_BAR_WIDTH, REP_BAR_HEIGHT, Pixmap.Format.RGBA8888)
        pixmap.setColor(REP_BAR_EMPTY)
        pixmap.fill()

        val zeroX = REP_BAR_WIDTH / 2
        pixmap.setColor(faction.tint)
        val valueX = reputationToX(value)
        val left = minOf(zeroX, valueX)
        val width = kotlin.math.abs(valueX - zeroX)
        if (width > 0) pixmap.fillRectangle(left, 0, width, REP_BAR_HEIGHT)

        pixmap.setColor(REP_BAR_TIER_MARK)
        for (mark in REP_TIER_MARKS) {
            pixmap.fillRectangle(reputationToX(mark), 0, 1, REP_BAR_HEIGHT)
        }
        pixmap.setColor(REP_BAR_ZERO_MARK)
        pixmap.fillRectangle(zeroX, 0, 1, REP_BAR_HEIGHT)

        val texture = Texture(pixmap)
        pixmap.dispose()
        return texture
    }

    private fun reputationToX(value: Int): Int =
        (((value + 100) / 200f) * REP_BAR_WIDTH).toInt().coerceIn(0, REP_BAR_WIDTH - 1)

    /** The F3 developer overlay - null hides it. Anchored bottom-left, out of the way of both the
     *  info column and the minimap stack. */
    fun setDebugText(text: String?) {
        debugLabel.isVisible = text != null
        if (text != null) debugLabel.setText(text)
    }

    fun setDate(text: String) {
        dateLabel.setText(text)
    }

    fun setGold(amount: Int) {
        goldLabel.setText("${amount}g")
    }

    fun setGameSpeed(multiplier: Float) {
        val text = if (multiplier == multiplier.toInt().toFloat()) "${multiplier.toInt()}x" else "${multiplier}x"
        speedLabel.setText(text)
    }

    /** Rebuilds the captain-icon row only when the active-captain list actually changed (hire/
     *  recall/loss) - NOT every call. This runs once per frame (see PortRoyaleGame's render loop),
     *  and rebuilding the actors unconditionally used to drop clicks: a click spans a touchDown
     *  frame and a touchUp frame, and Table.clear() between them tears down the exact Actor
     *  instance Scene2D's touch focus was pointed at, so the touchUp had nothing to deliver
     *  clicked() to. Comparing against the previous list (Captain has no equals() override, so
     *  this is really an identity/order comparison) keeps the same button Actors alive across
     *  frames whenever nothing changed, which is the common case. */
    fun setCaptains(captains: List<Captain>) {
        if (captains == lastCaptains) return
        lastCaptains = captains
        captainIconsGroup.clear()
        for (captain in captains) {
            val button = Table()
            button.background = skin.getDrawable("button-up")
            button.add(Image(TextureRegionDrawable(TextureRegion(IconFactory.portraitFor(captain.name.hashCode())))))
                .size(CAPTAIN_ICON_SIZE, CAPTAIN_ICON_SIZE).pad(4f)
            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onJumpToCaptain(captain)
                }
            })
            captainIconsGroup.add(button).padBottom(6f).row()
        }
    }

    /** fraction 0..1 = how much of the loan term has elapsed (fills red as it approaches 1). */
    fun setDebt(visible: Boolean, fraction: Float, countdownText: String) {
        debtGroup.isVisible = visible
        if (!visible) return
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped != lastFraction) {
            lastFraction = clamped
            debtBarTexture?.dispose()
            debtBarTexture = buildBarTexture(clamped)
            debtFillImage.drawable = TextureRegionDrawable(TextureRegion(debtBarTexture))
        }
        debtCountdownLabel.setText(countdownText)
    }

    private fun buildBarTexture(fraction: Float): Texture {
        val pixmap = Pixmap(DEBT_BAR_WIDTH, DEBT_BAR_HEIGHT, Pixmap.Format.RGBA8888)
        pixmap.setColor(DEBT_BAR_EMPTY)
        pixmap.fill()
        val filledWidth = (DEBT_BAR_WIDTH * fraction).toInt()
        if (filledWidth > 0) {
            pixmap.setColor(DEBT_BAR_FILL)
            pixmap.fillRectangle(0, 0, filledWidth, DEBT_BAR_HEIGHT)
        }
        val texture = Texture(pixmap)
        pixmap.dispose()
        return texture
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
        debtBarTexture?.dispose()
        reputationTextures.values.forEach { it.dispose() }
    }
}
