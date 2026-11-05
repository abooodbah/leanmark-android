package io.github.abooodbah.leanmark

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The budgets job (scripts/measure.sh) times page work in release builds from
 * the lines the app logs under LeanMarkTiming. These tests check that each
 * kind of work it reads is logged, with a duration in whole milliseconds.
 */
@RunWith(AndroidJUnit4::class)
class PageTimingTest {

    @Before
    fun clearTheLog() {
        shell("logcat -c")
        clearSettings()
    }

    // A zoom or theme saved here would change what later test classes see.
    @After
    fun clearSettings() {
        Reader.context.getSharedPreferences("reader", 0).edit().clear().commit()
    }

    @Test
    fun loadingThePageAndDrawingTheDocumentAreTimed() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        waitForLine("page page-dom")
        waitForLine("page page-load")
        waitForLine("page render")
        waitForLine("phase file read")
        waitForLine("phase rendered")
    }

    @Test
    fun aThemeSwitchIsTimed() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        Reader.js(scenario, "document.getElementById('themeButton').click()")
        waitForLine("page theme")
    }

    @Test
    fun aZoomStepIsTimed() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        Reader.js(
            scenario,
            "document.dispatchEvent(new KeyboardEvent('keydown', {key: '=', ctrlKey: true, bubbles: true, cancelable: true}))",
        )
        waitForLine("page zoom")
    }

    @Test
    fun aSearchIsTimed() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        Reader.js(scenario, "var f=document.getElementById('findInput'); f.value='bullet'; f.dispatchEvent(new Event('input', {bubbles: true}))")
        waitForLine("page find")
    }

    @Test
    fun aResizeIsTimed() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        shell("logcat -c")
        scenario.onActivity { activity ->
            val view = activity.webViewForTest!!
            view.layoutParams = view.layoutParams.apply { width = view.width / 2 }
        }
        waitForLine("page resize")
    }

    @Test
    fun theWebViewTakesFocusSoKeyboardShortcutsReachThePage() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        var focused = false
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!focused && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { focused = it.webViewForTest?.hasFocus() == true }
            if (!focused) SystemClock.sleep(50)
        }
        assertTrue("the WebView has focus", focused)
    }

    /** Waits for a LeanMarkTiming line that starts with [prefix] and ends in "N ms". */
    private fun waitForLine(prefix: String) {
        val pattern = Regex("""\b${Regex.escape(prefix)} \d+ ms\b""")
        val deadline = SystemClock.uptimeMillis() + 30_000
        var log = ""
        while (SystemClock.uptimeMillis() < deadline) {
            log = shell("logcat -d -v raw -s LeanMarkTiming:I")
            if (pattern.containsMatchIn(log)) return
            SystemClock.sleep(200)
        }
        fail("no \"$prefix N ms\" line in the log:\n$log")
    }

    private fun shell(command: String): String {
        val output = Reader.instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }
}
