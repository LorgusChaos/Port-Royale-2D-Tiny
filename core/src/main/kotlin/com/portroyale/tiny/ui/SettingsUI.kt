package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.save.SettingsData
import com.portroyale.tiny.save.SettingsManager

private val RESOLUTIONS = listOf(1024 to 576, 1280 to 720, 1366 to 768, 1600 to 900, 1920 to 1080)
private const val LABEL_WIDTH = 150f
private const val DEFAULT_WINDOWED_WIDTH = 1280
private const val DEFAULT_WINDOWED_HEIGHT = 720

/** Volume/mute, a fullscreen toggle, and an "updates" stub. Windowed mode is always freely
 *  resizable by dragging the window - Resolution only ever changes anything while Fullscreen is
 *  on, since a windowed game already has no real use for a fixed resolution list. Applies
 *  changes immediately (no separate confirm step) and persists them to disk. */
class SettingsUI(private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var volumeSlider: Slider
    private lateinit var muteButton: TextButton
    private lateinit var fullscreenButton: TextButton
    private lateinit var resPrevButton: TextButton
    private lateinit var resNextButton: TextButton
    private lateinit var resolutionLabel: Label
    private lateinit var updateLabel: Label
    private var resolutionIndex = 1
    private var fullscreen = false
    /** True while open() moves the slider to the current volume - not a change the player made. */
    private var syncingSlider = false

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open() {
        syncingSlider = true
        volumeSlider.value = AudioManager.masterVolume
        syncingSlider = false
        updateMuteLabel()
        updateLabel.setText("")
        fullscreen = Gdx.graphics.isFullscreen
        val current = RESOLUTIONS.indexOfFirst { it.first == Gdx.graphics.width && it.second == Gdx.graphics.height }
        if (current >= 0) resolutionIndex = current
        updateFullscreenLabel()
        updateResolutionLabel()
        updateResolutionEnabled()
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
        panel.pad(48f)

        val title = Label("Settings", skin)
        title.setFontScale(1.4f)
        panel.add(title).colspan(3).padBottom(24f).row()

        panel.add(Label("Volume:", skin)).left().width(LABEL_WIDTH).padRight(10f)
        volumeSlider = Slider(0f, 1f, 0.05f, false, skin)
        volumeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                AudioManager.setMasterVolume(volumeSlider.value)
                // Applied live, but the file is written only once the drag ends (libGDX fires
                // one more change on release) - not on every pixel of it (REVIEW.md, finding 36).
                if (!volumeSlider.isDragging && !syncingSlider) persistSettings()
            }
        })
        panel.add(volumeSlider).width(200f).colspan(2).padBottom(16f).row()

        panel.add(Label("Sound:", skin)).left().width(LABEL_WIDTH).padRight(10f)
        muteButton = TextButton("", skin)
        muteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.setMuted(!AudioManager.muted)
                updateMuteLabel()
                persistSettings()
                AudioManager.playClick()
            }
        })
        panel.add(muteButton).colspan(2).width(120f).left().padBottom(16f).row()

        panel.add(Label("Fullscreen:", skin)).left().width(LABEL_WIDTH).padRight(10f)
        fullscreenButton = TextButton("", skin)
        fullscreenButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                setFullscreen(!fullscreen)
                AudioManager.playClick()
            }
        })
        panel.add(fullscreenButton).colspan(2).width(120f).left().padBottom(20f).row()

        panel.add(Label("Resolution:", skin)).left().width(LABEL_WIDTH).padRight(10f)
        val resRow = Table()
        resPrevButton = TextButton("<", skin)
        resPrevButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                resolutionIndex = (resolutionIndex - 1 + RESOLUTIONS.size) % RESOLUTIONS.size
                applyResolution()
            }
        })
        resolutionLabel = Label("", skin)
        resNextButton = TextButton(">", skin)
        resNextButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                resolutionIndex = (resolutionIndex + 1) % RESOLUTIONS.size
                applyResolution()
            }
        })
        resRow.add(resPrevButton).width(40f)
        resRow.add(resolutionLabel).width(150f)
        resRow.add(resNextButton).width(40f)
        panel.add(resRow).colspan(2).left().padBottom(6f).row()

        val resHint = Label("(only used while Fullscreen is on)", skin)
        panel.add(resHint).colspan(3).padBottom(20f).row()

        val updateButton = TextButton("Check for Updates", skin)
        updateButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                updateLabel.setText("You're on the latest version (update checking not yet implemented).")
            }
        })
        panel.add(updateButton).colspan(3).width(260f).row()
        updateLabel = Label("", skin)
        updateLabel.setWrap(true)
        panel.add(updateLabel).colspan(3).width(360f).padBottom(20f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                persistSettings()
                onBack()
            }
        })
        panel.add(backButton).colspan(3).width(140f)

        root.add(panel)
    }

    private fun setFullscreen(value: Boolean) {
        if (value) {
            // The flag and label only turn On once the switch has really happened
            // (REVIEW.md, finding 23).
            fullscreen = enterFullscreen()
        } else {
            fullscreen = false
            Gdx.graphics.setWindowedMode(DEFAULT_WINDOWED_WIDTH, DEFAULT_WINDOWED_HEIGHT)
        }
        updateFullscreenLabel()
        updateResolutionEnabled()
        persistSettings()
    }

    private fun applyResolution() {
        updateResolutionLabel()
        if (fullscreen) {
            fullscreen = enterFullscreen()
            updateFullscreenLabel()
            updateResolutionEnabled()
        }
        persistSettings()
        AudioManager.playClick()
    }

    /** Switches to the mode chosen for the selected resolution; true only if the switch happened. */
    private fun enterFullscreen(): Boolean {
        val (w, h) = RESOLUTIONS[resolutionIndex]
        // Highest refresh rate first, so the first exact match is the best one.
        val available = Gdx.graphics.displayModes.sortedByDescending { it.refreshRate }
        val mode = chooseDisplayMode(available, w, h, Gdx.graphics.displayMode) { it.width to it.height }
        return Gdx.graphics.setFullscreenMode(mode)
    }

    private fun updateResolutionLabel() {
        val (w, h) = RESOLUTIONS[resolutionIndex]
        resolutionLabel.setText("${w}x${h}")
    }

    private fun updateResolutionEnabled() {
        val touchable = if (fullscreen) Touchable.enabled else Touchable.disabled
        val color = if (fullscreen) Color.WHITE else Color(1f, 1f, 1f, 0.4f)
        resPrevButton.touchable = touchable
        resNextButton.touchable = touchable
        resPrevButton.color = color
        resNextButton.color = color
        resolutionLabel.color = color
    }

    private fun updateFullscreenLabel() {
        fullscreenButton.setText(if (fullscreen) "On" else "Off")
    }

    private fun updateMuteLabel() {
        muteButton.setText(if (AudioManager.muted) "Muted" else "On")
    }

    private fun persistSettings() {
        val (w, h) = RESOLUTIONS[resolutionIndex]
        val data = SettingsData()
        data.masterVolume = AudioManager.masterVolume
        data.muted = AudioManager.muted
        data.fullscreen = fullscreen
        data.windowWidth = w
        data.windowHeight = h
        SettingsManager.save(data)
    }
}
