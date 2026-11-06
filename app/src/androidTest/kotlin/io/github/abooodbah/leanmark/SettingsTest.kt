package io.github.abooodbah.leanmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Desktop LeanMark 0.6.0 on a phone: the settings button opens a sheet with
 * theme, text width and zoom; the width is remembered; zoom enlarges only the
 * document, from the sheet and from a pinch.
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest {
    private val prefs get() = Reader.context.getSharedPreferences("reader", 0)

    @Before
    @After
    fun startFromDefaults() {
        prefs.edit().clear().commit()
    }

    @Test
    fun theSettingsButtonOpensASheetAtTheBottomOfAPhone() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        openSheet(scenario)
        assertEquals("", Reader.js(scenario, SHEET_PROBLEMS))
        assertEquals("Settings", Reader.js(scenario, "document.getElementById('settingsButton').getAttribute('aria-label')"))
    }

    @Test
    fun backClosesTheSheetBeforeLeaving() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        openSheet(scenario)
        val deadline = SystemClock.uptimeMillis() + 5_000
        var open = false
        while (!open && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { open = it.settingsOpenForTest }
            if (!open) SystemClock.sleep(50)
        }
        assertTrue("the app knows the sheet is open", open)
        Focus.ensure()
        Reader.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        Reader.waitFor(scenario, "document.getElementById('settingsPanel').hidden")
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }

    @Test
    fun aThemeChosenInTheSheetIsAppliedAndSaved() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        openSheet(scenario)
        Reader.js(scenario, "document.querySelector('input[name=settings-theme][value=dusk]').click()")
        Reader.waitFor(scenario, "document.documentElement.dataset.theme === 'dusk'")
        waitForSaved("theme", "dusk")
        assertEquals("dusk", Reader.js(scenario, "document.getElementById('themeSwatch').dataset.swatch"))
    }

    @Test
    fun aWidthChosenInTheSheetIsSavedAndUsedOnTheNextStart() {
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("narrow", Reader.js(scenario, "document.documentElement.dataset.width"))
            openSheet(scenario)
            Reader.js(scenario, "document.querySelector('input[name=settings-width][value=wide]').click()")
            Reader.waitFor(scenario, "document.documentElement.dataset.width === 'wide'")
            waitForSaved("width", "wide")
        }
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("wide", Reader.js(scenario, "document.documentElement.dataset.width"))
        }
    }

    @Test
    fun settingsFromTheLastVersionHaveNoWidthAndOpenInNarrow() {
        prefs.edit().putString("theme", "dim").putFloat("zoom", 1.1f).commit()
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("narrow", Reader.js(scenario, "document.documentElement.dataset.width"))
        }
        prefs.edit().putString("width", "huge").commit()
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("narrow", Reader.js(scenario, "document.documentElement.dataset.width"))
        }
    }

    @Test
    fun eachWidthSetsTheColumnOnAWideScreen() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        // 1100 dp, as on a tablet in landscape; the WebView may be wider than this screen.
        scenario.onActivity { activity ->
            val view = activity.webViewForTest!!
            view.layoutParams = view.layoutParams.apply {
                width = (1100 * activity.resources.displayMetrics.density).roundToInt()
            }
        }
        Reader.waitFor(scenario, "document.documentElement.clientWidth >= 1099")
        val widths = mutableMapOf<String, Long>()
        for (name in listOf("wide", "full", "narrow")) {
            // Ctrl+Shift+L steps through the widths, as on the desktop.
            Reader.js(
                scenario,
                "document.dispatchEvent(new KeyboardEvent('keydown', {key: 'L', ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true}))",
            )
            Reader.waitFor(scenario, "document.documentElement.dataset.width === '$name'")
            waitForSaved("width", name)
            widths[name] = (Reader.js(scenario, "Math.round(document.getElementById('article').getBoundingClientRect().width)") as Number).toLong()
        }
        assertTrue("narrow $widths", widths.getValue("narrow") in 700..720)
        assertTrue("wide $widths", widths.getValue("wide") in 940..960)
        assertTrue("full $widths", widths.getValue("full") > 1000)
    }

    @Test
    fun zoomEnlargesTheDocumentButNotTheToolbar() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        val toolbar = "Math.round(document.querySelector('.topbar').getBoundingClientRect().height * 10)"
        val button = "getComputedStyle(document.getElementById('copyButton')).fontSize"
        // The page's height, not the article's box: WebView before 128 reports
        // boxes inside a CSS-zoomed element in unzoomed pixels.
        val article = "document.documentElement.scrollHeight"
        val toolbarBefore = Reader.js(scenario, toolbar)
        val buttonBefore = Reader.js(scenario, button)
        val articleBefore = (Reader.js(scenario, article) as Number).toDouble()
        openSheet(scenario)
        Reader.js(scenario, "document.getElementById('settingsZoomIn').click()")
        Reader.waitFor(scenario, "document.documentElement.dataset.zoom === '110'")
        Reader.js(scenario, "document.getElementById('settingsZoomIn').click()")
        Reader.waitFor(scenario, "document.documentElement.dataset.zoom === '125'")
        assertEquals("125%", Reader.text(scenario, "#settingsZoomValue"))
        assertEquals("toolbar height", toolbarBefore, Reader.js(scenario, toolbar))
        assertEquals("toolbar text", buttonBefore, Reader.js(scenario, button))
        val articleAfter = (Reader.js(scenario, article) as Number).toDouble()
        assertTrue("article $articleBefore to $articleAfter", articleAfter > articleBefore * 1.15)
        assertTextZoomFollowsOnlyTheFontScale(scenario)
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (abs(prefs.getFloat("zoom", 0f) - 1.25f) > 0.001f && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertEquals(1.25f, prefs.getFloat("zoom", 0f), 0.001f)
    }

    @Test
    fun aZoomSavedByTheLastVersionLoadsAtTheSameTextSize() {
        // 0.1.x applied the zoom as WebView text zoom; 0.2.0 zooms the document.
        prefs.edit().putFloat("zoom", 1.5f).commit()
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("150", Reader.js(scenario, "document.documentElement.dataset.zoom"))
            assertTextZoomFollowsOnlyTheFontScale(scenario)
        }
    }

    @Test
    fun aSavedZoomIsSnappedToTheLevelsAndOneOutsideThemGives100() {
        val expected = mapOf(1.3f to "125", 4f to "100", 40f to "100", 0f to "100", Float.NaN to "100")
        for ((saved, shown) in expected) {
            prefs.edit().putFloat("zoom", saved).commit()
            Reader.open("simple.md").use { scenario ->
                Reader.waitForDocument(scenario)
                assertEquals("saved $saved", shown, Reader.js(scenario, "document.documentElement.dataset.zoom"))
            }
        }
    }

    @Test
    fun pinchingZoomsTheDocumentInStepsNotTheWholePage() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        val toolbar = "Math.round(document.querySelector('.topbar').getBoundingClientRect().height * 10)"
        val toolbarBefore = Reader.js(scenario, toolbar)
        Reader.pinch(scenario, 500f, 1000f)
        Reader.waitFor(scenario, "Number(document.documentElement.dataset.zoom) >= 150")
        val zoomedIn = (Reader.js(scenario, "Number(document.documentElement.dataset.zoom)") as Number).toInt()
        // The fingers' midpoint moved up 250 px; the page must not scroll with it.
        val scrolled = (Reader.js(scenario, "window.scrollY") as Number).toDouble()
        assertTrue("the page scrolled $scrolled px during the pinch", scrolled < 60)
        assertEquals("the page itself is not magnified", 1.0, (Reader.js(scenario, "window.visualViewport ? window.visualViewport.scale : 1") as Number).toDouble(), 0.001)
        assertEquals("toolbar height", toolbarBefore, Reader.js(scenario, toolbar))
        scenario.onActivity { assertFalse("WebView zoom is off", it.webViewForTest!!.settings.supportZoom()) }
        Reader.pinch(scenario, 1000f, 500f)
        Reader.waitFor(scenario, "Number(document.documentElement.dataset.zoom) < $zoomedIn")
    }

    @Test
    fun pinchingAnExpandedDiagramZoomsTheDiagram() {
        assumeTrue("Mermaid needs WebView 94 or later", Reader.webViewMajor >= 94)
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario, timeoutMs = 60_000)
            assertEquals(true, Reader.js(scenario, "window.LeanMarkTest.openViewer(0)"))
            val before = (Reader.js(scenario, "window.LeanMarkTest.viewerState().scale") as Number).toDouble()
            Reader.pinch(scenario, 500f, 1000f)
            Reader.waitFor(scenario, "window.LeanMarkTest.viewerState().scale > $before")
            assertEquals("the document zoom stays", "100", Reader.js(scenario, "document.documentElement.dataset.zoom"))
        }
    }

    private fun openSheet(scenario: ActivityScenario<MainActivity>) {
        Reader.js(scenario, "document.getElementById('settingsButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('settingsPanel').hidden")
    }

    private fun assertTextZoomFollowsOnlyTheFontScale(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val expected = (activity.resources.configuration.fontScale * 100).roundToInt()
            assertEquals("WebView text zoom", expected, activity.webViewForTest!!.settings.textZoom)
        }
    }

    private fun waitForSaved(key: String, value: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (prefs.getString(key, null) != value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertEquals(value, prefs.getString(key, null))
    }

    private companion object {
        /** An empty string when the open settings panel is a sheet at the bottom of a phone. */
        const val SHEET_PROBLEMS = """(function () {
            var problems = [];
            var width = document.documentElement.clientWidth;
            var height = window.innerHeight;
            var panel = document.getElementById('settingsPanel');
            var box = panel.getBoundingClientRect();
            if (Math.abs(box.left) > 1 || Math.abs(box.right - width) > 1) {
              problems.push('the sheet spans ' + Math.round(box.left) + ' to ' + Math.round(box.right) + ' of ' + width);
            }
            if (Math.abs(box.bottom - height) > 1) problems.push('the sheet ends at ' + Math.round(box.bottom) + ' of ' + height);
            if (box.top < height * 0.2) problems.push('the sheet starts at ' + Math.round(box.top) + ' and covers the screen');
            ['input[name=settings-theme][value=dusk]', 'input[name=settings-width][value=full]',
             '#settingsZoomOut', '#settingsZoomIn', '#settingsZoomReset', '#settingsClose'].forEach(function (selector) {
              var r = document.querySelector(selector).getBoundingClientRect();
              if (r.width < 1 || r.top < box.top - 1 || r.bottom > height + 1) problems.push(selector + ' is not on the sheet');
            });
            if (getComputedStyle(document.getElementById('shortcutList')).display !== 'none') problems.push('the shortcut list is shown');
            if (parseFloat(getComputedStyle(document.querySelector('.content-grid')).marginRight) !== 0) problems.push('the document moved aside');
            var surface = getComputedStyle(document.documentElement).getPropertyValue('--surface').trim();
            if (!surface) problems.push('no surface colour');
            return problems.join('; ');
          })()"""
    }
}
