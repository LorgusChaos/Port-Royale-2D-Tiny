package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.portroyale.tiny.audio.AudioManager
import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.combat.CombatState

private const val CANNONBALL_FLIGHT_TIME = 0.35f
private const val CANNONBALL_SIZE = 10f
private const val SHIP_COLUMN_GAP = 220f

/** Modal turn-based combat screen: fire cannons or attempt to flee, one round per click.
 *  Logic resolves instantly in CombatState; this class just animates a cannonball flying
 *  between the two ships to present that instant result over a short, readable beat. */
class CombatUI(private val onCombatEnd: (CombatOutcome) -> Unit) {

    val stage = Stage(ScreenViewport())

    private val skin = UiSkinFactory.build()
    private val root = Table()
    private val shipTextures = mutableMapOf<String, Texture>()
    private val cannonballDrawable = TextureRegionDrawable(TextureRegion(buildCannonballTexture()))

    private lateinit var titleLabel: Label
    private lateinit var playerHullLabel: Label
    private lateinit var enemyHullLabel: Label
    private lateinit var logLabel: Label
    private lateinit var playerShipImage: Image
    private lateinit var enemyShipImage: Image
    private lateinit var attackButton: TextButton
    private lateinit var fleeButton: TextButton

    private var combat: CombatState? = null
    private var busy = false

    init {
        root.setFillParent(true)
        root.center()
        stage.addActor(root)
        buildLayout()
    }

    fun open(combatState: CombatState) {
        combat = combatState
        busy = false
        playerShipImage.drawable = TextureRegionDrawable(TextureRegion(textureFor(combatState.playerSpritePath)))
        enemyShipImage.drawable = TextureRegionDrawable(TextureRegion(textureFor(combatState.enemySpritePath)))
        playerShipImage.color = Color.WHITE
        enemyShipImage.color = Color.WHITE
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
        cannonballDrawable.region.texture.dispose()
        shipTextures.values.forEach { it.dispose() }
    }

    private fun textureFor(path: String): Texture = shipTextures.getOrPut(path) { Texture(Gdx.files.internal(path)) }

    private fun buildCannonballTexture(): Texture {
        val size = 16
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.valueOf("1C1B18"))
        pixmap.fillCircle(size / 2, size / 2, size / 2)
        val texture = Texture(pixmap)
        pixmap.dispose()
        return texture
    }

    private fun buildLayout() {
        val panel = Table()
        panel.background = skin.getDrawable("panel")
        panel.pad(36f)

        titleLabel = Label("", skin)
        titleLabel.setFontScale(1.3f)
        panel.add(titleLabel).colspan(3).padBottom(16f).row()

        playerShipImage = Image()
        playerShipImage.setScaling(Scaling.fit)
        enemyShipImage = Image()
        enemyShipImage.setScaling(Scaling.fit)
        playerHullLabel = Label("", skin)
        enemyHullLabel = Label("", skin)

        val playerColumn = Table()
        playerColumn.add(playerShipImage).size(90f, 150f).padBottom(8f).row()
        playerColumn.add(playerHullLabel).row()

        val enemyColumn = Table()
        enemyColumn.add(enemyShipImage).size(90f, 150f).padBottom(8f).row()
        enemyColumn.add(enemyHullLabel).row()

        val shipsRow = Table()
        shipsRow.add(playerColumn).padRight(SHIP_COLUMN_GAP / 2f)
        shipsRow.add(enemyColumn).padLeft(SHIP_COLUMN_GAP / 2f)
        panel.add(shipsRow).colspan(3).padBottom(24f).row()

        logLabel = Label("", skin)
        logLabel.setWrap(true)
        panel.add(logLabel).width(460f).colspan(3).padBottom(24f).row()

        attackButton = TextButton("Fire Cannons", skin)
        attackButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onFireCannons()
            }
        })

        fleeButton = TextButton("Attempt to Flee", skin)
        fleeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onAttemptFlee()
            }
        })

        panel.add(attackButton).width(170f).pad(4f)
        panel.add(fleeButton).width(170f).pad(4f)

        root.add(panel)
    }

    private fun onFireCannons() {
        if (busy) return
        val c = combat ?: return
        if (c.isOver) return
        val enemyHullBefore = c.enemyHull
        val playerHullBefore = c.playerHull
        c.playerAttack()
        val playerHitEnemy = c.enemyHull < enemyHullBefore
        val enemyHitPlayer = c.playerHull < playerHullBefore
        val outcomeAfter = c.outcome

        busy = true
        val playerCenter = shipCenterInStage(playerShipImage)
        val enemyCenter = shipCenterInStage(enemyShipImage)

        fireCannonball(playerCenter, enemyCenter) {
            if (playerHitEnemy) flashHit(enemyShipImage)
            if (outcomeAfter != CombatOutcome.ONGOING) {
                finishRound(outcomeAfter)
            } else {
                fireCannonball(enemyCenter, playerCenter) {
                    if (enemyHitPlayer) flashHit(playerShipImage)
                    finishRound(outcomeAfter)
                }
            }
        }
    }

    private fun onAttemptFlee() {
        if (busy) return
        val c = combat ?: return
        if (c.isOver) return
        AudioManager.playClick()
        val playerHullBefore = c.playerHull
        c.playerFlee()

        if (c.outcome == CombatOutcome.FLED) {
            finishRound(c.outcome)
            return
        }

        val enemyHitPlayer = c.playerHull < playerHullBefore
        val outcomeAfter = c.outcome
        busy = true
        val playerCenter = shipCenterInStage(playerShipImage)
        val enemyCenter = shipCenterInStage(enemyShipImage)
        fireCannonball(enemyCenter, playerCenter) {
            if (enemyHitPlayer) flashHit(playerShipImage)
            finishRound(outcomeAfter)
        }
    }

    private fun finishRound(outcome: CombatOutcome) {
        refresh()
        busy = false
        if (outcome != CombatOutcome.ONGOING) {
            onCombatEnd(outcome)
        }
    }

    private fun shipCenterInStage(image: Image): Vector2 =
        image.localToStageCoordinates(Vector2(image.width / 2f, image.height / 2f))

    private fun fireCannonball(from: Vector2, to: Vector2, onArrive: () -> Unit) {
        AudioManager.playCannonFire()
        val ball = Image(cannonballDrawable)
        ball.setSize(CANNONBALL_SIZE, CANNONBALL_SIZE)
        ball.setPosition(from.x - CANNONBALL_SIZE / 2f, from.y - CANNONBALL_SIZE / 2f)
        stage.addActor(ball)
        ball.addAction(
            Actions.sequence(
                Actions.moveTo(to.x - CANNONBALL_SIZE / 2f, to.y - CANNONBALL_SIZE / 2f, CANNONBALL_FLIGHT_TIME),
                Actions.run {
                    ball.remove()
                    onArrive()
                }
            )
        )
    }

    private fun flashHit(image: Image) {
        AudioManager.playHit()
        image.clearActions()
        image.color = Color.WHITE
        image.addAction(Actions.sequence(Actions.color(Color.RED, 0.06f), Actions.color(Color.WHITE, 0.3f)))
    }

    private fun refresh() {
        val c = combat ?: return
        titleLabel.setText(c.enemyName)
        playerHullLabel.setText("Your Ship: ${c.playerHull} / ${c.playerMaxHull}")
        enemyHullLabel.setText("${c.enemyName}: ${c.enemyHull} / ${c.enemyMaxHull}")
        logLabel.setText(c.log)
    }
}
