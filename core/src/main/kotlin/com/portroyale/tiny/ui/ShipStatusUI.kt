package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.MAX_UPGRADE_LEVEL

private const val BAR_WIDTH = 180
private const val BAR_HEIGHT = 16
private val BAR_EMPTY = Color.valueOf("1C140B")
private val BAR_BORDER = Color.valueOf("241609")
private val HULL_FILL = Color.valueOf("8A2E2E")
private val FOULING_FILL = Color.valueOf("4E6B3A")

// PortFrameArt.SHIPYARD leaves 729x665 of clear parchment, and this screen is the fullest of the
// four: six stats, three upgrades, two repairs and two ways on.
//
// One three-column grid for every row, because a Table shares its column widths down the whole
// table: mixing a two-column stat row with a three-column upgrade row let the widest cell of one
// kind set the position of the other kind's button, and pushed it off the parchment. Rows that
// need fewer columns span them instead.
private const val NAME_COL = 250f
private const val VALUE_COL = 300f
private const val BUTTON_COL = 160f
private const val STAT_ROW_H = 40f
private const val SECTION_H = 42f
private const val ACTION_ROW_H = 46f
private const val BUTTON_H = 40f

/** The primary shipyard screen: current ship's condition and full stat readout (with bars for
 *  hull and fouling instead of raw numbers alone), plus permanent upgrades (cannons/oars/sails)
 *  and one-off maintenance (hull cleaning, repair). Reached directly from PortHubUI - this, not
 *  the ship-buying grid, is what the hub's "Shipyard" opens - and the frame's painted (X) returns
 *  there. "Buy a Ship" opens [ShipyardUI] as a sub-screen, inside the same painted shopfront.
 *  Balance here is a rough first pass, not tuned. */
class ShipStatusUI(
    private val player: Player,
    private val onBack: () -> Unit,
    private val onOpenShipyard: () -> Unit,
    private val onOpenDocks: () -> Unit
) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val frame = PortFrame(skin, PortFrameArt.SHIPYARD) { leave() }

    private lateinit var cargoValue: Label
    private lateinit var speedValue: Label
    private lateinit var maneuverValue: Label
    private lateinit var damageValue: Label
    private lateinit var hullBar: Image
    private lateinit var hullValue: Label
    private lateinit var foulingBar: Image
    private lateinit var foulingValue: Label
    private var hullBarTexture: Texture? = null
    private var foulingBarTexture: Texture? = null
    private lateinit var cannonRow: UpgradeRow
    private lateinit var oarRow: UpgradeRow
    private lateinit var sailRow: UpgradeRow
    private lateinit var cleanLabel: Label
    private lateinit var cleanButton: TextButton
    private lateinit var repairLabel: Label
    private lateinit var repairButton: TextButton

    init {
        stage.addActor(frame.group)
        buildLayout()
        layoutFrame()
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
        layoutFrame()
    }

    fun dispose() {
        stage.dispose()
        skin.dispose()
        hullBarTexture?.dispose()
        foulingBarTexture?.dispose()
    }

    private fun layoutFrame() = frame.layout(stage.viewport.worldWidth, stage.viewport.worldHeight)

    private fun leave() {
        AudioManager.playClick()
        onBack()
    }

    private fun buildLayout() {
        val panel = Table()
        panel.top().left()

        cargoValue = statRow(panel, "Cargo", IconFactory.cargo)
        val hullRow = barRow(panel, "Hull")
        hullBar = hullRow.first
        hullValue = hullRow.second
        val foulingRow = barRow(panel, "Fouling")
        foulingBar = foulingRow.first
        foulingValue = foulingRow.second
        speedValue = statRow(panel, "Speed")
        maneuverValue = statRow(panel, "Maneuverability")
        damageValue = statRow(panel, "Damage")

        panel.add(section("Upgrades")).colspan(3).height(SECTION_H).padTop(14f).left().row()
        cannonRow = upgradeRow(panel, "Cannons") { player.upgradeCannons() }
        oarRow = upgradeRow(panel, "Extra Oars") { player.upgradeOars() }
        sailRow = upgradeRow(panel, "Extra Sails") { player.upgradeSails() }

        panel.add(section("Maintenance")).colspan(3).height(SECTION_H).padTop(14f).left().row()

        cleanLabel = frame.bodyLabel()
        panel.add(cleanLabel).colspan(2).width(NAME_COL + VALUE_COL).height(ACTION_ROW_H).left()
        cleanButton = frame.button("Clean Hull") {
            if (player.canCleanHull()) {
                player.cleanHull()
                AudioManager.playCoins()
                refresh()
            }
        }
        panel.add(cleanButton).width(BUTTON_COL).height(BUTTON_H).row()

        repairLabel = frame.bodyLabel()
        panel.add(repairLabel).colspan(2).width(NAME_COL + VALUE_COL).height(ACTION_ROW_H).left()
        repairButton = frame.button("Repair") {
            if (player.canRepair()) {
                player.repair()
                AudioManager.playCoins()
                refresh()
            }
        }
        panel.add(repairButton).width(BUTTON_COL).height(BUTTON_H).row()

        // The two ways on from here. There is no "Back" button: the frame's painted (X) is it.
        val ways = Table()
        ways.add(frame.button("Buy a Ship") {
            AudioManager.playClick()
            onOpenShipyard()
        }).width(240f).height(BUTTON_H).padRight(24f)
        ways.add(frame.button("Docks") {
            AudioManager.playClick()
            onOpenDocks()
        }).width(240f).height(BUTTON_H)
        panel.add(ways).colspan(3).padTop(14f).left().row()

        frame.content.add(panel).grow()
    }

    private fun section(text: String) = frame.bodyLabel().apply {
        setText(text)
        setFontScale(1.15f)
    }

    /** Plain "label ......... value" row, returns the value Label to update on refresh. Pass an
     *  [icon] instead of a name where the game has a picture for the thing - the hold is shown as
     *  its barrel rather than the word "Cargo". */
    private fun statRow(panel: Table, name: String, icon: Texture? = null): Label {
        if (icon != null) {
            val holder = Table()
            holder.left()
            val height = STAT_ROW_H * 0.86f
            holder.add(Image(TextureRegionDrawable(TextureRegion(icon))))
                .size(height * icon.width / icon.height, height)
            panel.add(holder).left().width(NAME_COL).height(STAT_ROW_H)
        } else {
            panel.add(frame.bodyLabel().apply { setText(name) }).left().width(NAME_COL).height(STAT_ROW_H)
        }
        val value = frame.bodyLabel()
        panel.add(value).colspan(2).left().width(VALUE_COL + BUTTON_COL).height(STAT_ROW_H).row()
        return value
    }

    /** Same shape as [statRow] but with a small fill-bar Image ahead of the numeric value,
     *  redrawn (not scaled) each refresh so it always shows a crisp, un-blurred fraction. The bar
     *  and the number share one spanned cell: given a cell of their own they stretch to fill it,
     *  and a 16px bar came out as tall as the row. */
    private fun barRow(panel: Table, name: String): Pair<Image, Label> {
        panel.add(frame.bodyLabel().apply { setText(name) }).left().width(NAME_COL).height(STAT_ROW_H)
        val bar = Image()
        val value = frame.bodyLabel()
        val cell = Table()
        cell.add(bar).size(BAR_WIDTH.toFloat(), BAR_HEIGHT.toFloat()).padRight(24f)
        cell.add(value).left().expandX().fillX()
        panel.add(cell).colspan(2).left().width(VALUE_COL + BUTTON_COL).height(STAT_ROW_H).row()
        return bar to value
    }

    private class UpgradeRow(val levelLabel: Label, val button: TextButton)

    private fun upgradeRow(panel: Table, name: String, onUpgrade: () -> Unit): UpgradeRow {
        panel.add(frame.bodyLabel().apply { setText(name) })
            .left().width(NAME_COL).height(ACTION_ROW_H)
        val levelLabel = frame.bodyLabel()
        panel.add(levelLabel).left().width(VALUE_COL).height(ACTION_ROW_H)
        val button = frame.button("Upgrade") {
            onUpgrade()
            AudioManager.playCoins()
            refresh()
        }
        panel.add(button).width(BUTTON_COL).height(BUTTON_H).row()
        return UpgradeRow(levelLabel, button)
    }

    private fun refresh() {
        val c = player.condition
        val activeName = player.ownedShips.firstOrNull { it.id == player.activeShipId }?.name ?: player.ship.name
        frame.setTitle(activeName)
        frame.setStatus("Shipyard", player.gold)

        cargoValue.setText("${player.cargoUsed} / ${player.cargoCapacity}")
        speedValue.setText(player.effectiveSpeed.toInt().toString())
        maneuverValue.setText(player.effectiveManeuverability.toString())
        damageValue.setText("${player.effectiveMinDamage}-${player.effectiveMaxDamage}")

        val hullFraction = c.currentHull.toFloat() / player.effectiveMaxHull.toFloat()
        hullBarTexture?.dispose()
        hullBarTexture = buildBarTexture(hullFraction, HULL_FILL)
        hullBar.drawable = TextureRegionDrawable(TextureRegion(hullBarTexture))
        hullValue.setText("${c.currentHull} / ${player.effectiveMaxHull}")

        foulingBarTexture?.dispose()
        foulingBarTexture = buildBarTexture(c.fouling, FOULING_FILL)
        foulingBar.drawable = TextureRegionDrawable(TextureRegion(foulingBarTexture))
        foulingValue.setText("${(c.fouling * 100).toInt()}%")

        setUpgradeRow(cannonRow, c.cannonLevel, c.cannonUpgradeCost(), player::canUpgradeCannons)
        setUpgradeRow(oarRow, c.oarLevel, c.oarUpgradeCost(), player::canUpgradeOars)
        setUpgradeRow(sailRow, c.sailLevel, c.sailUpgradeCost(), player::canUpgradeSails)

        cleanLabel.setText("Fouling ${(c.fouling * 100).toInt()}% - cost ${c.cleanCost()}g")
        cleanButton.enable(player.canCleanHull())

        val missingHull = player.effectiveMaxHull - c.currentHull
        repairLabel.setText("Missing $missingHull hp - cost ${c.repairCost(player.effectiveMaxHull)}g")
        repairButton.enable(player.canRepair())
    }

    private fun setUpgradeRow(row: UpgradeRow, level: Int, cost: Int?, canAfford: () -> Boolean) {
        row.levelLabel.setText(
            if (cost == null) "Level $level / $MAX_UPGRADE_LEVEL (max)" else "Level $level / $MAX_UPGRADE_LEVEL - cost ${cost}g"
        )
        row.button.enable(cost != null && canAfford())
    }

    /** An action that cannot be taken reads as unavailable rather than silently doing nothing -
     *  the same treatment the warehouse gives its rows. */
    private fun TextButton.enable(enabled: Boolean) {
        touchable = if (enabled) Touchable.enabled else Touchable.disabled
        color.a = if (enabled) 1f else 0.45f
    }

    private fun buildBarTexture(fraction: Float, fillColor: Color): Texture {
        val pixmap = Pixmap(BAR_WIDTH, BAR_HEIGHT, Pixmap.Format.RGBA8888)
        pixmap.setColor(BAR_EMPTY)
        pixmap.fill()
        val filledWidth = (BAR_WIDTH * fraction.coerceIn(0f, 1f)).toInt()
        if (filledWidth > 0) {
            pixmap.setColor(fillColor)
            pixmap.fillRectangle(0, 0, filledWidth, BAR_HEIGHT)
        }
        pixmap.setColor(BAR_BORDER)
        pixmap.drawRectangle(0, 0, BAR_WIDTH, BAR_HEIGHT)
        val texture = Texture(pixmap)
        pixmap.dispose()
        return texture
    }
}
