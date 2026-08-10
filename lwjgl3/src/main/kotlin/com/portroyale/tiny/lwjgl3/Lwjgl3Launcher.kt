package com.portroyale.tiny.lwjgl3

import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.portroyale.tiny.PortRoyaleGame

object Lwjgl3Launcher {
    /** The one place the running build states its own version - the window title and the
     *  diagnostic log/crash report all read it from here, so a release bump stays a single edit
     *  in a single file (see CLAUDE.md's release workflow). */
    private const val VERSION = "0.4.5.6"

    @JvmStatic
    fun main(args: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration()
        config.setTitle("Port Royale 2D Tiny v$VERSION")
        config.setWindowedMode(1280, 720)
        config.setResizable(true)
        config.useVsync(true)
        config.setForegroundFPS(60)
        // Force raw framebuffer pixels everywhere (input, resize, graphics size) so mouse
        // clicks and rendering can't disagree on Windows displays with DPI scaling above 100%.
        config.setHdpiMode(HdpiMode.Pixels)
        Lwjgl3Application(PortRoyaleGame(VERSION), config)
    }
}
