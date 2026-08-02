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
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Market
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.buyPriceMultiplier
import com.portroyale.tiny.factions.sellPriceMultiplier
import com.portroyale.tiny.world.Ports

private const val REP_GAIN_PER_TRADE = 1

/** Goods market sub-screen: buy/sell one unit at a time, per-port pricing (adjusted further by
 *  reputation with the port's faction), one row per good with a small icon. Every completed
 *  trade nudges reputation with that faction up slightly. Reached from PortHubUI; "Back" returns
 *  to the hub, not to open sea. */
class MarketUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
    private lateinit var cargoLabel: Label
    private lateinit var reputationLabel: Label
    private val priceLabels = mutableMapOf<Good, Label>()
    private var currentPort = ""
    private var portFaction: Faction? = null

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        currentPort = portName
        portFaction = Ports.ALL.firstOrNull { it.name == portName }?.faction
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

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(5).left().padBottom(12f).row()
        goldLabel = Label("", skin)
        panel.add(goldLabel).colspan(5).left().row()
        cargoLabel = Label("", skin)
        panel.add(cargoLabel).colspan(5).left().row()
        reputationLabel = Label("", skin)
        panel.add(reputationLabel).colspan(5).left().padBottom(20f).row()

        for (good in Good.entries) {
            val icon = Image(TextureRegionDrawable(TextureRegion(IconFactory.iconFor(good))))
            val priceLabel = Label("", skin)
            priceLabels[good] = priceLabel

            val buyButton = TextButton("Buy", skin)
            buyButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val price = buyPrice(good)
                    if (player.canBuy(price)) {
                        player.buy(good, price)
                        gainReputation()
                        AudioManager.playCoins()
                        refresh()
                    }
                }
            })

            val sellButton = TextButton("Sell", skin)
            sellButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val price = sellPrice(good)
                    if (player.canSell(good)) {
                        player.sell(good, price)
                        gainReputation()
                        AudioManager.playCoins()
                        refresh()
                    }
                }
            })

            panel.add(icon).size(32f, 32f).pad(4f)
            panel.add(Label(good.displayName, skin)).left().width(90f).pad(4f)
            panel.add(priceLabel).width(230f).pad(4f)
            panel.add(buyButton).width(70f).pad(4f)
            panel.add(sellButton).width(70f).pad(4f)
            panel.row()
        }

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(5).left().width(140f).padTop(20f)

        root.add(panel)
    }

    private fun basePriceAt(good: Good): Int = Market.priceAt(currentPort, good)

    private fun buyPrice(good: Good): Int {
        val faction = portFaction ?: return basePriceAt(good)
        val rep = player.reputation[faction] ?: 0
        return (basePriceAt(good) * buyPriceMultiplier(rep)).toInt()
    }

    private fun sellPrice(good: Good): Int {
        val faction = portFaction ?: return basePriceAt(good)
        val rep = player.reputation[faction] ?: 0
        return (basePriceAt(good) * sellPriceMultiplier(rep)).toInt()
    }

    private fun gainReputation() {
        portFaction?.let { player.adjustReputation(it, REP_GAIN_PER_TRADE) }
    }

    private fun refresh() {
        titleLabel.setText("$currentPort - Market")
        goldLabel.setText("Gold: ${player.gold}")
        cargoLabel.setText("Cargo hold: ${player.cargoUsed} / ${player.cargoCapacity}  (${player.ship.name})")
        val faction = portFaction
        reputationLabel.setText(
            if (faction != null) {
                "${faction.displayName} standing: ${player.reputationTier(faction).label} (${player.reputation[faction]})"
            } else {
                ""
            }
        )
        for ((good, label) in priceLabels) {
            val held = player.cargo[good] ?: 0
            label.setText("buy ${buyPrice(good)}g / sell ${sellPrice(good)}g  (hold: $held)")
        }
    }
}
