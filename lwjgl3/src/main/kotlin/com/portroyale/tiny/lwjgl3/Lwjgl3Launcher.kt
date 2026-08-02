package com.portroyale.tiny.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.portroyale.tiny.PortRoyaleGame

object Lwjgl3Launcher {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration()
        config.setTitle("Port Royale 2D Tiny v0.1")
        config.setWindowedMode(1280, 720)
        config.setResizable(true)
        config.useVsync(true)
        config.setForegroundFPS(60)
        Lwjgl3Application(PortRoyaleGame(), config)
    }
}
