package com.portroyale.tiny.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.save.SaveManager

/** Load screen: lists every save found on disk: click one to load it immediately. */
class LoadGameUI(private val onLoad: (saveName: String) -> Unit, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private val listTable = Table()

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open() {
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
                    onLoad(name)
                }
            })
            listTable.add(button).width(240f).pad(4f).row()
        }
    }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        val title = Label("Load Game", skin)
        title.setFontScale(1.4f)
        panel.add(title).padBottom(20f).row()

        panel.add(listTable).padBottom(20f).row()

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
