package com.portroyale.tiny.ui

import com.badlogic.gdx.utils.viewport.ScreenViewport

private const val FACTOR = 1.5f

/**
 * Every modal screen's Stage should use [viewport] instead of a bare ScreenViewport(). Setting
 * unitsPerPixel below 1 makes the Stage's "unit" space smaller than the actual window, so every
 * widget - text, buttons, padding, icons - renders proportionally bigger without any of the
 * screens needing to hand-tune their own size numbers. One shared factor here instead of
 * scattering it across eleven files also means a single place to retune it later.
 */
object UiScale {
    fun viewport(): ScreenViewport = ScreenViewport().apply { unitsPerPixel = 1f / FACTOR }
}
