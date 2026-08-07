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

private const val ICON_SIZE = 40f
private const val CAPTAIN_ICON_SIZE = 32f
private const val DEBT_BAR_WIDTH = 40
private const val DEBT_BAR_HEIGHT = 8
private val DEBT_BAR_EMPTY = Color.valueOf("2E2A24")
private val DEBT_BAR_FILL = Color.valueOf("A61C1C")

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
        infoRoot.add(goldRow).left().padTop(8f)

        stage.addActor(infoRoot)
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
    }
}
