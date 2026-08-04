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
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.economy.WAREHOUSE_PURCHASE_PRICE

private const val GOLD_MOVE_AMOUNT = 100

/** Per-port private storage sub-screen: move goods and gold between the ship's hold and a
 *  warehouse the player owns in this specific port. Reached from PortHubUI; "Back" returns
 *  there. Before a warehouse exists here, this just shows a "Buy Warehouse" offer instead of the
 *  goods/gold rows. */
class WarehouseUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
    private lateinit var buyPanel: Table
    private lateinit var buyLabel: Label
    private lateinit var buyButton: TextButton
    private lateinit var storagePanel: Table
    private lateinit var capacityLabel: Label
    private lateinit var expandLabel: Label
    private lateinit var expandButton: TextButton
    private lateinit var warehouseGoldLabel: Label
    private val goodRows = mutableMapOf<Good, Label>()
    private var currentPort = ""

    init {
        root.setFillParent(true)
        root.center()
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

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(4).left().padBottom(12f).row()

        goldLabel = Label("", skin)
        panel.add(goldLabel).colspan(4).left().padBottom(20f).row()

        buyPanel = Table()
        buyLabel = Label("", skin)
        buyPanel.add(buyLabel).left().padRight(12f)
        buyButton = TextButton("Buy Warehouse", skin)
        buyButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (player.canBuyWarehouse(currentPort)) {
                    player.buyWarehouse(currentPort)
                    AudioManager.playCoins()
                    refresh()
                }
            }
        })
        buyPanel.add(buyButton).width(160f)
        panel.add(buyPanel).colspan(4).left().padBottom(16f).row()

        storagePanel = Table()

        capacityLabel = Label("", skin)
        storagePanel.add(capacityLabel).colspan(4).left().padBottom(4f).row()

        expandLabel = Label("", skin)
        storagePanel.add(expandLabel).left().padRight(12f)
        expandButton = TextButton("Expand", skin)
        expandButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (player.canExpandWarehouse(currentPort)) {
                    player.expandWarehouse(currentPort)
                    AudioManager.playCoins()
                    refresh()
                }
            }
        })
        storagePanel.add(expandButton).width(120f).colspan(3).left().row()

        val goldRow = Table()
        goldRow.add(Label("Gold", skin)).left().width(90f).pad(4f)
        warehouseGoldLabel = Label("", skin)
        goldRow.add(warehouseGoldLabel).width(220f).pad(4f)
        val storeGoldButton = TextButton("Store", skin)
        storeGoldButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (player.canStoreGold(currentPort, GOLD_MOVE_AMOUNT)) {
                    player.storeGold(currentPort, GOLD_MOVE_AMOUNT)
                    AudioManager.playCoins()
                    refresh()
                }
            }
        })
        goldRow.add(storeGoldButton).width(70f).pad(4f)
        val withdrawGoldButton = TextButton("Withdraw", skin)
        withdrawGoldButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (player.canWithdrawGold(currentPort, GOLD_MOVE_AMOUNT)) {
                    player.withdrawGold(currentPort, GOLD_MOVE_AMOUNT)
                    AudioManager.playCoins()
                    refresh()
                }
            }
        })
        goldRow.add(withdrawGoldButton).width(80f).pad(4f)
        storagePanel.add(goldRow).colspan(4).left().padTop(8f).padBottom(4f).row()

        for (good in Good.entries) {
            val icon = Image(TextureRegionDrawable(TextureRegion(IconFactory.iconFor(good))))
            val statusLabel = Label("", skin)
            goodRows[good] = statusLabel

            val storeButton = TextButton("Store", skin)
            storeButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (player.canStoreGood(currentPort, good)) {
                        player.storeGood(currentPort, good)
                        refresh()
                    }
                }
            })
            val withdrawButton = TextButton("Withdraw", skin)
            withdrawButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (player.canWithdrawGood(currentPort, good)) {
                        player.withdrawGood(currentPort, good)
                        refresh()
                    }
                }
            })

            storagePanel.add(icon).size(28f, 28f).pad(4f)
            storagePanel.add(statusLabel).width(220f).pad(4f)
            storagePanel.add(storeButton).width(70f).pad(4f)
            storagePanel.add(withdrawButton).width(80f).pad(4f)
            storagePanel.row()
        }

        panel.add(storagePanel).colspan(4).left().row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(4).left().width(140f).padTop(20f)

        root.add(panel)
    }

    private fun refresh() {
        titleLabel.setText("$currentPort - Warehouse")
        goldLabel.setText("Gold: ${player.gold}")

        val warehouse = player.warehouseAt(currentPort)
        if (warehouse == null) {
            buyPanel.isVisible = true
            storagePanel.isVisible = false
            buyLabel.setText("No warehouse here yet - cost ${WAREHOUSE_PURCHASE_PRICE}g")
            buyButton.touchable = touchableFor(player.canBuyWarehouse(currentPort))
        } else {
            buyPanel.isVisible = false
            storagePanel.isVisible = true
            capacityLabel.setText("Storage: ${warehouse.goodsUsed} / ${warehouse.capacity}  (level ${warehouse.level})")
            val expansionCost = warehouse.expansionCost()
            expandLabel.setText(
                if (expansionCost == null) "Fully expanded" else "Expand for ${expansionCost}g"
            )
            expandButton.touchable = touchableFor(player.canExpandWarehouse(currentPort))
            warehouseGoldLabel.setText("${warehouse.gold}g stored (move $GOLD_MOVE_AMOUNT at a time)")
            for ((good, label) in goodRows) {
                val held = player.cargo[good] ?: 0
                val stored = warehouse.goods[good] ?: 0
                label.setText("hold: $held / stored: $stored")
            }
        }
    }

    private fun touchableFor(enabled: Boolean) =
        if (enabled) Touchable.enabled else Touchable.disabled
}
