package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/** Minimal programmatic Skin shared by every modal screen - solid-color buttons/panel and the
 *  default bitmap font, no external skin asset needed. Real UI art can replace this later. */
object UiSkinFactory {

    fun build(): Skin {
        val skin = Skin()
        val font = BitmapFont()

        skin.add("default-font", font, BitmapFont::class.java)
        skin.add("panel", solidDrawable(Color(0.08f, 0.06f, 0.04f, 0.88f)), Drawable::class.java)
        skin.add("button-up", solidDrawable(Color(0.42f, 0.28f, 0.16f, 1f)), Drawable::class.java)
        skin.add("button-down", solidDrawable(Color(0.30f, 0.19f, 0.10f, 1f)), Drawable::class.java)

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
        textFieldStyle.background = solidDrawable(Color(0.15f, 0.11f, 0.07f, 1f), minHeight = 30f)
        skin.add("default", textFieldStyle)

        val sliderStyle = Slider.SliderStyle()
        sliderStyle.background = solidDrawable(Color(0.25f, 0.18f, 0.10f, 1f), minHeight = 8f)
        sliderStyle.knob = solidDrawable(Color(0.85f, 0.75f, 0.55f, 1f), minWidth = 16f, minHeight = 20f)
        skin.add("default-horizontal", sliderStyle)

        return skin
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
}
