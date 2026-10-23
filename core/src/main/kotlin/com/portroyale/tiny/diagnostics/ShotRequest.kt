package com.portroyale.tiny.diagnostics

/** Which screen a [ShotRequest] opens. Port screens are entered by docking at [ShotRequest.port]. */
enum class ShotScreen { MENU, SEA, HUB, MARKET, SHIPYARD, STATUS, FORTRESS, WAREHOUSE, TAVERN, DOCKS }

/**
 * A launch that opens one screen, renders it for a while, saves a screenshot and quits:
 *
 *     gradlew :lwjgl3:run --args="--shot=market --shot-port=Havana --shot-out=build/shots/market.png"
 *
 * It exists so a visual change can be looked at without a person at the keyboard. CLAUDE.md's
 * rule still stands - an exit code says nothing about how a screen looks, and the user has the
 * final word - but now the first look happens before the user is asked, not instead of it.
 *
 * A shot always starts from a fresh new game, never from a save, so the same arguments give the
 * same picture: a screenshot of "whatever my last session left behind" proves nothing on its own.
 * Pass --shot-seed for AI ships and sea events that land in the same places every time.
 */
data class ShotRequest(
    val screen: ShotScreen,
    /** Relative paths resolve against the working directory, which for `:lwjgl3:run` is lwjgl3/. */
    val outPath: String = "build/shots/${screen.name.lowercase()}.png",
    /** Frames rendered before the grab - enough for Scene2D layout and toasts to settle. */
    val frames: Int = 30,
    val seed: Long? = null,
    val port: String = "Port Royal",
    val width: Int = 1280,
    val height: Int = 720
) {
    companion object {
        const val USAGE = "--shot=<menu|sea|hub|market|shipyard|status|fortress|warehouse|tavern|docks> " +
            "[--shot-out=<png path>] [--shot-frames=<n>] [--shot-seed=<n>] [--shot-port=<port name>] [--shot-size=<w>x<h>]"

        /** Null when the launch is an ordinary one (no --shot argument). Throws
         *  IllegalArgumentException with a readable message for anything malformed, so a typo in
         *  a screen name fails loudly instead of silently screenshotting the main menu. */
        fun parse(args: Array<String>): ShotRequest? {
            val options = args.filter { it.startsWith("--shot") }.associate { arg ->
                val eq = arg.indexOf('=')
                require(eq > 0) { "expected $arg=<value>. Usage: $USAGE" }
                arg.substring(0, eq) to arg.substring(eq + 1)
            }
            if (options.isEmpty()) return null
            val unknown = options.keys - setOf("--shot", "--shot-out", "--shot-frames", "--shot-seed", "--shot-port", "--shot-size")
            require(unknown.isEmpty()) { "unknown option(s) $unknown. Usage: $USAGE" }
            val screenName = requireNotNull(options["--shot"]) { "--shot-* options need --shot=<screen>. Usage: $USAGE" }
            val screen = ShotScreen.entries.firstOrNull { it.name.equals(screenName, ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown screen '$screenName'. Usage: $USAGE")

            var request = ShotRequest(screen)
            options["--shot-out"]?.let { request = request.copy(outPath = it) }
            options["--shot-frames"]?.let {
                val frames = requireNotNull(it.toIntOrNull()) { "--shot-frames must be a number, got '$it'" }
                require(frames >= 1) { "--shot-frames must be at least 1, got $frames" }
                request = request.copy(frames = frames)
            }
            options["--shot-seed"]?.let {
                request = request.copy(seed = requireNotNull(it.toLongOrNull()) { "--shot-seed must be a number, got '$it'" })
            }
            options["--shot-port"]?.let { request = request.copy(port = it) }
            options["--shot-size"]?.let {
                val parts = it.lowercase().split('x').mapNotNull { n -> n.toIntOrNull() }
                require(parts.size == 2 && parts.all { n -> n >= 320 }) { "--shot-size must look like 1280x720, got '$it'" }
                request = request.copy(width = parts[0], height = parts[1])
            }
            return request
        }
    }
}
