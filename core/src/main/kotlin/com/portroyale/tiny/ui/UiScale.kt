package com.portroyale.tiny.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport

private const val REFERENCE_WIDTH = 1280f
private const val MIN_FACTOR = 0.8f
private const val MAX_FACTOR = 1.5f

/**
 * Every modal screen's Stage should use [viewport] instead of a bare ScreenViewport(), and every
 * screen's resize() should call [applyResize] instead of updating the viewport directly. Setting
 * unitsPerPixel below 1 makes the Stage's "unit" space smaller than the actual window, so every
 * widget - text, buttons, padding, icons - renders proportionally bigger without any of the
 * screens needing to hand-tune their own size numbers. The factor scales with the actual window
 * width (clamped) rather than being fixed, so content still fits at the default 1280x720 launch
 * size instead of overflowing, while still scaling up nicely on larger windows.
 */
object UiScale {
    private fun factorFor(width: Int): Float = (width / REFERENCE_WIDTH).coerceIn(MIN_FACTOR, MAX_FACTOR)

    fun viewport(): ScreenViewport =
        ScreenViewport().apply { unitsPerPixel = 1f / factorFor(Gdx.graphics.width) }

    fun applyResize(stage: Stage, width: Int, height: Int) {
        (stage.viewport as? ScreenViewport)?.unitsPerPixel = 1f / factorFor(width)
        stage.viewport.update(width, height, true)
    }
}
