package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture

/**
 * The one place ship artwork is loaded, shared by the world map, the shipyard and combat.
 *
 * Three screens used to keep three caches of the same files, each with libGDX's default Nearest
 * filter and no mipmaps. That is the wrong filter for this art: a ship sprite is painted at
 * 60-115px wide and drawn at a fraction of that, rotated, so Nearest picked individual pixels and
 * the edges crawled as the hull turned. Mipmaps + Linear is the same treatment the map and the
 * port icons already get, for the same reason (see IconFactory.buildArtIcon).
 *
 * Linear filtering only looks right because the sprites' transparent pixels carry the colour of
 * the hull next to them (tools/art/art.py `bleed`, checked by AssetIntegrityTest). Without that,
 * the GPU blends in whatever the cut-out background was and every ship grows a white rim.
 *
 * Owned by PortRoyaleGame: disposed once, in its dispose(), never by the screens that borrow it.
 */
object ShipArt {

    private val textures = mutableMapOf<String, Texture>()

    fun texture(path: String): Texture = textures.getOrPut(path) {
        Texture(Gdx.files.internal(path), true).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        }
    }

    fun dispose() {
        textures.values.forEach { it.dispose() }
        textures.clear()
    }
}
