package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/**
 * The painted HUD chrome: the plates, panels and buttons that sit over the sea.
 *
 * Shared and disposed once by PortRoyaleGame, the same way [ShipArt] and [PortFrame] are - never
 * by the screen that borrows them. Every piece was generated on flat magenta and cut with
 * `tools/art/art.py key` (see CLAUDE.md for the exact recipe); what ships here is already
 * background-free and edge-bled, so it can be drawn with mipmaps and Linear filtering at whatever
 * size the window asks for.
 */
object HudArt {

    private val textures = mutableMapOf<String, Texture>()
    private var white: Texture? = null

    fun texture(name: String): Texture = textures.getOrPut(name) {
        Texture(Gdx.files.internal("sprites/ui/hud/$name.png"), true).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        }
    }

    /** One white pixel, stretched and tinted to fill a bar's trough. Bars used to rebuild a
     *  Pixmap texture every time a value moved; a stretched pixel costs nothing and never needs
     *  disposing per value. */
    fun fill(colour: Color): Image {
        val texture = white ?: Pixmap(1, 1, Pixmap.Format.RGBA8888).let { pixmap ->
            pixmap.setColor(Color.WHITE)
            pixmap.fill()
            Texture(pixmap).also { white = it; pixmap.dispose() }
        }
        val image = Image(TextureRegionDrawable(TextureRegion(texture)))
        image.color = colour
        return image
    }

    fun dispose() {
        textures.values.forEach { it.dispose() }
        textures.clear()
        white?.dispose()
        white = null
    }
}

/**
 * One painted element placed on the HUD, and the bridge between the two coordinate spaces this
 * involves: the artwork's own pixels (y down from its top-left corner, which is how every box in
 * this file was measured off the file) and the Stage's world units (y up).
 *
 * [place] sizes the element by width alone - the height follows from the picture's own aspect, so
 * nothing is ever stretched. [box] then converts a rectangle measured on the artwork into the
 * world rectangle it currently occupies, which is how a label lands exactly on a brass strip and
 * a bar fill lands exactly inside its trough at any scale.
 */
class HudPiece(name: String) {

    private val source = HudArt.texture(name)
    val image = Image(TextureRegionDrawable(TextureRegion(source)))

    /** The artwork's own size, which every measured box in HudOverlayUI is expressed against. */
    val assetW = source.width.toFloat()
    val assetH = source.height.toFloat()

    var scale = 1f
        private set

    val width get() = assetW * scale
    val height get() = assetH * scale
    val x get() = image.x
    val y get() = image.y

    /** Place by the element's top-left corner, in world units, at a given width. */
    fun place(left: Float, top: Float, width: Float) {
        scale = width / assetW
        image.setBounds(left, top - assetH * scale, width, assetH * scale)
    }

    /** A rectangle measured on the artwork (y down) as it currently sits in the world (y up). */
    fun box(ax: Float, ay: Float, aw: Float, ah: Float): Rectangle = Rectangle(
        image.x + ax * scale,
        image.y + (assetH - ay - ah) * scale,
        aw * scale,
        ah * scale,
    )
}
