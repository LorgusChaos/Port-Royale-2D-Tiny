package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.portroyale.tiny.economy.Good

/** Small hand-drawn pictograms (Pixmap primitives only, no external art dependency) used as
 *  icons in the port hub and its sub-screens. Every icon gets a 1px dark outline traced
 *  automatically around its silhouette in [finish] - the single biggest thing that made these
 *  read as "pixel art" instead of flat vector shapes, and it's applied uniformly so every icon
 *  shares the same look regardless of how each one is drawn. Built once on first use and cached
 *  for the lifetime of the app, same lazy-singleton pattern as AudioManager. */
object IconFactory {
    private const val SIZE = 64
    private val OUTLINE_COLOR = Color.valueOf("1A120A")

    val market: Texture by lazy { buildArtIcon("sprites/icons/market.png") }
    val shipyard: Texture by lazy { buildArtIcon("sprites/icons/shipyard.png") }
    val fortress: Texture by lazy { buildArtIcon("sprites/icons/fortress.png") }
    val tavern: Texture by lazy { buildArtIcon("sprites/icons/tavern.png") }
    val port: Texture by lazy { buildPortIcon() }
    /** Drawn behind a good icon on the map to flag it as notably cheap (surplus) or notably
     *  expensive (shortage) at that port - see MarketState.notableGoods. */
    val surplusFrame: Texture by lazy { buildFrameIcon(Color.valueOf("3E8E41")) }
    val shortageFrame: Texture by lazy { buildFrameIcon(Color.valueOf("A61C1C")) }
    /** Soft, smoothly-filtered glow marking the port the player's ship is currently docked at -
     *  deliberately not run through [finish]'s pixel-art outline, since a glow should be soft. */
    val dockedGlow: Texture by lazy { buildGlowIcon() }
    /** Reward icons for the sea-event toast (see EventToastUI) - gold/goods chests reuse
     *  [coin]/a good's own icon, so these two cover the bottle and castaway cases specifically. */
    val coin: Texture by lazy { buildCoinIcon() }
    val bottle: Texture by lazy { buildBottleIcon() }
    val castaway: Texture by lazy { buildCastawayIcon() }
    val warehouse: Texture by lazy { buildArtIcon("sprites/icons/warehouse.png") }
    val property: Texture by lazy { buildPropertyIcon() }
    val debt: Texture by lazy { buildDebtIcon() }

    private val goodIcons = mutableMapOf<Good, Texture>()

    fun iconFor(good: Good): Texture = goodIcons.getOrPut(good) { buildGoodIcon(good) }

    fun dispose() {
        market.dispose()
        shipyard.dispose()
        fortress.dispose()
        tavern.dispose()
        port.dispose()
        surplusFrame.dispose()
        shortageFrame.dispose()
        dockedGlow.dispose()
        coin.dispose()
        bottle.dispose()
        castaway.dispose()
        warehouse.dispose()
        property.dispose()
        debt.dispose()
        goodIcons.values.forEach { it.dispose() }
    }

    private fun blank(): Pixmap {
        val pixmap = Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888)
        pixmap.setColor(0f, 0f, 0f, 0f)
        pixmap.fill()
        return pixmap
    }

    private fun finish(pixmap: Pixmap): Texture {
        outline(pixmap)
        val texture = Texture(pixmap)
        pixmap.dispose()
        return texture
    }

    /** Traces a 1px outline around whatever's already drawn: any transparent pixel that's
     *  4-directionally adjacent to an opaque one gets painted [OUTLINE_COLOR]. Works on any
     *  silhouette automatically, no matter how many primitives it's built from. */
    private fun outline(pixmap: Pixmap) {
        val w = pixmap.width
        val h = pixmap.height
        val opaque = Array(w) { x -> BooleanArray(h) { y -> (pixmap.getPixel(x, y) and 0xFF) != 0 } }
        pixmap.setColor(OUTLINE_COLOR)
        for (x in 0 until w) {
            for (y in 0 until h) {
                if (opaque[x][y]) continue
                val touchesShape =
                    (x > 0 && opaque[x - 1][y]) || (x < w - 1 && opaque[x + 1][y]) ||
                        (y > 0 && opaque[x][y - 1]) || (y < h - 1 && opaque[x][y + 1])
                if (touchesShape) pixmap.drawPixel(x, y)
            }
        }
    }

    /** Loads one of the hand-painted (AI-generated, background-removed) port-menu icons from
     *  disk - unlike the other icons here, these are not built from Pixmap primitives, so they
     *  skip [finish]'s pixel-art outline pass. Mipmapped + linearly filtered since the source art
     *  is ~5x the 64x64 display size and would alias badly under nearest-neighbor minification. */
    private fun buildArtIcon(path: String): Texture {
        val texture = Texture(Gdx.files.internal(path), true)
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        return texture
    }

    /** Map marker for a port: a couple of house silhouettes plus a small flagged dock post,
     *  standing in for real city art (drawn at a fixed world size, same technique as the ship
     *  sprite) until the map has room for actual settlement art. */
    /** Map marker for a port: one bold watchtower silhouette (not the 3-turret Fortress icon -
     *  distinguishable at a glance, same "stone castle" language) with simple two-tone shading
     *  for volume. Drawn big and simple on purpose, since this renders tiny on the world map. */
    private fun buildPortIcon(): Texture {
        val pm = blank()
        val stoneDark = Color.valueOf("6E695F")
        val stoneLit = Color.valueOf("9C958A")
        val stoneShadow = Color.valueOf("4E4A42")

        // tower body: dark base, lit face on the left, shadow strip down the right edge
        pm.setColor(stoneDark)
        pm.fillRectangle(16, 20, 32, 38)
        pm.setColor(stoneLit)
        pm.fillRectangle(18, 22, 22, 36)
        pm.setColor(stoneShadow)
        pm.fillRectangle(42, 22, 6, 36)

        // crenellations along the top
        pm.setColor(stoneDark)
        pm.fillRectangle(16, 12, 8, 10)
        pm.fillRectangle(28, 12, 8, 10)
        pm.fillRectangle(40, 12, 8, 10)

        // doorway
        pm.setColor(Color.valueOf("241609"))
        pm.fillRectangle(25, 42, 14, 16)

        // flagpole + flag
        pm.setColor(Color.valueOf("5A4530"))
        pm.fillRectangle(31, 2, 2, 12)
        pm.setColor(Color.valueOf("A61C1C"))
        pm.fillTriangle(33, 3, 33, 11, 46, 6)

        return finish(pm)
    }

    /** A hollow colored square ring - a good icon is drawn on top of this (smaller, centered)
     *  wherever it's shown as notably cheap/expensive on the map. */
    private fun buildFrameIcon(color: Color): Texture {
        val pm = blank()
        val thickness = 8
        pm.setColor(color)
        pm.fillRectangle(0, 0, SIZE, thickness)
        pm.fillRectangle(0, SIZE - thickness, SIZE, thickness)
        pm.fillRectangle(0, 0, thickness, SIZE)
        pm.fillRectangle(SIZE - thickness, 0, thickness, SIZE)
        return finish(pm)
    }

    private fun buildGlowIcon(): Texture {
        val pm = blank()
        val center = SIZE / 2
        pm.setColor(Color(0.55f, 0.1f, 0.85f, 0.28f))
        pm.fillCircle(center, center, SIZE / 2)
        pm.setColor(Color(0.68f, 0.15f, 0.95f, 0.55f))
        pm.fillCircle(center, center, SIZE / 3)
        pm.setColor(Color(0.88f, 0.55f, 1f, 0.9f))
        pm.fillCircle(center, center, SIZE / 6)
        val texture = Texture(pm)
        pm.dispose()
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        return texture
    }

    private fun buildCoinIcon(): Texture {
        val pm = blank()
        pm.setColor(Color.valueOf("8A6A1E"))
        pm.fillCircle(32, 32, 22)
        pm.setColor(Color.valueOf("EFCB61"))
        pm.fillCircle(32, 32, 18)
        pm.setColor(Color.valueOf("8A6A1E"))
        pm.drawCircle(32, 32, 13)
        return finish(pm)
    }

    private fun buildBottleIcon(): Texture {
        val pm = blank()
        pm.setColor(Color.valueOf("2E6E4A"))
        pm.fillRectangle(27, 8, 10, 12)
        pm.fillTriangle(20, 20, 44, 20, 32, 12)
        pm.fillRectangle(18, 20, 28, 34)
        pm.setColor(Color.valueOf("4A9E6E"))
        pm.fillRectangle(21, 26, 22, 24)
        pm.setColor(Color.valueOf("D8C8A8"))
        pm.fillRectangle(25, 6, 14, 6)
        return finish(pm)
    }

    private fun buildCastawayIcon(): Texture {
        val pm = blank()
        val skin = Color.valueOf("C98A5E")
        val water = Color.valueOf("3E7EA8")
        pm.setColor(water)
        pm.fillRectangle(4, 44, 56, 12)
        pm.setColor(skin)
        pm.fillCircle(32, 22, 12)
        pm.fillRectangle(22, 32, 20, 16)
        pm.setColor(Color.valueOf("8A5A2E"))
        pm.fillRectangle(12, 40, 14, 6)
        pm.fillRectangle(38, 40, 14, 6)
        return finish(pm)
    }

    private fun buildPropertyIcon(): Texture {
        val pm = blank()
        pm.setColor(Color.valueOf("6E4A2E"))
        pm.fillRectangle(10, 20, 44, 10)
        pm.setColor(Color.valueOf("9C6E3E"))
        pm.fillRectangle(10, 30, 44, 26)
        pm.setColor(Color.valueOf("D9A24A"))
        pm.fillRectangle(14, 34, 15, 10)
        pm.fillRectangle(35, 34, 15, 10)
        pm.fillRectangle(14, 48, 15, 6)
        pm.fillRectangle(35, 48, 15, 6)
        pm.setColor(Color.valueOf("5A3A20"))
        pm.drawRectangle(10, 20, 44, 10)
        return finish(pm)
    }

    private fun buildDebtIcon(): Texture {
        val pm = blank()
        pm.setColor(Color.valueOf("2E2A24"))
        pm.fillRectangle(16, 8, 32, 48)
        pm.setColor(Color.valueOf("D9CBA8"))
        pm.fillRectangle(19, 11, 26, 42)
        pm.setColor(Color.valueOf("8A1C1C"))
        pm.fillRectangle(23, 18, 18, 3)
        pm.fillRectangle(23, 26, 18, 3)
        pm.fillRectangle(23, 34, 12, 3)
        pm.setColor(Color.valueOf("D9A24A"))
        pm.fillCircle(32, 48, 8)
        pm.setColor(Color.valueOf("6E4A0E"))
        pm.drawCircle(32, 48, 8)
        return finish(pm)
    }

    private fun buildGoodIcon(good: Good): Texture {
        val pm = blank()
        when (good) {
            Good.SUGAR -> {
                pm.setColor(Color.valueOf("7A5A3A"))
                pm.fillRectangle(20, 46, 24, 8)
                pm.setColor(Color.valueOf("F5F0E6"))
                pm.fillTriangle(32, 8, 16, 46, 48, 46)
            }
            Good.RUM -> {
                pm.setColor(Color.valueOf("3A2A1E"))
                pm.fillRectangle(22, 10, 8, 10)
                pm.fillRectangle(18, 20, 16, 32)
                pm.setColor(Color.valueOf("8A5A2E"))
                pm.fillRectangle(20, 26, 12, 20)
                pm.setColor(Color.valueOf("D8C8A8"))
                pm.fillRectangle(17, 34, 18, 6)
            }
            Good.TOBACCO -> {
                pm.setColor(Color.valueOf("6B4226"))
                pm.fillCircle(24, 30, 15)
                pm.fillCircle(38, 34, 13)
                pm.setColor(Color.valueOf("8A5A2E"))
                pm.fillCircle(24, 30, 11)
                pm.fillCircle(38, 34, 9)
            }
            Good.COTTON -> {
                pm.setColor(Color.valueOf("D9C79A"))
                pm.fillRectangle(30, 36, 4, 16)
                pm.setColor(Color.valueOf("F7F5F0"))
                pm.fillCircle(24, 28, 12)
                pm.fillCircle(38, 28, 12)
                pm.fillCircle(32, 20, 12)
            }
            Good.SPICES -> {
                pm.setColor(Color.valueOf("6E4A2E"))
                pm.fillTriangle(20, 20, 44, 20, 32, 10)
                pm.setColor(Color.valueOf("C24A1E"))
                pm.fillRectangle(18, 20, 28, 28)
                pm.setColor(Color.valueOf("E8792E"))
                pm.fillRectangle(22, 24, 20, 20)
            }
            Good.WOOD -> {
                pm.setColor(Color.valueOf("6B4226"))
                pm.fillRectangle(10, 26, 44, 14)
                pm.setColor(Color.valueOf("8A5A2E"))
                pm.fillCircle(12, 33, 7)
                pm.fillCircle(52, 33, 7)
                pm.setColor(Color.valueOf("C9A876"))
                pm.fillCircle(12, 33, 4)
                pm.fillCircle(52, 33, 4)
            }
            Good.COFFEE -> {
                pm.setColor(Color.valueOf("3E2415"))
                pm.fillCircle(24, 32, 13)
                pm.fillCircle(40, 32, 13)
                pm.setColor(Color.valueOf("1C1209"))
                pm.fillRectangle(23, 26, 2, 12)
                pm.fillRectangle(39, 26, 2, 12)
            }
            Good.CACAO -> {
                pm.setColor(Color.valueOf("A85A2E"))
                pm.fillCircle(32, 32, 18)
                pm.setColor(Color.valueOf("C97A3E"))
                pm.drawLine(32, 14, 32, 50)
                pm.drawLine(18, 22, 24, 44)
                pm.drawLine(46, 22, 40, 44)
            }
            Good.CLOTH -> {
                pm.setColor(Color.valueOf("4A3E7A"))
                pm.fillRectangle(14, 18, 36, 30)
                pm.setColor(Color.valueOf("6355A0"))
                pm.fillRectangle(14, 24, 36, 4)
                pm.fillRectangle(14, 34, 36, 4)
            }
            Good.SILVER -> {
                pm.setColor(Color.valueOf("8A8F98"))
                pm.fillRectangle(16, 26, 32, 18)
                pm.setColor(Color.valueOf("D5D9E0"))
                pm.fillRectangle(18, 28, 28, 5)
            }
        }
        return finish(pm)
    }
}
