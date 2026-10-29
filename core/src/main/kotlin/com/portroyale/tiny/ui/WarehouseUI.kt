package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.economy.WAREHOUSE_PURCHASE_PRICE

private const val GOLD_MOVE_AMOUNT = 100

// Column widths and row heights on the frame's design canvas. PortFrame.CONTENT_BOX gives 874x605
// of clear parchment (630 tall), and eleven movable things (gold plus ten goods) plus a header have to fit
// in it without a scroll bar - no other screen here has one either. The columns are sized to span
// the panel rather than huddle in its top-left corner, which is what an unsized table does.
private const val ICON_COL = 50f
private const val NAME_COL = 195f
private const val HELD_COL = 140f
private const val STORED_COL = 145f
private const val STORE_COL = 150f
private const val WITHDRAW_COL = 195f
private const val CELL_PAD = 6f
private const val HEADER_H = 48f
private const val CAPTION_H = 40f
private const val ROW_H = 49f
private const val BUTTON_H = 40f

/** Per-port private storage sub-screen: move goods and gold between the ship's hold and a
 *  warehouse the player owns in this specific port. Drawn inside the same painted shopfront as
 *  the port hub ([PortFrame]) - the nameplate carries the port and the purse, the parchment
 *  carries the rows, and the frame's own (X) goes back to the hub. Before a warehouse exists
 *  here, the panel shows a "Buy Warehouse" offer instead of the rows.
 *  [onFirstWarehousePurchased] fires exactly once, the moment the player's very first warehouse
 *  anywhere is bought - PortRoyaleGame uses it to pop the explanatory toast over this screen
 *  (EventToastUI, not something built into this screen's own Stage, so it can render and stay
 *  dismissible on top regardless of which screen triggered it). */
class WarehouseUI(
    private val player: Player,
    private val onBack: () -> Unit,
    private val onFirstWarehousePurchased: () -> Unit = {}
) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val frame = PortFrame(skin) { leave() }

    private val buyPanel = Table()
    private val storagePanel = Table()
    private lateinit var buyLabel: Label
    private lateinit var buyButton: TextButton
    private lateinit var capacityLabel: Label
    private lateinit var expandButton: TextButton
    private lateinit var goldRow: MoveRow
    private val goodRows = mutableMapOf<Good, MoveRow>()
    private var currentPort = ""

    /** One movable line: what the hold has, what the warehouse has, and the two buttons. */
    private class MoveRow(val held: Label, val stored: Label, val store: TextButton, val withdraw: TextButton)

    init {
        stage.addActor(frame.group)
        buildLayout()
        layoutFrame()
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
        layoutFrame()
    }

    fun dispose() {
        stage.dispose()
        skin.dispose()
    }

    private fun layoutFrame() = frame.layout(stage.viewport.worldWidth, stage.viewport.worldHeight)

    private fun leave() {
        AudioManager.playClick()
        onBack()
    }

    private fun buildLayout() {
        buildBuyPanel()
        buildStoragePanel()
        // One cell, two panels: only one of them is ever visible, and a hidden Table still takes
        // up its cell, which would push the other one out of the parchment.
        frame.content.add(Stack(buyPanel, storagePanel)).grow()
    }

    private fun buildBuyPanel() {
        buyPanel.center()
        buyPanel.add(Image(TextureRegionDrawable(TextureRegion(IconFactory.warehouse))))
            .size(260f, 260f).padBottom(24f).row()
        buyLabel = frame.bodyLabel(Align.center)
        buyPanel.add(buyLabel).padBottom(20f).row()
        buyButton = frame.button("Buy Warehouse") {
            if (player.canBuyWarehouse(currentPort)) {
                val isFirstEver = player.warehouses.isEmpty()
                player.buyWarehouse(currentPort)
                AudioManager.playCoins()
                refresh()
                if (isFirstEver) onFirstWarehousePurchased()
            }
        }
        buyPanel.add(buyButton).width(320f).height(BUTTON_H * 1.3f)
    }

    private fun buildStoragePanel() {
        storagePanel.top().left()

        // What this warehouse is, and the one thing you can do to the building itself.
        capacityLabel = frame.bodyLabel()
        storagePanel.add(capacityLabel)
            .colspan(4).width(ICON_COL + NAME_COL + HELD_COL + STORED_COL).height(HEADER_H).left()
        expandButton = frame.button("Expand") {
            if (player.canExpandWarehouse(currentPort)) {
                player.expandWarehouse(currentPort)
                AudioManager.playCoins()
                refresh()
            }
        }
        storagePanel.add(expandButton).colspan(2).width(STORE_COL + WITHDRAW_COL).height(BUTTON_H).row()

        // Column captions on their own line instead of "hold"/"stored" repeated down every row:
        // the cells then hold numbers only, and a six-figure pile of gold cannot run into the
        // next column.
        storagePanel.add().colspan(2).width(ICON_COL + NAME_COL).height(CAPTION_H)
        storagePanel.add(frame.bodyLabel(Align.center).apply { setText("in hold") })
            .width(HELD_COL).height(CAPTION_H)
        storagePanel.add(frame.bodyLabel(Align.center).apply { setText("in store") })
            .width(STORED_COL).height(CAPTION_H)
        storagePanel.add().colspan(2).width(STORE_COL + WITHDRAW_COL).row()

        goldRow = addRow(
            IconFactory.coin, "x$GOLD_MOVE_AMOUNT", "Store", "Withdraw",
            onStore = {
                if (player.canStoreGold(currentPort, GOLD_MOVE_AMOUNT)) {
                    player.storeGold(currentPort, GOLD_MOVE_AMOUNT)
                    AudioManager.playCoins()
                    refresh()
                }
            },
            onWithdraw = {
                if (player.canWithdrawGold(currentPort, GOLD_MOVE_AMOUNT)) {
                    player.withdrawGold(currentPort, GOLD_MOVE_AMOUNT)
                    AudioManager.playCoins()
                    refresh()
                }
            }
        )

        for (good in Good.entries) {
            goodRows[good] = addRow(
                IconFactory.iconFor(good), good.displayName, "Store", "Withdraw",
                onStore = {
                    if (player.canStoreGood(currentPort, good)) {
                        player.storeGood(currentPort, good)
                        refresh()
                    }
                },
                onWithdraw = {
                    if (player.canWithdrawGood(currentPort, good)) {
                        player.withdrawGood(currentPort, good)
                        refresh()
                    }
                }
            )
        }
    }

    private fun addRow(
        icon: Texture,
        name: String,
        storeText: String,
        withdrawText: String,
        onStore: () -> Unit,
        onWithdraw: () -> Unit
    ): MoveRow {
        storagePanel.add(Image(TextureRegionDrawable(TextureRegion(icon))))
            .size(ICON_COL - CELL_PAD * 2).height(ROW_H).padRight(CELL_PAD)
        storagePanel.add(frame.bodyLabel().apply { setText(name) }).width(NAME_COL).height(ROW_H).left()

        val held = frame.bodyLabel(Align.center)
        storagePanel.add(held).width(HELD_COL).height(ROW_H)
        val stored = frame.bodyLabel(Align.center)
        storagePanel.add(stored).width(STORED_COL).height(ROW_H)

        val store = frame.button(storeText, onClick = onStore)
        storagePanel.add(store).width(STORE_COL - CELL_PAD).height(BUTTON_H).padRight(CELL_PAD)
        val withdraw = frame.button(withdrawText, onClick = onWithdraw)
        storagePanel.add(withdraw).width(WITHDRAW_COL).height(BUTTON_H).row()

        return MoveRow(held, stored, store, withdraw)
    }

    private fun refresh() {
        frame.setTitle("Warehouse")
        frame.setStatus(currentPort, player.gold, player.cargoUsed to player.cargoCapacity)

        val warehouse = player.warehouseAt(currentPort)
        buyPanel.isVisible = warehouse == null
        storagePanel.isVisible = warehouse != null
        if (warehouse == null) {
            buyLabel.setText("No warehouse here yet - cost ${WAREHOUSE_PURCHASE_PRICE}g")
            buyButton.enable(player.canBuyWarehouse(currentPort))
            return
        }

        capacityLabel.setText("Storage: ${warehouse.goodsUsed} / ${warehouse.capacity}   (level ${warehouse.level})")
        val expansionCost = warehouse.expansionCost()
        expandButton.setText(if (expansionCost == null) "Fully expanded" else "Expand ${expansionCost}g")
        expandButton.enable(player.canExpandWarehouse(currentPort))

        goldRow.held.setText("${player.gold}g")
        goldRow.stored.setText("${warehouse.gold}g")
        goldRow.store.enable(player.canStoreGold(currentPort, GOLD_MOVE_AMOUNT))
        goldRow.withdraw.enable(player.canWithdrawGold(currentPort, GOLD_MOVE_AMOUNT))

        for ((good, row) in goodRows) {
            row.held.setText("${player.cargo[good] ?: 0}")
            row.stored.setText("${warehouse.goods[good] ?: 0}")
            row.store.enable(player.canStoreGood(currentPort, good))
            row.withdraw.enable(player.canWithdrawGood(currentPort, good))
        }
    }

    /** A move that cannot be made reads as unavailable rather than silently doing nothing: the
     *  old screen left every button looking alive and only refused the click. */
    private fun TextButton.enable(enabled: Boolean) {
        touchable = if (enabled) Touchable.enabled else Touchable.disabled
        color.a = if (enabled) 1f else 0.45f
    }
}
