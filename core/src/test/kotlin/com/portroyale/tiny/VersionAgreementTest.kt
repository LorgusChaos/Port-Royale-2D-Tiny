package com.portroyale.tiny

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The version lives in two places: `appVersion` in gradle.properties (the jar's name, the release)
 * and `VERSION` in the desktop launcher (the window title, and what the game reports in its logs).
 * A release has to change both, and nothing caught them drifting apart (REVIEW.md, finding 27).
 * This test is that catch, for as long as the second copy exists.
 */
class VersionAgreementTest {

    @Test
    fun `the launcher states the same version as the build`() {
        val build = Regex("""(?m)^appVersion\s*=\s*(\S+)""").find(File("../gradle.properties").readText())?.groupValues?.get(1)
        val launcherFile = File("../lwjgl3/src/main/kotlin/com/portroyale/tiny/lwjgl3/Lwjgl3Launcher.kt")
        val launcher = Regex("""const val VERSION\s*=\s*"([^"]+)"""").find(launcherFile.readText())?.groupValues?.get(1)

        assertNotNull(build, "no appVersion in gradle.properties")
        assertNotNull(launcher, "no VERSION in ${launcherFile.path}")
        assertEquals(build, launcher, "gradle.properties says $build, the launcher says $launcher")
    }
}
