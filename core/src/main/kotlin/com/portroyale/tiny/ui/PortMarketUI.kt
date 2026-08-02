package com.portroyale.tiny.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Market
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

/** Simple modal port screen: a goods market (buy/sell one unit at a time) plus a shipyard. */
class PortMarketUI(private val player: Player, private val onSetSail: () -> Unit) {

    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
    private lateinit var cargoLabel: Label
    private val priceLabels = mutableMapOf<Good, Label>()
    private val shipStatusLabels = mutableMapOf<ShipType, Label>()
    private var currentPort = ""

    init {
        root.setFillParent(true)
        root.top().left()
        root.pad(48f)
        root.background = skin.getDrawable("panel")
        stage.addActor(root)
        buildLayout()
    }

    fun open(portName: String) {
        currentPort = portName
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
        goldLabel = Label("", skin)
        cargoLabel = Label("", skin)

        root.add(titleLabel).left().padBottom(12f).row()
        root.add(goldLabel).left().row()
        root.add(cargoLabel).left().padBottom(20f).row()

        val goodsTable = Table()
        for (good in Good.entries) {
            val priceLabel = Label("", skin)
            priceLabels[good] = priceLabel

            val buyButton = TextButton("Buy", skin)
            buyButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val price = Market.priceAt(currentPort, good)
                    if (player.canBuy(price)) {
                        player.buy(good, price)
                        refresh()
                    }
                }
            })

            val sellButton = TextButton("Sell", skin)
            sellButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val price = Market.priceAt(currentPort, good)
                    if (player.canSell(good)) {
                        player.sell(good, price)
                        refresh()
                    }
                }
            })

            goodsTable.add(Label(good.displayName, skin)).left().width(110f).pad(4f)
            goodsTable.add(priceLabel).width(140f).pad(4f)
            goodsTable.add(buyButton).width(70f).pad(4f)
            goodsTable.add(sellButton).width(70f).pad(4f)
            goodsTable.row()
        }
        root.add(goodsTable).left().row()

        val shipyardLabel = Label("Shipyard", skin)
        shipyardLabel.setFontScale(1.1f)
        root.add(shipyardLabel).left().padTop(20f).padBottom(8f).row()

        val shipyardTable = Table()
        for (shipType in ShipTypes.ALL) {
            val statusLabel = Label("", skin)
            shipStatusLabels[shipType] = statusLabel

            val buyButton = TextButton("Buy", skin)
            buyButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (player.canBuyShip(shipType)) {
                        player.buyShip(shipType)
                        refresh()
                    }
                }
            })

            shipyardTable.add(Label(shipType.name, skin)).left().width(110f).pad(4f)
            shipyardTable.add(statusLabel).width(320f).pad(4f)
            shipyardTable.add(buyButton).width(70f).pad(4f)
            shipyardTable.row()
        }
        root.add(shipyardTable).left().row()

        val setSailButton = TextButton("Set Sail", skin)
        setSailButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onSetSail()
            }
        })
        root.add(setSailButton).left().width(140f).padTop(24f)
    }

    private fun refresh() {
        titleLabel.setText(currentPort)
        goldLabel.setText("Gold: ${player.gold}")
        cargoLabel.setText("Cargo hold: ${player.cargoUsed} / ${player.cargoCapacity}  (${player.ship.name})")
        for ((good, label) in priceLabels) {
            val price = Market.priceAt(currentPort, good)
            val held = player.cargo[good] ?: 0
            label.setText("$price g  (hold: $held)")
        }
        for ((shipType, label) in shipStatusLabels) {
            label.setText(
                if (shipType == player.ship) {
                    "owned - cargo ${shipType.cargoCapacity}, hull ${shipType.maxHull}"
                } else {
                    "${shipType.price}g - cargo ${shipType.cargoCapacity}, hull ${shipType.maxHull}"
                }
            )
        }
    }
}
