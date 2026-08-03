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
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.buyPriceMultiplier
import com.portroyale.tiny.factions.sellPriceMultiplier
import com.portroyale.tiny.world.Ports

private const val REP_GAIN_PER_TRADE = 1

/** Goods market sub-screen: buy/sell one unit at a time. Price = port's historical bias x
 *  current supply/demand (MarketState) x buy/sell spread x reputation with the port's faction -
 *  buying nudges that good's stock down (price rises), selling nudges it up (price falls), and
 *  every trade nudges reputation up slightly. Reached from PortHubUI; "Back" returns to the hub,
 *  not to open sea. */
class MarketUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
    private lateinit var cargoLabel: Label
    private lateinit var reputationLabel: Label
    private val buyPriceLabels = mutableMapOf<Good, Label>()
    private val sellPriceLabels = mutableMapOf<Good, Label>()
    private val holdLabels = mutableMapOf<Good, Label>()
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

        val columns = 7
        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(columns).left().padBottom(12f).row()
        goldLabel = Label("", skin)
        panel.add(goldLabel).colspan(columns).left().row()
        cargoLabel = Label("", skin)
        panel.add(cargoLabel).colspan(columns).left().row()
        reputationLabel = Label("", skin)
        panel.add(reputationLabel).colspan(columns).left().padBottom(20f).row()

        // Row layout mirrors the classic trading-game look: a boxed buy price on the left, the
        // good's icon/name in the middle, a boxed sell price on the right - rather than one
        // combined "buy Xg / sell Yg" sentence. Plain numbers (no currency letter) since the
        // pill's position already tells you which price it is.
        for (good in Good.entries) {
            val buyPriceLabel = Label("", skin, "price")
            buyPriceLabel.setAlignment(Align.center)
            buyPriceLabels[good] = buyPriceLabel

            val buyButton = TextButton("Buy", skin)
            buyButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val price = buyPrice(good)
                    if (player.canBuy(price)) {
                        player.buy(good, price)
                        MarketState.onBuy(currentPort, good)
                        gainReputation()
                        AudioManager.playCoins()
                        refresh()
                    }
                }
            })

            val icon = Image(TextureRegionDrawable(TextureRegion(IconFactory.iconFor(good))))

            val sellPriceLabel = Label("", skin, "price")
            sellPriceLabel.setAlignment(Align.center)
            sellPriceLabels[good] = sellPriceLabel

            val sellButton = TextButton("Sell", skin)
            sellButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val price = sellPrice(good)
                    if (player.canSell(good)) {
                        player.sell(good, price)
                        MarketState.onSell(currentPort, good)
                        gainReputation()
                        AudioManager.playCoins()
                        refresh()
                    }
                }
            })

            val holdLabel = Label("", skin)
            holdLabels[good] = holdLabel

            panel.add(buyPriceLabel).width(64f).pad(4f)
            panel.add(buyButton).width(64f).pad(4f)
            panel.add(icon).size(32f, 32f).pad(4f)
            panel.add(Label(good.displayName, skin)).left().width(90f).pad(4f)
            panel.add(sellPriceLabel).width(64f).pad(4f)
            panel.add(sellButton).width(64f).pad(4f)
            panel.add(holdLabel).width(50f).pad(4f)
            panel.row()
        }

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(columns).left().width(140f).padTop(20f)

        root.add(panel)
    }

    private fun buyPrice(good: Good): Int {
        val base = MarketState.buyPrice(currentPort, good)
        val faction = portFaction ?: return base
        val rep = player.reputation[faction] ?: 0
        return (base * buyPriceMultiplier(rep)).toInt().coerceAtLeast(1)
    }

    private fun sellPrice(good: Good): Int {
        val base = MarketState.sellPrice(currentPort, good)
        val faction = portFaction ?: return base
        val rep = player.reputation[faction] ?: 0
        return (base * sellPriceMultiplier(rep)).toInt().coerceAtLeast(1)
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
        for (good in Good.entries) {
            buyPriceLabels.getValue(good).setText(buyPrice(good).toString())
            sellPriceLabels.getValue(good).setText(sellPrice(good).toString())
            val held = player.cargo[good] ?: 0
            holdLabels.getValue(good).setText("×$held")
        }
    }
}
