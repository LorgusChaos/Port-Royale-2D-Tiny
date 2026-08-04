package com.portroyale.tiny.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player

/** "Имущество" - a read-only list of every port where the player owns a warehouse with anything
 *  actually stored in it (empty warehouses don't show up). Opened from the HUD icon under the
 *  minimap; "Back" returns to sailing. */
class PropertyUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private val listPanel = Table()

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open() {
        refresh()
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

        val titleLabel = Label("Property", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).left().padBottom(16f).row()

        panel.add(listPanel).left().width(420f).padBottom(20f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).left().width(140f)

        root.add(panel)
    }

    private fun refresh() {
        listPanel.clear()
        val entries = player.warehouses.entries
            .filter { (_, w) -> w.goodsUsed > 0 || w.gold > 0 }
            .sortedBy { it.key }

        if (entries.isEmpty()) {
            listPanel.add(Label("Nothing stored anywhere yet.", skin)).left().row()
            return
        }

        for ((portName, warehouse) in entries) {
            val nameLabel = Label(portName, skin)
            nameLabel.setFontScale(1.05f)
            listPanel.add(nameLabel).left().padTop(8f).row()

            if (warehouse.gold > 0) {
                listPanel.add(Label("  Gold: ${warehouse.gold}", skin)).left().row()
            }
            for ((good, qty) in warehouse.goods) {
                if (qty > 0) {
                    listPanel.add(Label("  ${good.displayName}: $qty", skin)).left().row()
                }
            }
        }
    }
}
