package com.portroyale.tiny.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.combat.CombatState

/** Modal turn-based combat screen: fire cannons or attempt to flee, one round per click. */
class CombatUI(private val onCombatEnd: (CombatOutcome) -> Unit) {

    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var playerHullLabel: Label
    private lateinit var enemyHullLabel: Label
    private lateinit var logLabel: Label

    private var combat: CombatState? = null

    init {
        root.setFillParent(true)
        root.top().left()
        root.pad(48f)
        root.background = skin.getDrawable("panel")
        stage.addActor(root)
        buildLayout()
    }

    fun open(combatState: CombatState) {
        combat = combatState
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
        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        playerHullLabel = Label("", skin)
        enemyHullLabel = Label("", skin)
        logLabel = Label("", skin)
        logLabel.setWrap(true)

        root.add(titleLabel).left().padBottom(12f).row()
        root.add(playerHullLabel).left().width(420f).row()
        root.add(enemyHullLabel).left().width(420f).padBottom(16f).row()
        root.add(logLabel).left().width(420f).padBottom(24f).row()

        val attackButton = TextButton("Fire Cannons", skin)
        attackButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val c = combat ?: return
                c.playerAttack()
                refresh()
                if (c.isOver) onCombatEnd(c.outcome)
            }
        })

        val fleeButton = TextButton("Attempt to Flee", skin)
        fleeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val c = combat ?: return
                c.playerFlee()
                refresh()
                if (c.isOver) onCombatEnd(c.outcome)
            }
        })

        val buttons = Table()
        buttons.add(attackButton).width(170f).pad(4f)
        buttons.add(fleeButton).width(170f).pad(4f)
        root.add(buttons).left()
    }

    private fun refresh() {
        val c = combat ?: return
        titleLabel.setText(c.enemyName)
        playerHullLabel.setText("Your Ship: ${c.playerHull} / ${c.playerMaxHull}")
        enemyHullLabel.setText("${c.enemyName}: ${c.enemyHull} / ${c.enemyMaxHull}")
        logLabel.setText(c.log)
    }
}
