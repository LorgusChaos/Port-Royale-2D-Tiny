package com.portroyale.tiny.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.portroyale.tiny.audio.AudioManager

/** Captain creation: just a name for now - skills/background are a later addition. */
class NewGameUI(private val onBegin: (captainName: String) -> Unit, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var nameField: TextField

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open() {
        nameField.text = ""
        stage.keyboardFocus = nameField
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
    }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        val title = Label("New Captain", skin)
        title.setFontScale(1.4f)
        panel.add(title).colspan(2).padBottom(20f).row()

        panel.add(Label("Name:", skin)).left().padRight(10f)
        nameField = TextField("", skin)
        nameField.maxLength = 24
        panel.add(nameField).width(220f).row()

        val beginButton = TextButton("Begin Voyage", skin)
        beginButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBegin(nameField.text)
            }
        })
        panel.add(beginButton).colspan(2).width(200f).padTop(20f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(2).width(140f).padTop(10f)

        root.add(panel)
    }
}
