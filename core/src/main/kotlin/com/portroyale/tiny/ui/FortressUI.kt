package com.portroyale.tiny.ui

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
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.audio.AudioManager

/** Commandant's office sub-screen: currently a stub with flavor text only. Reserved as the
 *  future home for quests/orders from the local commandant or governor; for now it just gives
 *  the city menu a third, navigable destination. Reached from PortHubUI; "Back" returns there. */
class FortressUI(private val onBack: () -> Unit) {

    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        titleLabel.setText("$portName - Commandant's Office")
    }

    fun render(delta: Float) {
        stage.viewport.apply()
        stage.act(delta)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun dispose() {
        stage.dispose()
        skin.dispose()
    }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        val icon = Image(TextureRegionDrawable(TextureRegion(IconFactory.fortress)))
        panel.add(icon).size(80f, 80f).padBottom(16f).row()

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).padBottom(16f).row()

        val bodyLabel = Label(
            "The commandant is not receiving visitors today.\nCheck back another time.",
            skin
        )
        bodyLabel.setWrap(true)
        bodyLabel.setAlignment(Align.center)
        panel.add(bodyLabel).width(360f).padBottom(24f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).width(140f)

        root.add(panel)
    }
}
