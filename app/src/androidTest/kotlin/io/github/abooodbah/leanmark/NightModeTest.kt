package io.github.abooodbah.leanmark

import android.app.Activity
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching the phone between light and dark keeps the activity, the WebView,
 * the document and the reading position. Only the System theme changes
 * colour; a chosen theme stays as it is. Android 10 is the first to switch
 * dark mode outside car mode, so these run from API 29.
 */
@RunWith(AndroidJUnit4::class)
class NightModeTest {
    private val prefs get() = Reader.context.getSharedPreferences("reader", 0)

    @Before
    fun startLightWithDefaults() {
        assumeTrue("dark mode switches from Android 10", Build.VERSION.SDK_INT >= 29)
        prefs.edit().clear().commit()
        night(false)
    }

    @After
    fun backToLight() {
        if (Build.VERSION.SDK_INT >= 29) night(false)
        prefs.edit().clear().commit()
    }

    @Test
    fun theSystemThemeRecoloursInPlace() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        Reader.js(scenario, "window.scrollTo(0, 900)")
        Reader.waitFor(scenario, "window.scrollY >= 800")
        val before = place(scenario)
        val activity = activity(scenario)
        val view = Reader.webView(scenario)
        shell("logcat -c")
        night(true)
        Reader.waitFor(scenario, "getComputedStyle(document.documentElement).getPropertyValue('--canvas').trim() === '#0e0d0b'")
        assertTrue("the activity was kept", activity(scenario) === activity)
        assertTrue("the WebView was kept", Reader.webView(scenario) === view)
        assertEquals("LeanMark renderer showcase", Reader.text(scenario, "#article h1")?.trim())
        assertEquals("the reading position", before, place(scenario), 50.0)
        // The budgets job reads how long the switch took from this line.
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!Regex("page night \\d+ ms").containsMatchIn(shell("logcat -d -v raw -s LeanMarkTiming:I")) &&
            SystemClock.uptimeMillis() < deadline
        ) SystemClock.sleep(200)
        assertTrue("the switch is timed", Regex("page night \\d+ ms").containsMatchIn(shell("logcat -d -v raw -s LeanMarkTiming:I")))
        Reader.instrumentation.waitForIdleSync()
        scenario.onActivity {
            @Suppress("DEPRECATION")
            assertEquals("dark bars", 0xFF1A1917.toInt(), it.window.statusBarColor)
        }
        night(false)
        Reader.waitFor(scenario, "getComputedStyle(document.documentElement).getPropertyValue('--canvas').trim() === '#faf9f7'")
        assertTrue("the activity was kept", activity(scenario) === activity)
    }

    @Test
    fun aChosenThemeStaysAsItIs() {
        prefs.edit().putString("theme", "dusk").commit()
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            val activity = activity(scenario)
            night(true)
            // Give the switch time to arrive, then check nothing changed.
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            assertTrue("the activity was kept", activity(scenario) === activity)
            assertEquals("dusk", Reader.js(scenario, "document.documentElement.dataset.theme"))
            assertEquals("#221e19", Reader.js(scenario, "getComputedStyle(document.documentElement).getPropertyValue('--canvas').trim()"))
            scenario.onActivity {
                @Suppress("DEPRECATION")
                assertEquals("dusk bars", 0xFF29241E.toInt(), it.window.statusBarColor)
            }
        }
    }

    @Test
    fun theNewColoursAreOnScreenBeforeTheDiagramsAreDrawnAgain() {
        assumeTrue("Mermaid needs WebView 94 or later", Reader.webViewMajor >= 94)
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario, timeoutMs = 60_000)
            shell("logcat -c")
            night(true)
            val pattern = Regex("page scheme-diagrams (\\d+) ms")
            val deadline = SystemClock.uptimeMillis() + 30_000
            var log = ""
            while (!pattern.containsMatchIn(log) && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(200)
                log = shell("logcat -d -v raw -s LeanMarkTiming:I")
            }
            val colours = Regex("page scheme (\\d+) ms").find(log)!!.groupValues[1].toLong()
            val diagrams = pattern.find(log)!!.groupValues[1].toLong()
            assertTrue("colours after $colours ms, diagrams after $diagrams ms", colours * 2 < diagrams)
        }
    }

    private fun place(scenario: ActivityScenario<MainActivity>): Double =
        (Reader.js(scenario, "window.scrollY") as Number).toDouble()

    private fun activity(scenario: ActivityScenario<MainActivity>): Activity {
        var found: Activity? = null
        scenario.onActivity { found = it }
        return found!!
    }

    private fun shell(command: String): String {
        val output = Reader.instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    private fun night(on: Boolean) {
        val output = Reader.instrumentation.uiAutomation.executeShellCommand("cmd uimode night " + if (on) "yes" else "no")
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        Reader.instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
    }
}
