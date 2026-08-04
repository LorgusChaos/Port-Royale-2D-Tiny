package com.portroyale.tiny.lwjgl3

import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.portroyale.tiny.PortRoyaleGame

object Lwjgl3Launcher {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration()
        config.setTitle("Port Royale 2D Tiny v0.4.5")
        config.setWindowedMode(1280, 720)
        config.setResizable(true)
        config.useVsync(true)
        config.setForegroundFPS(60)
        // Force raw framebuffer pixels everywhere (input, resize, graphics size) so mouse
        // clicks and rendering can't disagree on Windows displays with DPI scaling above 100%.
        config.setHdpiMode(HdpiMode.Pixels)
        Lwjgl3Application(PortRoyaleGame(), config)
    }
}
