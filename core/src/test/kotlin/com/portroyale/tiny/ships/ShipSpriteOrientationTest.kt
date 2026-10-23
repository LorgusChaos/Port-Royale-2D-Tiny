package com.portroyale.tiny.ships

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every hull sails bow first.
 *
 * The renderer knows one thing about ship artwork: the bow points up the image. It rotates a
 * sprite by the heading less ninety degrees, and that is the whole of it - so a sprite drawn the
 * other way round sails backwards, and does it silently, because nothing in the code is wrong.
 * The project has already shipped this bug once (v0.4.5.x, "fixed ships rendering stern-first")
 * and shipped it again with the ship's boat, which comes from an older asset set drawn bow-down.
 *
 * Hence a test over the artwork itself. [ShipType.spriteRotationDeg] states which way round each
 * class is drawn, and this checks the claim against the pixels - a Pixmap needs libGDX natives
 * but no GL context, the same trick WorldMap's tests use.
 */
class ShipSpriteOrientationTest {

    @BeforeTest
    fun loadNatives() {
        GdxNativesLoader.load()
    }

    /**
     * How much of the artwork sits in the end sixth of the image, by opaque pixel count.
     *
     * Mass rather than width, and that distinction was measured rather than assumed: a Galleon's
     * spread sails make the bow end of its bounding box *wider* than the stern, so width reports
     * the orientation backwards on exactly the largest hull. Ink does not - a bow tapers to a
     * point and a stern carries the hull, the castle and the rigging, on every sprite in the set.
     */
    private fun inkAtEnds(spritePath: String): Pair<Int, Int> {
        val bytes = File("../assets/$spritePath").readBytes()
        val pixmap = Pixmap(bytes, 0, bytes.size)
        try {
            val band = pixmap.height / 6
            var top = 0
            var bottom = 0
            for (y in 0 until pixmap.height) {
                val end = when {
                    y < band -> 0
                    y >= pixmap.height - band -> 1
                    else -> continue
                }
                for (x in 0 until pixmap.width) {
                    // Pixmap hands back RGBA8888, so alpha is the low byte.
                    if ((pixmap.getPixel(x, y) and 0xFF) > 40) {
                        if (end == 0) top++ else bottom++
                    }
                }
            }
            return top to bottom
        } finally {
            pixmap.dispose()
        }
    }

    @Test
    fun `every hull is drawn the way its class says it is drawn`() {
        for (shipType in ShipTypes.ALL + ShipTypes.LONGBOAT) {
            val (topInk, bottomInk) = inkAtEnds(shipType.spritePath)
            assertTrue(
                topInk > 0 && bottomInk > 0,
                "${shipType.name}: ${shipType.spritePath} looks empty at one end"
            )
            val bowIsUp = when (shipType.spriteRotationDeg) {
                0f -> true
                180f -> false
                else -> throw AssertionError(
                    "${shipType.name} is drawn at ${shipType.spriteRotationDeg} degrees, which " +
                        "this test cannot judge - decide what it means before adding it"
                )
            }
            val bow = if (bowIsUp) topInk else bottomInk
            val stern = if (bowIsUp) bottomInk else topInk
            assertTrue(
                bow < stern * BOW_INK_MARGIN,
                "${shipType.name} (${shipType.spritePath}) claims its bow is at the " +
                    "${if (bowIsUp) "top" else "bottom"} of the image, but that end carries $bow " +
                    "pixels against $stern at the other - it will sail stern first"
            )
        }
    }

    private companion object {
        /** The bow end must be clearly the lighter one. The narrowest real margin in the current
         *  set is 0.68 (the smallest ship), and a sprite drawn the wrong way round comes out
         *  above 1.4 - so this sits well clear of both. */
        const val BOW_INK_MARGIN = 0.9f
    }
}
