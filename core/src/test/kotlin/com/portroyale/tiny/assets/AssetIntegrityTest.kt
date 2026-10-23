package com.portroyale.tiny.assets

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.world.AiShipFactory
import java.io.File
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The artwork, checked the way ShipSpriteOrientationTest checks which way a hull points: against
 * the files themselves, with no window. Adding a picture is where things kept going wrong - a
 * path with a typo that only fails when the screen first opens, a background that was painted
 * instead of transparent, a source sheet shipped in the jar - and none of it is visible to a
 * compile. All of it is visible to this.
 *
 * Sprites are produced by tools/art/art.py; when a check here fails, the fix is almost always
 * to run that script over the file rather than to edit the image by hand.
 */
class AssetIntegrityTest {

    @BeforeTest
    fun loadNatives() {
        GdxNativesLoader.load()
    }

    private val assetsDir = File("../assets")

    /** Every asset the game names: path literals in the main sources, plus the ship lists,
     *  which build some of their paths at runtime (the pirate tiers are "ship2_black_$it"). */
    private fun referencedPaths(): Set<String> {
        val literal = Regex("\"((?:sprites|maps|fonts|audio)/[^\"\$]+)\"")
        val fromSource = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> literal.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        val ships = ShipTypes.ALL.map { it.spritePath } + ShipTypes.LONGBOAT.spritePath + AiShipFactory.ALL_SPRITES
        return fromSource + ships
    }

    /** Sprites drawn with Linear filtering and mipmaps - every PNG under sprites/ the game uses. */
    private fun referencedSprites() = referencedPaths().filter { it.startsWith("sprites/") && it.endsWith(".png") }.sorted()

    @Test
    fun `every asset the code names exists`() {
        val missing = referencedPaths().filterNot { File(assetsDir, it).isFile }.sorted()
        assertTrue(missing.isEmpty(), "Referenced in code but not in assets/: $missing")
    }

    @Test
    fun `source sheets and mockups live in art-src, not in the shipped assets`() {
        // assets/ is the jar's resource root (lwjgl3/build.gradle), so anything left here ships.
        val strays = assetsDir.walkTopDown()
            .filter { it.isFile && (it.name.contains("_source") || it.name.contains("mockup")) }
            .map { it.relativeTo(assetsDir).invariantSeparatorsPath }
            .toList()
        assertTrue(strays.isEmpty(), "Move these to art-src/ - they would ship in the game: $strays")
    }

    @Test
    fun `sprites have a real transparent background`() {
        // A cut-out sprite with an opaque corner means the background was never removed - most
        // often an image generator's painted checkerboard, which looks transparent in a viewer.
        val opaque = referencedSprites().filter { path ->
            val img = load(path)
            listOf(img.alpha(0, 0), img.alpha(img.w - 1, 0), img.alpha(0, img.h - 1), img.alpha(img.w - 1, img.h - 1))
                .any { it > ALPHA_CLEAR }
        }
        assertTrue(opaque.isEmpty(), "Opaque corners (background not removed): $opaque - run tools/art/art.py slice")
    }

    @Test
    fun `transparent pixels carry the colour of the sprite edge`() {
        // Under Linear filtering the GPU blends a sprite's edge with the RGB hidden under its
        // transparent neighbours. If that RGB is the old background (white, black) every ship and
        // icon gets a rim of it. art.py `bleed` fills it with the nearest opaque colour; this
        // checks that it was run. Measured on the real set: unbled files sit at 29-90% of edge
        // pixels over the limit, bled ones at 0-1%.
        val haloed = referencedSprites().mapNotNull { path ->
            val img = load(path)
            var edge = 0
            var bad = 0
            for (y in 0 until img.h) for (x in 0 until img.w) {
                if (img.alpha(x, y) > ALPHA_CLEAR) continue
                var best = Int.MAX_VALUE
                for ((dx, dy) in NEIGHBOURS) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until img.w || ny !in 0 until img.h || img.alpha(nx, ny) <= ALPHA_CLEAR) continue
                    best = minOf(best, img.rgbDistance(x, y, nx, ny))
                }
                if (best == Int.MAX_VALUE) continue
                edge++
                if (best > EDGE_COLOUR_TOLERANCE) bad++
            }
            if (edge > 0 && bad > edge * MAX_BAD_EDGE_SHARE) "$path (${bad * 100 / edge}% of edge)" else null
        }
        assertTrue(haloed.isEmpty(), "Edge colour not bled, will show a halo: $haloed - run tools/art/art.py bleed")
    }

    private class Rgba(val w: Int, val h: Int, private val bytes: ByteArray) {
        private fun at(x: Int, y: Int, channel: Int) = bytes[(y * w + x) * 4 + channel].toInt() and 0xFF
        fun alpha(x: Int, y: Int) = at(x, y, 3)
        fun rgbDistance(x: Int, y: Int, ox: Int, oy: Int) =
            (0..2).sumOf { abs(at(x, y, it) - at(ox, oy, it)) }
    }

    /** Whole image as RGBA bytes in one copy - per-pixel getPixel is a native call each, which on
     *  the 1536x1024 hub frame is the difference between milliseconds and seconds. */
    private fun load(path: String): Rgba {
        val raw = File(assetsDir, path).readBytes()
        val src = Pixmap(raw, 0, raw.size)
        try {
            val rgba = if (src.format == Pixmap.Format.RGBA8888) {
                src
            } else {
                Pixmap(src.width, src.height, Pixmap.Format.RGBA8888).also {
                    it.blending = Pixmap.Blending.None
                    it.drawPixmap(src, 0, 0)
                }
            }
            val buffer = rgba.pixels
            buffer.rewind()
            val bytes = ByteArray(src.width * src.height * 4)
            buffer.get(bytes)
            buffer.rewind()
            if (rgba !== src) rgba.dispose()
            return Rgba(src.width, src.height, bytes)
        } finally {
            src.dispose()
        }
    }

    private companion object {
        const val ALPHA_CLEAR = 8
        const val EDGE_COLOUR_TOLERANCE = 96
        const val MAX_BAD_EDGE_SHARE = 0.05
        val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}
