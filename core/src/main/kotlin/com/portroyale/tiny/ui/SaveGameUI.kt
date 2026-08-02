package com.portroyale.tiny.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.save.SaveManager

/** Save screen: type a new name, or click an existing save to overwrite it. Only reachable via
 *  Esc during an active game (PortRoyaleGame keeps the Save button on the main menu disabled
 *  otherwise), so there's always a real game to snapshot by the time this screen opens. */
class SaveGameUI(private val onSave: (saveName: String) -> Unit, private val onBack: () -> Unit) {

    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private val listTable = Table()
    private lateinit var nameField: TextField
    private lateinit var statusLabel: Label

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open() {
        nameField.text = ""
        statusLabel.setText("")
        refreshList()
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

    private fun refreshList() {
        listTable.clear()
        val saves = SaveManager.listSaves()
        if (saves.isEmpty()) {
            listTable.add(Label("No saved games yet.", skin)).pad(8f)
            return
        }
        for (name in saves) {
            val button = TextButton(name, skin)
            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    AudioManager.playClick()
                    nameField.text = name
                    stage.keyboardFocus = nameField
                }
            })
            listTable.add(button).width(240f).pad(4f).row()
        }
    }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        val title = Label("Save Game", skin)
        title.setFontScale(1.4f)
        panel.add(title).colspan(2).padBottom(16f).row()

        panel.add(Label("Existing saves (click to overwrite):", skin)).colspan(2).padBottom(6f).row()
        panel.add(listTable).colspan(2).padBottom(16f).row()

        nameField = TextField("", skin)
        nameField.maxLength = 24
        panel.add(nameField).width(200f).padRight(8f)

        val saveButton = TextButton("Save", skin)
        saveButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val name = nameField.text.trim()
                if (name.isEmpty()) {
                    statusLabel.setText("Enter a name first.")
                    return
                }
                AudioManager.playCoins()
                onSave(name)
                statusLabel.setText("Saved \"$name\".")
                refreshList()
            }
        })
        panel.add(saveButton).width(100f).row()

        statusLabel = Label("", skin)
        panel.add(statusLabel).colspan(2).padBottom(16f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(2).width(140f)

        root.add(panel)
    }
}
