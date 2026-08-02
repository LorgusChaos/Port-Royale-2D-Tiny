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

private val PANEL_FILL = Color(0.08f, 0.06f, 0.04f, 0.94f)
private val PANEL_BORDER = Color.valueOf("C9A24A")
private val BUTTON_FILL = Color(0.42f, 0.28f, 0.16f, 1f)
private val BUTTON_DOWN_FILL = Color(0.30f, 0.19f, 0.10f, 1f)
private val CHROME_BORDER = Color.valueOf("241609")
private val FIELD_FILL = Color(0.15f, 0.11f, 0.07f, 1f)

/**
 * Shared Skin used by every modal screen, built around one real (CC0) pixel font instead of
 * libGDX's default placeholder font, plus bordered panel/button/field chrome instead of flat
 * solid rectangles - the goal is one consistent, deliberately "pixel game UI" look everywhere
 * rather than each screen inventing its own. The font is generated once and reused across every
 * Skin instance (regenerating a glyph atlas per screen would be wasteful); call
 * [disposeSharedFont] once, from the app's own dispose(), after every screen has disposed.
 */
object UiSkinFactory {

    private val sharedFont: BitmapFont by lazy { generateFont(16) }

    fun build(): Skin {
        val skin = Skin()
        val font = sharedFont

        skin.add("panel", borderedDrawable(PANEL_FILL, PANEL_BORDER, 3), Drawable::class.java)
        skin.add("button-up", borderedDrawable(BUTTON_FILL, CHROME_BORDER, 2), Drawable::class.java)
        skin.add("button-down", borderedDrawable(BUTTON_DOWN_FILL, CHROME_BORDER, 2), Drawable::class.java)

        val labelStyle = Label.LabelStyle(font, Color.WHITE)
        skin.add("default", labelStyle)

        val buttonStyle = TextButton.TextButtonStyle()
        buttonStyle.up = skin.getDrawable("button-up")
        buttonStyle.down = skin.getDrawable("button-down")
        buttonStyle.font = font
        buttonStyle.fontColor = Color.WHITE
        skin.add("default", buttonStyle)

        val textFieldStyle = TextField.TextFieldStyle()
        textFieldStyle.font = font
        textFieldStyle.fontColor = Color.WHITE
        textFieldStyle.cursor = solidDrawable(Color.WHITE, minWidth = 2f)
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

    private fun generateFont(size: Int): BitmapFont {
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/kenney_pixel.ttf"))
        val param = FreeTypeFontGenerator.FreeTypeFontParameter()
        param.size = size
        param.minFilter = Texture.TextureFilter.Nearest
        param.magFilter = Texture.TextureFilter.Nearest
        param.color = Color.WHITE
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
