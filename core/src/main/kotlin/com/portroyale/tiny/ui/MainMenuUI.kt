package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.portroyale.tiny.audio.AudioManager

/** Title screen and Esc pause menu - the same screen serves both roles. Continue and Save only
 *  make sense once a game is actually running, so they're disabled at first launch and enabled
 *  from the moment New Game/Load starts a session. */
class MainMenuUI(
    private val onContinue: () -> Unit,
    private val onNewGame: () -> Unit,
    private val onLoad: () -> Unit,
    private val onSave: () -> Unit,
    private val onSettings: () -> Unit
) {
    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var continueButton: TextButton
    private lateinit var saveButton: TextButton

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(gameActive: Boolean) {
        setEnabled(continueButton, gameActive)
        setEnabled(saveButton, gameActive)
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

        val title = Label("Port Royale 2D Tiny", skin)
        title.setFontScale(1.6f)
        panel.add(title).padBottom(30f).row()

        continueButton = menuButton("Continue") {
            AudioManager.playClick()
            onContinue()
        }
        panel.add(continueButton).width(220f).pad(6f).row()

        panel.add(menuButton("New Game") {
            AudioManager.playClick()
            onNewGame()
        }).width(220f).pad(6f).row()

        panel.add(menuButton("Load") {
            AudioManager.playClick()
            onLoad()
        }).width(220f).pad(6f).row()

        saveButton = menuButton("Save") {
            AudioManager.playClick()
            onSave()
        }
        panel.add(saveButton).width(220f).pad(6f).row()

        panel.add(menuButton("Settings") {
            AudioManager.playClick()
            onSettings()
        }).width(220f).pad(6f).row()

        root.add(panel)
    }

    private fun menuButton(label: String, onClick: () -> Unit): TextButton {
        val button = TextButton(label, skin)
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onClick()
            }
        })
        return button
    }

    private fun setEnabled(button: TextButton, enabled: Boolean) {
        button.touchable = if (enabled) Touchable.enabled else Touchable.disabled
        button.color = if (enabled) Color.WHITE else Color(1f, 1f, 1f, 0.4f)
    }
}
