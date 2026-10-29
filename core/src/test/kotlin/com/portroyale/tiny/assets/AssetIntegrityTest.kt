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

    /** Window frames that are deliberately shipped with the black field they were painted on.
     *
     *  Cutting them out is what the background rule is for, and it was tried: the frames share
     *  their background colour with their own painted shadows, so a flood from the border walks in
     *  through the dark gaps between the props and shreds them - the sea showed through the
     *  stalls. Until it is decided how a frame should meet the sea behind it (the same decision
     *  that settles how the HUD and a port window share the screen), they go in whole, and this
     *  check would otherwise fail on a corner that is opaque on purpose. The hub's frame is not
     *  listed: its art is light enough that the cut worked, and it already ships cut out. */
    private val uncutWindowFrames = setOf(
        "sprites/ui/port_market_frame.png",
        "sprites/ui/port_shipyard_frame.png",
        "sprites/ui/port_tavern_frame.png",
    )

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
        val opaque = (referencedSprites() - uncutWindowFrames).filter { path ->
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

    @Test
    fun `no sprite carries a painted transparency checkerboard`() {
        // The defect this exists for: port_town.png shipped with the generator's grey checkerboard
        // baked in as opaque pixels - squares of it floating beside the town on the world map. The
        // background check above looks at the corners and the bleed check looks under transparent
        // pixels, so neither sees squares that sit next to the art. This does, by its pattern:
        // two grey tones, far apart in brightness, laid out on a grid.
        val haunted = referencedSprites().mapNotNull { path ->
            val img = load(path)
            val tones = img.checkerTones() ?: return@mapNotNull null
            val block = img.checkerBlock(tones) ?: return@mapNotNull null
            "$path (${block}px squares)"
        }
        assertTrue(haunted.isEmpty(), "A painted checkerboard is baked into: $haunted - run tools/art/art.py uncheck")
    }

    private class Rgba(val w: Int, val h: Int, private val bytes: ByteArray) {
        private fun at(x: Int, y: Int, channel: Int) = bytes[(y * w + x) * 4 + channel].toInt() and 0xFF
        fun alpha(x: Int, y: Int) = at(x, y, 3)
        fun opaque(x: Int, y: Int) = alpha(x, y) > ALPHA_CLEAR
        fun rgb(x: Int, y: Int) = Triple(at(x, y, 0), at(x, y, 1), at(x, y, 2))

        /** Pixels of the sprite that sit within two pixels of the transparent field. */
        private fun rim(): List<Pair<Int, Int>> {
            val out = ArrayList<Pair<Int, Int>>()
            for (y in 0 until h) for (x in 0 until w) {
                if (!opaque(x, y)) continue
                var touches = false
                for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    if (!opaque(nx, ny)) touches = true
                }
                if (touches) out.add(x to y)
            }
            return out
        }

        /** The two grey tones of a checkerboard along the sprite's edge, or null if there is no
         *  such pair. Two tones of nearly the same brightness are a sprite's own dark outline -
         *  every clean sprite here looks like that - hence the brightness gap. Mirrors
         *  tools/art/art.py checker_tones; keep the two in step. */
        fun checkerTones(): Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>? {
            val candidates = rim().filter { (x, y) ->
                val (r, g, b) = rgb(x, y)
                maxOf(r, g, b) - minOf(r, g, b) <= CHECKER_MAX_SATURATION
            }
            if (candidates.size < CHECKER_MIN_SAMPLE) return null
            val buckets = HashMap<Triple<Int, Int, Int>, Int>()
            for ((x, y) in candidates) {
                val (r, g, b) = rgb(x, y)
                val key = Triple(r / 12 * 12 + 6, g / 12 * 12 + 6, b / 12 * 12 + 6)
                buckets[key] = (buckets[key] ?: 0) + 1
            }
            val top = buckets.entries.sortedByDescending { it.value }.take(2)
                .filter { it.value >= CHECKER_MIN_SHARE * candidates.size }
            if (top.size < 2) return null
            fun luma(t: Triple<Int, Int, Int>) = (t.first + t.second + t.third) / 3.0
            if (abs(luma(top[0].key) - luma(top[1].key)) < CHECKER_MIN_CONTRAST) return null
            return top[0].key to top[1].key
        }

        /** The checkerboard's square size, or null if those two tones are not laid out as a grid:
         *  a checkerboard is the one pattern that correlates worst with itself one square away and
         *  best two squares away. Without this, grey stonework reads as a checkerboard. */
        fun checkerBlock(tones: Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>): Int? {
            fun near(x: Int, y: Int, t: Triple<Int, Int, Int>): Boolean {
                val (r, g, b) = rgb(x, y)
                return maxOf(abs(r - t.first), abs(g - t.second), abs(b - t.third)) <= CHECKER_TOLERANCE
            }
            val signed = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                if (!opaque(x, y)) continue
                signed[y * w + x] = when {
                    near(x, y, tones.first) -> 1
                    near(x, y, tones.second) -> -1
                    else -> 0
                }
            }
            val correlation = HashMap<Int, Double>()
            for (lag in 2..24) {
                var sum = 0
                var pairs = 0
                for (y in 0 until h) for (x in 0 until w - lag) {
                    val product = signed[y * w + x] * signed[y * w + x + lag]
                    if (product != 0) { sum += product; pairs++ }
                }
                for (y in 0 until h - lag) for (x in 0 until w) {
                    val product = signed[y * w + x] * signed[(y + lag) * w + x]
                    if (product != 0) { sum += product; pairs++ }
                }
                if (pairs >= CHECKER_MIN_SAMPLE) correlation[lag] = sum.toDouble() / pairs
            }
            val block = (2..12).filter { it in correlation && 2 * it in correlation }
                .minByOrNull { correlation.getValue(it) } ?: return null
            return block.takeIf { correlation.getValue(2 * it) - correlation.getValue(it) >= CHECKER_MIN_GAP }
        }

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

        /** Checkerboard detection, mirroring tools/art/art.py: two tones at least this far apart
         *  in brightness, each at least this share of the sprite's rim, laid out on a grid whose
         *  self-correlation dips at one square and recovers at two. */
        const val CHECKER_MAX_SATURATION = 24
        const val CHECKER_MIN_SAMPLE = 200
        const val CHECKER_MIN_SHARE = 0.10
        const val CHECKER_MIN_CONTRAST = 25.0
        const val CHECKER_TOLERANCE = 22
        const val CHECKER_MIN_GAP = 0.25
        const val EDGE_COLOUR_TOLERANCE = 96
        const val MAX_BAD_EDGE_SHARE = 0.05
        val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}
