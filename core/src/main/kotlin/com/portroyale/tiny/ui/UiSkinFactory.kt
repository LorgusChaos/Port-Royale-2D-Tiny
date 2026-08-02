package com.portroyale.tiny.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
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

        return skin
    }

    private fun solidDrawable(color: Color): TextureRegionDrawable {
        val pixmap = Pixmap(4, 4, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        return TextureRegionDrawable(TextureRegion(texture))
    }
}
