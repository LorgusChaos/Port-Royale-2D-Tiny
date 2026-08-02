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
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction

/** Commandant's office sub-screen: shows standing with each of the four colonial powers - the
 *  one real piece of content here so far. Still reserved as the future home for quests/orders
 *  from the local commandant or governor (footer note). Reached from PortHubUI; "Back" returns
 *  there. */
class FortressUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private val reputationLabels = mutableMapOf<Faction, Label>()

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        titleLabel.setText("$portName - Commandant's Office")
        refresh()
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
        panel.add(icon).size(80f, 80f).colspan(2).padBottom(16f).row()

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(2).padBottom(16f).row()

        val standingLabel = Label("Standing with the colonial powers:", skin)
        panel.add(standingLabel).colspan(2).left().padBottom(8f).row()

        for (faction in Faction.entries) {
            val nameLabel = Label(faction.displayName, skin)
            panel.add(nameLabel).left().width(140f).pad(4f)
            val valueLabel = Label("", skin)
            reputationLabels[faction] = valueLabel
            panel.add(valueLabel).left().width(200f).pad(4f).row()
        }

        val bodyLabel = Label(
            "The commandant has no orders for you today - check back another time.",
            skin
        )
        bodyLabel.setWrap(true)
        bodyLabel.setAlignment(Align.center)
        panel.add(bodyLabel).colspan(2).width(360f).padTop(16f).padBottom(24f).row()

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

    private fun refresh() {
        for ((faction, label) in reputationLabels) {
            val value = player.reputation[faction] ?: 0
            label.setText("${player.reputationTier(faction).label} ($value)")
        }
    }
}
