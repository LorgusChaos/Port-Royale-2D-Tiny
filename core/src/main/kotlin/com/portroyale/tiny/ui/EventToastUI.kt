package com.portroyale.tiny.ui

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
import com.badlogic.gdx.utils.Align

private const val TOAST_WIDTH = 560f
private const val ICON_SIZE = 72f

/** A dismissible notification centered on screen - the game's only non-modal notification
 *  mechanism, used for sea-event results (see PortRoyaleGame.updateSeaEvents) since those
 *  shouldn't interrupt sailing with a full screen the way a port/combat does. Stays open until
 *  the player clicks the close button - no auto-timeout, so a message can't be missed by looking
 *  away for a few seconds. Needs to share input with sailing (see PortRoyaleGame's
 *  sailingWithToastInputProcessor) so the close button is actually clickable while still sailing. */
class EventToastUI {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val label = Label("", skin)
    private val icon = Image()
    private val box = Table()

    /** True while a message is on screen - PortRoyaleGame freezes ship/world simulation for as
     *  long as this holds, so an event can't sneak in extra unread time while sailing continues
     *  underneath it. */
    val isShowing: Boolean get() = box.isVisible

    init {
        label.setWrap(true)
        label.setAlignment(Align.center)
        label.setFontScale(1.1f)

        box.background = skin.getDrawable("panel")
        box.pad(20f)
        box.isVisible = false

        val closeButton = TextButton("X", skin)
        closeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                box.isVisible = false
            }
        })
        val headerRow = Table()
        headerRow.add(Table()).expandX() // spacer to push the close button to the right
        headerRow.add(closeButton).size(30f, 30f)
        box.add(headerRow).growX().row()

        box.add(icon).size(ICON_SIZE, ICON_SIZE).padTop(4f).padBottom(10f).row()
        box.add(label).width(TOAST_WIDTH)

        val root = Table()
        root.setFillParent(true)
        root.center()
        root.add(box)
        stage.addActor(root)
    }

    fun show(message: String, iconTexture: Texture) {
        label.setText(message)
        icon.drawable = TextureRegionDrawable(TextureRegion(iconTexture))
        icon.isVisible = true
        box.isVisible = true
    }

    fun render(delta: Float) {
        if (!box.isVisible) return
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
    }
}
