package io.github.abooodbah.leanmark

import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The reading column on wide screens, from desktop LeanMark 0.7.1. From
 * 1180 px wide the page uses side columns for "On this page"; a document
 * with fewer than two second- or third-level headings has no outline, and
 * its text went into the 220 px outline column. simple.md has one such
 * heading, so no outline; showcase.md has eight.
 *
 * CI also runs this class on a tablet emulator (pixel_tablet, 1280 by 800
 * dp), where landscape is wider than 1180 px without help.
 */
@RunWith(AndroidJUnit4::class)
class WideLayoutTest {
    @Before
    @After
    fun startFromDefaults() {
        Reader.context.getSharedPreferences("reader", 0).edit().clear().commit()
    }

    @Test
    fun aDocumentWithoutAnOutlineGetsEachTextWidthOnAWidePage() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        widen(scenario, 1300)
        Reader.waitFor(scenario, "document.documentElement.dataset.layout === 'wide'")
        assertEquals(true, Reader.js(scenario, "document.getElementById('outline').hidden"))
        val expected = mapOf("narrow" to 720..720, "wide" to 960..960, "full" to 1150..1300)
        for (name in listOf("wide", "full", "narrow")) {
            stepWidth(scenario, name)
            val width = articleWidth(scenario)
            assertTrue("$name column is $width px", width in expected.getValue(name))
            assertCentred(scenario, name)
        }
    }

    @Test
    fun aDocumentWithAnOutlineKeepsItsSideColumns() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        widen(scenario, 1300)
        Reader.waitFor(scenario, "document.documentElement.dataset.layout === 'wide'")
        Reader.waitFor(scenario, "getComputedStyle(document.getElementById('outline')).display === 'block'")
        assertEquals(720, articleWidth(scenario))
        val outline = (Reader.js(scenario, "Math.round(document.getElementById('outline').getBoundingClientRect().right)") as Number).toInt()
        val article = (Reader.js(scenario, "Math.round(document.getElementById('article').getBoundingClientRect().left)") as Number).toInt()
        assertTrue("the outline ($outline) is left of the text ($article)", outline <= article)
        assertCentred(scenario, "narrow with outline")
    }

    @Test
    fun aDocumentWithoutAnOutlineAfterOneWithAnOutlineGetsTheFullColumn() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        widen(scenario, 1300)
        Reader.waitFor(scenario, "getComputedStyle(document.getElementById('outline')).display === 'block'")
        // The next file arrives in the same activity and page, as from the
        // file manager while LeanMark is open.
        var launched: Intent? = null
        scenario.onActivity { activity ->
            launched = activity.intent
            Reader.instrumentation.callActivityOnNewIntent(activity, Reader.viewIntent(FixtureProvider.uri("simple.md")))
        }
        Reader.waitFor(scenario, "document.getElementById('fileName').textContent === 'simple.md'")
        Reader.waitForDocument(scenario)
        Reader.waitFor(scenario, "document.getElementById('outline').hidden")
        assertEquals(720, articleWidth(scenario))
        assertCentred(scenario, "narrow after a document with an outline")
        // ActivityScenario finds its activity by the intent it started it with.
        scenario.onActivity { it.intent = launched }
    }

    @Test
    fun inLandscapeADocumentWithoutAnOutlineFillsItsColumn() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Reader.waitFor(scenario, "window.innerWidth > window.innerHeight")
        checkTheColumnOnThisScreen(scenario, "landscape")
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        Reader.waitFor(scenario, "window.innerWidth < window.innerHeight")
        checkTheColumnOnThisScreen(scenario, "portrait")
    }

    /**
     * The column is the Narrow 720 px, or the page less its padding where
     * the page is narrower, and centred. Logged so CI shows the page width
     * each emulator gave.
     */
    private fun checkTheColumnOnThisScreen(scenario: ActivityScenario<MainActivity>, what: String) {
        // Wait for the layout to settle after the rotation.
        Reader.waitFor(scenario, "Math.abs(document.getElementById('article').getBoundingClientRect().width - $ARTICLE_LIMIT) < 1")
        val page = (Reader.js(scenario, "document.documentElement.clientWidth") as Number).toInt()
        val layout = Reader.js(scenario, "document.documentElement.dataset.layout || 'none'")
        val width = articleWidth(scenario)
        Log.i("WideLayoutTest", "$what: page $page px, layout $layout, column $width px")
        val padding = (Reader.js(scenario, PADDING) as Number).toInt()
        assertEquals("$what column on a $page px page ($layout)", min(720, page - padding), width)
        assertCentred(scenario, what)
    }

    private fun widen(scenario: ActivityScenario<MainActivity>, dp: Int) {
        // As on a tablet in landscape; the WebView may be wider than this screen.
        scenario.onActivity { activity ->
            val view = activity.webViewForTest!!
            view.layoutParams = view.layoutParams.apply {
                width = (dp * activity.resources.displayMetrics.density).roundToInt()
            }
        }
        Reader.waitFor(scenario, "document.documentElement.clientWidth >= ${dp - 1}")
    }

    private fun stepWidth(scenario: ActivityScenario<MainActivity>, name: String) {
        // Ctrl+Shift+L steps through the widths, as on the desktop.
        Reader.js(
            scenario,
            "document.dispatchEvent(new KeyboardEvent('keydown', {key: 'L', ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true}))",
        )
        Reader.waitFor(scenario, "document.documentElement.dataset.width === '$name'")
    }

    private fun articleWidth(scenario: ActivityScenario<MainActivity>): Int =
        (Reader.js(scenario, "Math.round(document.getElementById('article').getBoundingClientRect().width)") as Number).toInt()

    private fun assertCentred(scenario: ActivityScenario<MainActivity>, what: String) {
        val offset = (Reader.js(scenario, CENTRE_OFFSET) as Number).toDouble()
        assertTrue("$what column is ${"%.1f".format(offset)} px off centre", abs(offset) <= 2)
    }

    private companion object {
        /** The centre of the text less the centre of the page. */
        const val CENTRE_OFFSET = """(function () {
            var box = document.getElementById('article').getBoundingClientRect();
            return box.left + box.width / 2 - document.documentElement.clientWidth / 2;
          })()"""

        /** The grid's left and right padding, which the column cannot use. */
        const val PADDING = """(function () {
            var style = getComputedStyle(document.querySelector('.content-grid'));
            return Math.round(parseFloat(style.paddingLeft) + parseFloat(style.paddingRight));
          })()"""

        /** The width the column should have on the current page. */
        const val ARTICLE_LIMIT = "Math.min(720, document.documentElement.clientWidth - $PADDING)"
    }
}
