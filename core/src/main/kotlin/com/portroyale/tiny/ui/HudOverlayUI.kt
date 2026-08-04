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

private const val ICON_SIZE = 40f
private const val DEBT_BAR_WIDTH = 40
private const val DEBT_BAR_HEIGHT = 8
private val DEBT_BAR_EMPTY = Color.valueOf("2E2A24")
private val DEBT_BAR_FILL = Color.valueOf("A61C1C")

/** Small persistent icon buttons anchored under the minimap - the Property button (always
 *  visible while sailing) and the debt indicator (only visible with an outstanding loan, see
 *  PortRoyaleGame's loan handling; the bar fills red as the deadline approaches). A dedicated
 *  Stage sharing input with sailing (see PortRoyaleGame's sailingWithToastInputProcessor), same
 *  pattern as EventToastUI. */
class HudOverlayUI(private val onOpenProperty: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val debtGroup = Table()
    private val debtFillImage = Image()
    private val debtCountdownLabel = Label("", skin)
    private var debtBarTexture: Texture? = null
    private var lastFraction = -1f

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

        debtGroup.isVisible = false
        val debtIcon = Image(TextureRegionDrawable(TextureRegion(IconFactory.debt)))
        debtGroup.add(debtIcon).size(ICON_SIZE, ICON_SIZE).padTop(10f).row()
        debtGroup.add(debtFillImage).size(DEBT_BAR_WIDTH.toFloat(), DEBT_BAR_HEIGHT.toFloat()).row()
        debtCountdownLabel.setAlignment(Align.center)
        debtCountdownLabel.setFontScale(0.8f)
        debtGroup.add(debtCountdownLabel).padTop(2f)
        root.add(debtGroup)

        stage.addActor(root)
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
