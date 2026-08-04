package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

// Light aged-parchment panel now that body text is black (see UiSkinFactory.generateFont) -
// black on the old near-black panel was almost unreadable. Buttons/fields stay a darker brown so
// they still read as distinct, clickable chrome against the lighter body.
private val PANEL_FILL = Color(0.80f, 0.72f, 0.56f, 0.97f)
private val PANEL_BORDER = Color.valueOf("C9A24A")
private val BUTTON_FILL = Color(0.55f, 0.40f, 0.24f, 1f)
private val BUTTON_DOWN_FILL = Color(0.42f, 0.29f, 0.16f, 1f)
private val CHROME_BORDER = Color.valueOf("241609")
private val FIELD_FILL = Color(0.68f, 0.60f, 0.45f, 1f)

/**
 * Shared Skin used by every modal screen, built around one real (CC0) font instead of libGDX's
 * default placeholder font, plus bordered panel/button/field chrome instead of flat solid
 * rectangles - the goal is one consistent look everywhere rather than each screen inventing its
 * own. The font is generated once and reused across every Skin instance (regenerating a glyph
 * atlas per screen would be wasteful); call [disposeSharedFont] once, from the app's own
 * dispose(), after every screen has disposed.
 */
object UiSkinFactory {

    private val sharedFont: BitmapFont by lazy { generateFont(21) }

    fun build(): Skin {
        val skin = Skin()
        val font = sharedFont

        skin.add("panel", borderedDrawable(PANEL_FILL, PANEL_BORDER, 3), Drawable::class.java)
        skin.add("button-up", borderedDrawable(BUTTON_FILL, CHROME_BORDER, 2), Drawable::class.java)
        skin.add("button-down", borderedDrawable(BUTTON_DOWN_FILL, CHROME_BORDER, 2), Drawable::class.java)
        // A subtler bordered box for grouping content within a panel (e.g. one card in a grid of
        // several), distinct from the panel background itself.
        skin.add("field-frame", borderedDrawable(FIELD_FILL, CHROME_BORDER, 2), Drawable::class.java)

        val labelStyle = Label.LabelStyle(font, Color.BLACK)
        skin.add("default", labelStyle)

        // A boxed, centered number - same chrome as a text field - for market-style price badges
        // (buy/sell price flanking a good's icon) instead of a plain inline label.
        val priceLabelStyle = Label.LabelStyle(font, Color.BLACK)
        priceLabelStyle.background = borderedDrawable(FIELD_FILL, CHROME_BORDER, 2, minWidth = 64f, minHeight = 30f)
        skin.add("price", priceLabelStyle)

        val buttonStyle = TextButton.TextButtonStyle()
        buttonStyle.up = skin.getDrawable("button-up")
        buttonStyle.down = skin.getDrawable("button-down")
        buttonStyle.font = font
        buttonStyle.fontColor = Color.BLACK
        skin.add("default", buttonStyle)

        val textFieldStyle = TextField.TextFieldStyle()
        textFieldStyle.font = font
        textFieldStyle.fontColor = Color.BLACK
        textFieldStyle.cursor = solidDrawable(Color.BLACK, minWidth = 2f)
        textFieldStyle.selection = solidDrawable(Color(0.4f, 0.4f, 0.7f, 0.5f))
        textFieldStyle.background = borderedDrawable(FIELD_FILL, CHROME_BORDER, 2, minHeight = 32f)
        skin.add("default", textFieldStyle)

        val sliderStyle = Slider.SliderStyle()
        sliderStyle.background = borderedDrawable(FIELD_FILL, CHROME_BORDER, 2, minHeight = 10f)
        sliderStyle.knob = borderedDrawable(PANEL_BORDER, CHROME_BORDER, 2, minWidth = 18f, minHeight = 22f)
        skin.add("default-horizontal", sliderStyle)

        return skin
    }

    /** Call once from the application's own dispose(), after every screen using this factory has
     *  already disposed its own Skin - the font itself isn't owned by any single Skin, since it's
     *  shared across all of them. */
    fun disposeSharedFont() {
        sharedFont.dispose()
    }

    /** Public so callers outside the Scene2D skin system (e.g. map-space labels drawn straight
     *  onto the world SpriteBatch) can get their OWN independent instance of the same game font -
     *  never hand out [sharedFont] itself for that, since its BitmapFontData.scale is mutable
     *  global state that every skin-based screen relies on staying at the skin's own size. */
    fun generateFont(size: Int, color: Color = Color.BLACK, borderRatio: Float = 0.035f): BitmapFont {
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/cormorant_garamond.ttf"))
        val param = FreeTypeFontGenerator.FreeTypeFontParameter()
        param.size = size
        param.minFilter = Texture.TextureFilter.Linear
        param.magFilter = Texture.TextureFilter.Linear
        param.color = color
        // Cormorant Garamond's default instance is its "Light" weight, and libGDX's FreeType
        // wrapper has no API to pick a heavier weight from a variable font - thickening the
        // outline is the standard way to fake bold out of a font that doesn't have one. The
        // default ratio is kept thin since the outline only grows glyph INK, not the
        // character-spacing metrics, so a wider border eats into the gaps between letters and
        // makes them visually run together - callers overlaying text on busy art (e.g. the port
        // hub's frame) pass a bolder ratio since legibility there matters more than dense text.
        param.borderWidth = size * borderRatio
        param.borderColor = Color.BLACK
        param.borderStraight = true
        val font = generator.generateFont(param)
        generator.dispose()
        return font
    }

    private fun solidDrawable(color: Color, minWidth: Float = 0f, minHeight: Float = 0f): TextureRegionDrawable {
        val pixmap = Pixmap(4, 4, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        val drawable = TextureRegionDrawable(TextureRegion(texture))
        if (minWidth > 0f) drawable.minWidth = minWidth
        if (minHeight > 0f) drawable.minHeight = minHeight
        return drawable
    }

    /** A small fill+border texture wrapped as a NinePatch, so the border stays a constant pixel
     *  width no matter how large the Drawable is stretched - the standard way to get a "framed"
     *  panel/button look out of flat colors without needing external art. */
    private fun borderedDrawable(
        fill: Color,
        border: Color,
        borderWidth: Int,
        minWidth: Float = 0f,
        minHeight: Float = 0f
    ): NinePatchDrawable {
        val size = borderWidth * 2 + 3
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap.setColor(fill)
        pixmap.fill()
        pixmap.setColor(border)
        pixmap.fillRectangle(0, 0, size, borderWidth)
        pixmap.fillRectangle(0, size - borderWidth, size, borderWidth)
        pixmap.fillRectangle(0, 0, borderWidth, size)
        pixmap.fillRectangle(size - borderWidth, 0, borderWidth, size)
        val texture = Texture(pixmap)
        pixmap.dispose()
        val patch = NinePatch(texture, borderWidth, borderWidth, borderWidth, borderWidth)
        val drawable = NinePatchDrawable(patch)
        if (minWidth > 0f) drawable.minWidth = minWidth
        if (minHeight > 0f) drawable.minHeight = minHeight
        return drawable
    }
}
