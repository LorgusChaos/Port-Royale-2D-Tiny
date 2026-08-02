package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.save.SettingsData
import com.portroyale.tiny.save.SettingsManager

private val RESOLUTIONS = listOf(1024 to 576, 1280 to 720, 1366 to 768, 1600 to 900, 1920 to 1080)

/** Volume/mute, windowed resolution cycling, and an "updates" stub. Applies changes immediately
 *  (no separate confirm step) and persists them to disk so they survive the next launch. */
class SettingsUI(private val onBack: () -> Unit) {

    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var volumeSlider: Slider
    private lateinit var muteButton: TextButton
    private lateinit var resolutionLabel: Label
    private lateinit var updateLabel: Label
    private var resolutionIndex = 1

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open() {
        volumeSlider.value = AudioManager.masterVolume
        updateMuteLabel()
        updateLabel.setText("")
        val current = RESOLUTIONS.indexOfFirst { it.first == Gdx.graphics.width && it.second == Gdx.graphics.height }
        if (current >= 0) resolutionIndex = current
        updateResolutionLabel()
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

        val title = Label("Settings", skin)
        title.setFontScale(1.4f)
        panel.add(title).colspan(3).padBottom(24f).row()

        panel.add(Label("Volume:", skin)).left().padRight(10f)
        volumeSlider = Slider(0f, 1f, 0.05f, false, skin)
        volumeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                AudioManager.setMasterVolume(volumeSlider.value)
                persistSettings()
            }
        })
        panel.add(volumeSlider).width(200f).colspan(2).padBottom(16f).row()

        panel.add(Label("Sound:", skin)).left().padRight(10f)
        muteButton = TextButton("", skin)
        muteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.setMuted(!AudioManager.muted)
                updateMuteLabel()
                persistSettings()
                AudioManager.playClick()
            }
        })
        panel.add(muteButton).colspan(2).width(120f).left().padBottom(20f).row()

        panel.add(Label("Resolution:", skin)).left().padRight(10f)
        val resRow = Table()
        val prevButton = TextButton("<", skin)
        prevButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                resolutionIndex = (resolutionIndex - 1 + RESOLUTIONS.size) % RESOLUTIONS.size
                applyResolution()
            }
        })
        resolutionLabel = Label("", skin)
        val nextButton = TextButton(">", skin)
        nextButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                resolutionIndex = (resolutionIndex + 1) % RESOLUTIONS.size
                applyResolution()
            }
        })
        resRow.add(prevButton).width(40f)
        resRow.add(resolutionLabel).width(140f)
        resRow.add(nextButton).width(40f)
        panel.add(resRow).colspan(2).padBottom(20f).row()

        val updateButton = TextButton("Check for Updates", skin)
        updateButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                updateLabel.setText("You're on the latest version (update checking not yet implemented).")
            }
        })
        panel.add(updateButton).colspan(3).width(240f).row()
        updateLabel = Label("", skin)
        updateLabel.setWrap(true)
        panel.add(updateLabel).colspan(3).width(320f).padBottom(20f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(3).width(140f)

        root.add(panel)
    }

    private fun applyResolution() {
        val (w, h) = RESOLUTIONS[resolutionIndex]
        Gdx.graphics.setWindowedMode(w, h)
        updateResolutionLabel()
        persistSettings()
        AudioManager.playClick()
    }

    private fun updateResolutionLabel() {
        val (w, h) = RESOLUTIONS[resolutionIndex]
        resolutionLabel.setText("${w}x${h}")
    }

    private fun updateMuteLabel() {
        muteButton.setText(if (AudioManager.muted) "Muted" else "On")
    }

    private fun persistSettings() {
        val (w, h) = RESOLUTIONS[resolutionIndex]
        val data = SettingsData()
        data.masterVolume = AudioManager.masterVolume
        data.muted = AudioManager.muted
        data.windowWidth = w
        data.windowHeight = h
        SettingsManager.save(data)
    }
}
