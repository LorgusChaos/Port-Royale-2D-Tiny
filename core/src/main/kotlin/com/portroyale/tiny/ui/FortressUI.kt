package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.LOAN_OFFERS
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.Ports

/** Commandant's office sub-screen: standing with each colonial power, plus this port's faction
 *  offering a loan (three fixed sums, see LOAN_OFFERS) when the player has none outstanding.
 *  Repaying is only possible at the SAME faction's office that issued it - other offices just
 *  report that a debt exists elsewhere while it's open. Reached from PortHubUI; "Back" returns
 *  there. */
class FortressUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private val reputationLabels = mutableMapOf<Faction, Label>()
    private lateinit var loanPanel: Table
    private var currentPort = ""
    private var currentFaction: Faction? = null

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        currentPort = portName
        currentFaction = Ports.ALL.firstOrNull { it.name == portName }?.faction
        titleLabel.setText("$portName - Commandant's Office")
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

        loanPanel = Table()
        panel.add(loanPanel).colspan(2).width(420f).padTop(16f).padBottom(16f).row()

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

        loanPanel.clear()
        val faction = currentFaction
        val loan = player.loan

        when {
            faction == null -> {
                loanPanel.add(Label("No loans offered here.", skin)).left()
            }
            loan != null && loan.faction == faction -> {
                val status = if (loan.overdue) {
                    "OVERDUE - you owe ${loan.repayAmount}g"
                } else {
                    val remainingGameMinutes = (loan.deadlineGameMinutes - GameClock.totalGameMinutes).coerceAtLeast(0f)
                    val remainingRealMinutes = GameClock.gameMinutesToRealMinutes(remainingGameMinutes).toInt()
                    "You owe ${loan.repayAmount}g, due in ${remainingRealMinutes / 60}h ${remainingRealMinutes % 60}m"
                }
                loanPanel.add(Label("Outstanding loan:", skin)).left().padBottom(4f).row()
                loanPanel.add(Label(status, skin)).left().padBottom(8f).row()
                val repayButton = TextButton("Repay", skin)
                repayButton.addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        if (player.canRepayLoan()) {
                            player.repayLoan()
                            AudioManager.playCoins()
                            refresh()
                        }
                    }
                })
                repayButton.touchable = if (player.canRepayLoan()) Touchable.enabled else Touchable.disabled
                loanPanel.add(repayButton).width(140f).left()
            }
            loan != null -> {
                loanPanel.add(
                    Label("You already have an outstanding loan with the ${loan.faction.displayName} commandant.", skin)
                        .apply { setWrap(true) }
                ).width(400f).left()
            }
            else -> {
                loanPanel.add(Label("The commandant will lend you gold, with interest:", skin)).left().colspan(2).padBottom(8f).row()
                for (offer in LOAN_OFFERS) {
                    val termRealMinutes = GameClock.gameMinutesToRealMinutes(offer.termGameMinutes).toInt()
                    val label = Label(
                        "${offer.principal}g now - repay ${offer.repayAmount}g within ${termRealMinutes / 60}h",
                        skin
                    )
                    loanPanel.add(label).left().width(280f).pad(3f)
                    val borrowButton = TextButton("Borrow", skin)
                    borrowButton.addListener(object : ClickListener() {
                        override fun clicked(event: InputEvent?, x: Float, y: Float) {
                            if (player.canTakeLoan()) {
                                player.takeLoan(offer, faction, GameClock.totalGameMinutes)
                                AudioManager.playCoins()
                                refresh()
                            }
                        }
                    })
                    loanPanel.add(borrowButton).width(100f).pad(3f).row()
                }
            }
        }
    }
}
