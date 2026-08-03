package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
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
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.MAX_UPGRADE_LEVEL

private const val BAR_WIDTH = 180
private const val BAR_HEIGHT = 16
private val BAR_EMPTY = Color.valueOf("1C140B")
private val BAR_BORDER = Color.valueOf("241609")
private val HULL_FILL = Color.valueOf("8A2E2E")
private val FOULING_FILL = Color.valueOf("4E6B3A")

/** Shipyard sub-screen: current ship's condition and full stat readout (with bars for hull and
 *  fouling instead of raw numbers alone), plus permanent upgrades (cannons/oars/sails) and
 *  one-off maintenance (hull cleaning, repair). Reached from ShipyardUI; "Back" returns there.
 *  Balance here is a rough first pass, not tuned. */
class ShipStatusUI(private val player: Player, private val onBack: () -> Unit) {

    val stage = Stage(UiScale.viewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private lateinit var titleLabel: Label
    private lateinit var goldLabel: Label
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
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
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
    }

    fun dispose() {
        stage.dispose()
        skin.dispose()
        hullBarTexture?.dispose()
        foulingBarTexture?.dispose()
    }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(40f)

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(3).left().padBottom(4f).row()

        goldLabel = Label("", skin)
        panel.add(goldLabel).colspan(3).left().padBottom(16f).row()

        cargoValue = statRow(panel, "Cargo")
        val hullRow = barRow(panel, "Hull")
        hullBar = hullRow.first
        hullValue = hullRow.second
        val foulingRow = barRow(panel, "Fouling")
        foulingBar = foulingRow.first
        foulingValue = foulingRow.second
        speedValue = statRow(panel, "Speed")
        maneuverValue = statRow(panel, "Maneuverability")
        damageValue = statRow(panel, "Damage")

        val upgradesLabel = Label("Upgrades", skin)
        upgradesLabel.setFontScale(1.1f)
        panel.add(upgradesLabel).colspan(3).left().padTop(16f).padBottom(8f).row()

        cannonRow = upgradeRow(panel, "Cannons") { player.upgradeCannons() }
        oarRow = upgradeRow(panel, "Extra Oars") { player.upgradeOars() }
        sailRow = upgradeRow(panel, "Extra Sails") { player.upgradeSails() }

        val maintenanceLabel = Label("Maintenance", skin)
        maintenanceLabel.setFontScale(1.1f)
        panel.add(maintenanceLabel).colspan(3).left().padTop(16f).padBottom(8f).row()

        cleanLabel = Label("", skin)
        panel.add(cleanLabel).left().width(240f).pad(4f)
        cleanButton = TextButton("Clean Hull", skin)
        cleanButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (player.canCleanHull()) {
                    player.cleanHull()
                    AudioManager.playCoins()
                    refresh()
                }
            }
        })
        panel.add(cleanButton).colspan(2).width(120f).pad(4f).row()

        repairLabel = Label("", skin)
        panel.add(repairLabel).left().width(240f).pad(4f)
        repairButton = TextButton("Repair", skin)
        repairButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (player.canRepair()) {
                    player.repair()
                    AudioManager.playCoins()
                    refresh()
                }
            }
        })
        panel.add(repairButton).colspan(2).width(120f).pad(4f).row()

        val backButton = TextButton("Back", skin)
        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                AudioManager.playClick()
                onBack()
            }
        })
        panel.add(backButton).colspan(3).left().width(140f).padTop(20f)

        root.add(panel)
    }

    /** Plain "label ......... value" row, returns the value Label to update on refresh. */
    private fun statRow(panel: Table, name: String): Label {
        panel.add(Label(name, skin)).left().width(160f).pad(3f)
        val value = Label("", skin)
        panel.add(value).colspan(2).left().pad(3f).row()
        return value
    }

    /** Same shape as [statRow] but with a small fill-bar Image ahead of the numeric value,
     *  redrawn (not scaled) each refresh so it always shows a crisp, un-blurred fraction. */
    private fun barRow(panel: Table, name: String): Pair<Image, Label> {
        panel.add(Label(name, skin)).left().width(160f).pad(3f)
        val bar = Image()
        panel.add(bar).size(BAR_WIDTH.toFloat(), BAR_HEIGHT.toFloat()).pad(3f)
        val value = Label("", skin)
        panel.add(value).left().pad(3f).row()
        return bar to value
    }

    private class UpgradeRow(val levelLabel: Label, val button: TextButton)

    private fun upgradeRow(panel: Table, name: String, onUpgrade: () -> Unit): UpgradeRow {
        panel.add(Label(name, skin)).left().width(140f).pad(4f)
        val levelLabel = Label("", skin)
        panel.add(levelLabel).width(220f).pad(4f)
        val button = TextButton("Upgrade", skin)
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onUpgrade()
                AudioManager.playCoins()
                refresh()
            }
        })
        panel.add(button).width(120f).pad(4f).row()
        return UpgradeRow(levelLabel, button)
    }

    private fun refresh() {
        val c = player.condition
        titleLabel.setText("${player.ship.name} - Status")
        goldLabel.setText("Gold: ${player.gold}")

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
        cleanButton.touchable = touchableFor(player.canCleanHull())

        val missingHull = player.effectiveMaxHull - c.currentHull
        repairLabel.setText("Missing $missingHull hp - cost ${c.repairCost(player.effectiveMaxHull)}g")
        repairButton.touchable = touchableFor(player.canRepair())
    }

    private fun setUpgradeRow(row: UpgradeRow, level: Int, cost: Int?, canAfford: () -> Boolean) {
        row.levelLabel.setText(
            if (cost == null) "Level $level / $MAX_UPGRADE_LEVEL (max)" else "Level $level / $MAX_UPGRADE_LEVEL - cost ${cost}g"
        )
        row.button.touchable = touchableFor(cost != null && canAfford())
    }

    private fun touchableFor(enabled: Boolean) =
        if (enabled) Touchable.enabled else Touchable.disabled

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
