package io.github.abooodbah.leanmark

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

/**
 * The reader themes from desktop LeanMark 0.5.0: System, Light, Dark, Dim and
 * Dusk. These follow the desktop's tests/Invoke-ReaderBrowserSmoke.mjs, with
 * the Android window colours added.
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest {
    private val prefs get() = Reader.context.getSharedPreferences("reader", 0)

    @Before
    fun startFromSystem() {
        prefs.edit().clear().commit()
    }

    @Test
    fun theThemeButtonCyclesTheDesktopThemesInOrder() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        assertEquals(
            "the theme button is shown on a phone",
            true,
            Reader.js(scenario, "getComputedStyle(document.getElementById('themeButton')).display !== 'none'"),
        )
        val labels = mutableListOf<String>()
        val saved = mutableListOf<String?>()
        repeat(5) {
            Reader.js(scenario, "document.getElementById('themeButton').click()")
            val label = Reader.text(scenario, "#themeButton")!!.trim()
            labels += label
            Reader.waitFor(scenario, "true") // let the message reach the app
            waitForSaved(label.lowercase())
            saved += prefs.getString("theme", null)
        }
        assertEquals(listOf("Light", "Dark", "Dim", "Dusk", "System"), labels)
        assertEquals(listOf("light", "dark", "dim", "dusk", "system"), saved)
    }

    @Test
    fun dimAndDuskApplyTheDesktopTokenValues() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        for ((name, expected) in SOFT_THEME_TOKENS) {
            choose(scenario, name)
            val actual = expected.keys.associateWith { token(scenario, it) }
            assertEquals("$name tokens", expected, actual)
            assertEquals("$name colour scheme", "dark", Reader.js(scenario, "getComputedStyle(document.documentElement).colorScheme"))
        }
    }

    @Test
    fun lightAndDarkKeepTheirColours() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        choose(scenario, "light")
        assertEquals("#faf9f7", token(scenario, "--canvas"))
        assertEquals("#1a1917", token(scenario, "--text-primary"))
        choose(scenario, "dark")
        assertEquals("#0e0d0b", token(scenario, "--canvas"))
        assertEquals("#faf9f7", token(scenario, "--text-primary"))
    }

    @Test
    fun softThemeTextMeetsWcagAaAndIsSofterThanDark() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        choose(scenario, "dark")
        val darkBody = contrast(style(scenario, "#article p", "color"), style(scenario, "body", "backgroundColor"))
        for (name in listOf("dim", "dusk")) {
            choose(scenario, name)
            val canvas = style(scenario, "body", "backgroundColor")
            val parts = mapOf(
                "body" to contrast(style(scenario, "#article p", "color"), canvas),
                "headings" to contrast(style(scenario, "#article h2", "color"), canvas),
                "links" to contrast(style(scenario, "#article a", "color"), canvas),
                "code block" to contrast(style(scenario, "#article pre code", "color"), style(scenario, "#article pre", "backgroundColor")),
                "table text" to contrast(style(scenario, "#article td", "color"), canvas),
                "selection" to contrast(token(scenario, "--text-primary"), token(scenario, "--selection")),
                "find" to contrast(token(scenario, "--find-text"), token(scenario, "--find")),
                "current find" to contrast(token(scenario, "--find-text"), token(scenario, "--find-current")),
            )
            val low = parts.filterValues { it < 4.5 }
            assertTrue("$name text below 4.5:1: $low", low.isEmpty())
            assertTrue("$name body ${parts["body"]} against dark $darkBody", parts.getValue("body") <= darkBody * 0.65)
        }
    }

    @Test
    fun theWindowAroundThePageTakesEachThemesColours() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        for (name in listOf("light", "dark", "dim", "dusk")) {
            choose(scenario, name)
            Reader.instrumentation.waitForIdleSync()
            val surface = color(token(scenario, "--surface"))
            val canvas = color(token(scenario, "--canvas"))
            scenario.onActivity { activity ->
                @Suppress("DEPRECATION")
                assertEquals("$name status bar", surface, activity.window.statusBarColor)
                assertEquals("$name window", canvas, (activity.window.decorView.background as ColorDrawable).color)
                assertEquals("$name core canvas", canvas, MarkdownCore.themeCanvas(name, false))
                assertEquals("$name light bar icons", name == "light", lightBarIcons(activity))
            }
        }
    }

    @Test
    fun aSavedThemeIsUsedOnTheNextStart() {
        prefs.edit().putString("theme", "dusk").commit()
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("dusk", Reader.js(scenario, "document.documentElement.dataset.theme"))
            assertEquals("Dusk", Reader.text(scenario, "#themeButton")?.trim())
            scenario.onActivity { activity ->
                assertEquals(MarkdownCore.themeCanvas("dusk", false), (activity.window.decorView.background as ColorDrawable).color)
            }
        }
    }

    @Test
    fun anUnknownSavedThemeGivesSystem() {
        prefs.edit().putString("theme", "neon").commit()
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals(null, Reader.js(scenario, "document.documentElement.getAttribute('data-theme')"))
            assertEquals("System", Reader.text(scenario, "#themeButton")?.trim())
        }
    }

    @Test
    fun diagramsInSoftThemesAreDrawnWithTheirTokens() {
        assumeTrue("Mermaid needs WebView 94 or later", Reader.webViewMajor >= 94)
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario, timeoutMs = 60_000)
            for (name in listOf("dim", "dusk")) {
                choose(scenario, name)
                Reader.waitFor(scenario, "!!document.querySelector('#article figure.diagram svg')", 60_000)
                val fill = Reader.js(
                    scenario,
                    "(function(){var n=document.querySelector('#article figure.diagram svg .node rect, " +
                        "#article figure.diagram svg .node polygon');return n?getComputedStyle(n).fill:null})()",
                ) as String
                assertEquals("$name diagram boxes", color(token(scenario, "--surface-muted")), color(fill))
            }
        }
    }

    private fun choose(scenario: ActivityScenario<MainActivity>, name: String) {
        val label = name.replaceFirstChar { it.uppercase() }
        repeat(6) {
            if (Reader.text(scenario, "#themeButton")?.trim() == label) return@repeat
            Reader.js(scenario, "document.getElementById('themeButton').click()")
        }
        assertEquals(label, Reader.text(scenario, "#themeButton")?.trim())
        Reader.waitFor(scenario, "document.documentElement.dataset.renderState === 'ready'")
        waitForSaved(name)
    }

    private fun waitForSaved(name: String) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (prefs.getString("theme", null) != name && android.os.SystemClock.uptimeMillis() < deadline) {
            android.os.SystemClock.sleep(50)
        }
        Reader.instrumentation.waitForIdleSync()
    }

    private fun token(scenario: ActivityScenario<MainActivity>, name: String): String =
        (Reader.js(scenario, "getComputedStyle(document.documentElement).getPropertyValue('$name').trim()") as String)
            .lowercase()

    private fun style(scenario: ActivityScenario<MainActivity>, selector: String, property: String): String =
        Reader.js(scenario, "getComputedStyle(document.querySelector(${JSONObject.quote(selector)})).$property") as String

    private fun lightBarIcons(activity: MainActivity): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            activity.window.insetsController!!.systemBarsAppearance and
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
        }

    companion object {
        /** tests/Invoke-ReaderBrowserSmoke.mjs in desktop LeanMark 0.5.0. */
        val SOFT_THEME_TOKENS = mapOf(
            "dim" to mapOf(
                "--canvas" to "#1e2023", "--surface" to "#25282c", "--surface-muted" to "#2d3035",
                "--border" to "#393d43", "--text-primary" to "#c8ccd2", "--text-secondary" to "#9ea4ac",
                "--link" to "#86aee6", "--code-canvas" to "#282b30", "--selection" to "#34486a",
                "--find" to "#5c4d1f", "--find-current" to "#7a5317", "--find-text" to "#e8e4dc",
                "--quote-rule" to "#4a4f56",
            ),
            "dusk" to mapOf(
                "--canvas" to "#221e19", "--surface" to "#29241e", "--surface-muted" to "#322c25",
                "--border" to "#3f3830", "--text-primary" to "#d4c8b4", "--text-secondary" to "#a99c87",
                "--link" to "#e0a86e", "--code-canvas" to "#2c2721", "--selection" to "#4d3f2c",
                "--find" to "#6a5420", "--find-current" to "#82561c", "--find-text" to "#f1e9db",
                "--quote-rule" to "#574c3f",
            ),
        )

        /** An opaque ARGB colour from "#rrggbb", "rgb(r, g, b)" or "rgba(r, g, b, a)". */
        fun color(text: String): Int {
            val value = text.trim().lowercase()
            Regex("^#([0-9a-f]{6})$").find(value)?.let { return (0xFF000000 or it.groupValues[1].toLong(16)).toInt() }
            val parts = Regex("rgba?\\(\\s*([\\d.]+)[ ,]+([\\d.]+)[ ,]+([\\d.]+)").find(value)
                ?: error("not a colour: $text")
            val (r, g, b) = parts.destructured
            return (0xFF shl 24) or (r.toDouble().toInt() shl 16) or (g.toDouble().toInt() shl 8) or b.toDouble().toInt()
        }

        /** WCAG 2 contrast ratio between two colours. */
        fun contrast(a: String, b: String): Double {
            fun luminance(text: String): Double {
                val argb = color(text)
                fun channel(shift: Int): Double {
                    val c = ((argb shr shift) and 0xFF) / 255.0
                    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
                }
                return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
            }
            val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
            return (light + 0.05) / (dark + 0.05)
        }
    }
}
