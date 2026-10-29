package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
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

// Columns and row heights on the frame's design canvas. PortFrameArt.MARKET leaves 828x621 of
// clear parchment between the stalls, and ten goods plus two header lines have to fit in it
// without a scroll bar - no other screen here has one. 44 + 38 + 10x52 = 602 of the 621.
private const val BUY_PRICE_COL = 92f
private const val BUY_COL = 120f
private const val ICON_COL = 46f
private const val NAME_COL = 230f
private const val SELL_PRICE_COL = 92f
private const val SELL_COL = 120f
private const val HOLD_COL = 78f
private const val CELL_PAD = 4f
private const val HEADER_H = 44f
private const val CAPTION_H = 38f
private const val ROW_H = 52f
/** The painted Buy/Sell plates are 452x139, so their height follows from the column width - a
 *  plate stretched to a taller cell would just look stretched. */
private const val BUTTON_H = 36f
private const val BUTTON_FONT_SCALE = 0.72f

/** Goods market sub-screen: buy/sell one unit at a time. Price = port's historical bias x
 *  current supply/demand (MarketState) x buy/sell spread x reputation with the port's faction -
 *  buying nudges that good's stock down (price rises), selling nudges it up (price falls).
 *  Selling also takes a side: it raises standing with this port's faction and lowers it with the
 *  other three (see Player.recordSale); buying is neutral. Drawn inside the market's own painted
 *  shopfront ([PortFrameArt.MARKET]) - the nameplate carries the port and the purse, the
 *  parchment carries the rows, and the frame's painted (X) returns to the hub. */
class MarketUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val frame = PortFrame(skin, PortFrameArt.MARKET) { leave() }

    private lateinit var standingLabel: Label
    private val buyPriceLabels = mutableMapOf<Good, Label>()
    private val sellPriceLabels = mutableMapOf<Good, Label>()
    private val holdLabels = mutableMapOf<Good, Label>()
    private val notabilityFrames = mutableMapOf<Good, Image>()
    private var currentPort = ""
    private var portFaction: Faction? = null

    // Same visual language as the map's surplus/shortage icons (see IconFactory) - built once
    // and reused rather than re-wrapped every refresh().
    private val surplusDrawable = TextureRegionDrawable(TextureRegion(IconFactory.surplusFrame))
    private val shortageDrawable = TextureRegionDrawable(TextureRegion(IconFactory.shortageFrame))

    init {
        stage.addActor(frame.group)
        buildLayout()
        layoutFrame()
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
        val panel = Table()
        panel.top().left()
        val columns = 7

        standingLabel = frame.bodyLabel()
        panel.add(standingLabel).colspan(columns).height(HEADER_H).left().row()

        // Captions on their own line rather than a currency letter in every cell: the old screen
        // told buy from sell by which side of the row the price pill sat on, which stops working
        // once both are plain text on parchment.
        panel.add(caption("buy")).width(BUY_PRICE_COL + BUY_COL).colspan(2).height(CAPTION_H)
        panel.add().colspan(2).width(ICON_COL + NAME_COL).height(CAPTION_H)
        panel.add(caption("sell")).width(SELL_PRICE_COL + SELL_COL).colspan(2).height(CAPTION_H)
        panel.add(caption("hold")).width(HOLD_COL).height(CAPTION_H).row()

        for (good in Good.entries) {
            val buyPriceLabel = frame.bodyLabel(Align.center)
            buyPriceLabels[good] = buyPriceLabel
            panel.add(buyPriceLabel).width(BUY_PRICE_COL).height(ROW_H)

            val buyButton = frame.plateButton("Buy", "button_buy", BUTTON_FONT_SCALE) {
                val price = buyPrice(good)
                if (player.canBuy(price)) {
                    player.buy(good, price)
                    MarketState.onBuy(currentPort, good)
                    // Buying takes no side and so moves no reputation - see Player.recordSale.
                    AudioManager.playCoins()
                    refresh()
                }
            }
            panel.add(buyButton).width(BUY_COL - CELL_PAD).height(BUTTON_H).padRight(CELL_PAD)

            // A surplus/shortage frame sits behind the icon (same visual language as the map),
            // toggled per-refresh via notabilityFrames rather than swapping the icon Image itself.
            val icon = Image(TextureRegionDrawable(TextureRegion(IconFactory.iconFor(good))))
            val notability = Image()
            notabilityFrames[good] = notability
            val iconInset = Table()
            iconInset.add(icon).size(ICON_COL - CELL_PAD * 2)
            panel.add(Stack(notability, iconInset)).size(ICON_COL).height(ROW_H)

            panel.add(frame.bodyLabel().apply { setText(good.displayName) })
                .width(NAME_COL).height(ROW_H).left()

            val sellPriceLabel = frame.bodyLabel(Align.center)
            sellPriceLabels[good] = sellPriceLabel
            panel.add(sellPriceLabel).width(SELL_PRICE_COL).height(ROW_H)

            val sellButton = frame.plateButton("Sell", "button_sell", BUTTON_FONT_SCALE) {
                val price = sellPrice(good)
                if (player.canSell(good)) {
                    player.sell(good, price)
                    MarketState.onSell(currentPort, good)
                    player.recordSale(portFaction, price)
                    AudioManager.playCoins()
                    refresh()
                }
            }
            panel.add(sellButton).width(SELL_COL - CELL_PAD).height(BUTTON_H).padRight(CELL_PAD)

            val holdLabel = frame.bodyLabel(Align.center)
            holdLabels[good] = holdLabel
            panel.add(holdLabel).width(HOLD_COL).height(ROW_H).row()
        }

        frame.content.add(panel).grow()
    }

    private fun caption(text: String) = frame.bodyLabel(Align.center).apply { setText(text) }

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

    private fun refresh() {
        frame.setTitle("Market")
        frame.setStatus(currentPort, player.gold, player.cargoUsed to player.cargoCapacity)
        val faction = portFaction
        standingLabel.setText(
            if (faction != null) {
                "${faction.displayName} standing: ${player.reputationTier(faction).label} " +
                    "(${player.reputation[faction]})   -   ${player.ship.name}"
            } else {
                player.ship.name
            }
        )
        for (good in Good.entries) {
            buyPriceLabels.getValue(good).setText(buyPrice(good).toString())
            sellPriceLabels.getValue(good).setText(sellPrice(good).toString())
            val held = player.cargo[good] ?: 0
            holdLabels.getValue(good).setText("x$held")
            notabilityFrames.getValue(good).drawable = when (MarketState.notability(currentPort, good)) {
                true -> surplusDrawable
                false -> shortageDrawable
                null -> null
            }
        }
    }
}
