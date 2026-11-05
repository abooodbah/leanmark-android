package io.github.abooodbah.leanmark

import android.content.pm.ActivityInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * The current section in "On this page", from desktop LeanMark 0.7.0: the
 * entry of the last heading at or above the reading line carries
 * aria-current="location" and stays in view, beside the document on a wide
 * screen and in the sheet on a phone. long-outline.md is the desktop's own
 * fixture (tests/fixtures), with 60 entries.
 */
@RunWith(AndroidJUnit4::class)
class HighlightTest {
    @Before
    @After
    fun startFromDefaults() {
        Reader.context.getSharedPreferences("reader", 0).edit().clear().commit()
    }

    @Test
    fun theFirstEntryIsCurrentAtTheTop() = open { scenario ->
        Reader.waitFor(scenario, "$CURRENT === 0")
    }

    @Test
    fun scrollingMovesTheHighlightToTheSectionAtTheReadingLine() = open { scenario ->
        for (fraction in listOf(0.2, 0.5, 0.8, 0.35)) {
            Reader.js(scenario, "window.scrollTo(0, (document.documentElement.scrollHeight - innerHeight) * $fraction)")
            waitForTheRightEntry(scenario)
        }
        assertTrue((Reader.js(scenario, CURRENT) as Number).toInt() > 5)
    }

    @Test
    fun theSheetShowsTheCurrentEntryAndKeepsItInView() = open { scenario ->
        Reader.js(scenario, "window.scrollTo(0, (document.documentElement.scrollHeight - innerHeight) * 0.7)")
        waitForTheRightEntry(scenario)
        Reader.js(scenario, "document.getElementById('outlineButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('outlineSheet').hidden")
        Reader.waitFor(scenario, "$SHEET_CURRENT === $CURRENT && $SHEET_CURRENT_IN_VIEW")
        // The sheet follows while it is open.
        Reader.js(scenario, "window.scrollTo(0, (document.documentElement.scrollHeight - innerHeight) * 0.3)")
        waitForTheRightEntry(scenario)
        Reader.waitFor(scenario, "$SHEET_CURRENT === $CURRENT && $SHEET_CURRENT_IN_VIEW")
    }

    @Test
    fun anEntryTappedInTheSheetBecomesCurrent() = open { scenario ->
        Reader.js(scenario, "document.getElementById('outlineButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('outlineSheet').hidden")
        // Near the end, where the heading cannot reach the reading line.
        Reader.js(scenario, "var links = document.querySelectorAll('#outlineSheet a'); links[links.length - 1].click()")
        Reader.waitFor(scenario, "document.getElementById('outlineSheet').hidden")
        Reader.waitFor(scenario, "$CURRENT === document.querySelectorAll('#outlineList a').length - 1")
    }

    @Test
    fun aLayoutChangeAfterALongJumpMovesTheHighlight() = open { scenario ->
        // A long jump, then content above grows without a scroll event, as
        // when an image or a diagram finishes late.
        Reader.js(scenario, "window.scrollTo(0, (document.documentElement.scrollHeight - innerHeight) * 0.6)")
        waitForTheRightEntry(scenario)
        val before = (Reader.js(scenario, CURRENT) as Number).toInt()
        Reader.js(
            scenario,
            "document.scrollingElement.style.overflowAnchor = 'none';" +
                "document.querySelector('#article p').style.paddingTop = (innerHeight * 3) + 'px'",
        )
        waitForTheRightEntry(scenario)
        assertTrue("moved from $before", (Reader.js(scenario, CURRENT) as Number).toInt() < before)
    }

    @Test
    fun theHighlightFollowsARotation() = open { scenario ->
        Reader.js(scenario, "window.scrollTo(0, (document.documentElement.scrollHeight - innerHeight) * 0.5)")
        waitForTheRightEntry(scenario)
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Reader.waitFor(scenario, "window.innerWidth > window.innerHeight")
        waitForTheRightEntry(scenario)
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        Reader.waitFor(scenario, "window.innerWidth < window.innerHeight")
        waitForTheRightEntry(scenario)
    }

    @Test
    fun theHighlightFollowsAZoomStep() = open { scenario ->
        Reader.js(scenario, "window.scrollTo(0, (document.documentElement.scrollHeight - innerHeight) * 0.5)")
        waitForTheRightEntry(scenario)
        for (zoom in listOf("150", "75")) {
            Reader.js(scenario, "window.LeanMarkHost.receive({type: 'zoom', percent: $zoom})")
            Reader.waitFor(scenario, "document.documentElement.dataset.zoom === '$zoom'")
            waitForTheRightEntry(scenario)
        }
    }

    @Test
    fun aWideScreenKeepsTheCurrentEntryInViewBesideTheDocument() = open { scenario ->
        scenario.onActivity { activity ->
            val view = activity.webViewForTest!!
            view.layoutParams = view.layoutParams.apply {
                width = (1300 * activity.resources.displayMetrics.density).roundToInt()
            }
        }
        Reader.waitFor(scenario, "document.documentElement.dataset.layout === 'wide'")
        Reader.js(scenario, "window.scrollTo(0, document.documentElement.scrollHeight)")
        waitForTheRightEntry(scenario)
        Reader.waitFor(
            scenario,
            "(function () { var o = document.getElementById('outline').getBoundingClientRect();" +
                " var a = document.querySelector('#outlineList a[aria-current]').getBoundingClientRect();" +
                " return a.top >= o.top - 1 && a.bottom <= o.bottom + 1; })()",
        )
    }

    @Test
    fun theHighlightLooksTheSameInEveryTheme() = open { scenario ->
        Reader.js(scenario, "window.scrollTo(0, (document.documentElement.scrollHeight - innerHeight) * 0.5)")
        waitForTheRightEntry(scenario)
        Reader.js(scenario, "document.getElementById('outlineButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('outlineSheet').hidden")
        for (name in listOf("light", "dark", "dim", "dusk", "system")) {
            Reader.js(scenario, "window.LeanMarkTest.applyTheme('$name')")
            val style = "getComputedStyle(document.querySelector('#outlineSheet a[aria-current=\"location\"]'))"
            val other = "getComputedStyle(document.querySelector('#outlineSheet a:not([aria-current])'))"
            val token = { tokenName: String -> Reader.js(scenario, "getComputedStyle(document.documentElement).getPropertyValue('$tokenName').trim()") as String }
            val background = Reader.js(scenario, "$style.backgroundColor") as String
            val text = Reader.js(scenario, "$style.color") as String
            val bar = Reader.js(scenario, "$style.boxShadow") as String
            assertEquals("$name background", ThemeTest.color(token("--surface-muted")), ThemeTest.color(background))
            assertEquals("$name text", ThemeTest.color(token("--text-primary")), ThemeTest.color(text))
            assertTrue("$name bar: $bar", bar.contains("inset") && ThemeTest.color(bar) == ThemeTest.color(token("--link")))
            assertEquals("$name weight", Reader.js(scenario, "$other.fontWeight"), Reader.js(scenario, "$style.fontWeight"))
            assertTrue("$name text contrast", ThemeTest.contrast(text, background) >= 4.5)
            assertTrue("$name bar contrast", ThemeTest.contrast(bar, background) >= 3.0)
        }
    }

    private fun open(body: (ActivityScenario<MainActivity>) -> Unit) =
        Reader.open("long-outline.md").use { scenario ->
            Reader.waitForDocument(scenario)
            body(scenario)
        }

    private fun waitForTheRightEntry(scenario: ActivityScenario<MainActivity>) =
        Reader.waitFor(scenario, "$CURRENT === $EXPECTED")

    private companion object {
        /** The index of the entry carrying aria-current, or -1. */
        const val CURRENT =
            "Array.prototype.indexOf.call(document.querySelectorAll('#outlineList a')," +
                " document.querySelector('#outlineList a[aria-current=\"location\"]'))"

        const val SHEET_CURRENT =
            "Array.prototype.indexOf.call(document.querySelectorAll('#outlineSheet a')," +
                " document.querySelector('#outlineSheet a[aria-current=\"location\"]'))"

        const val SHEET_CURRENT_IN_VIEW =
            "(function () { var s = document.getElementById('outlineSheet').getBoundingClientRect();" +
                " var a = document.querySelector('#outlineSheet a[aria-current]').getBoundingClientRect();" +
                " return a.top >= s.top - 1 && a.bottom <= s.bottom + 1; })()"

        /**
         * The desktop's rule: the last heading at or above the reading line,
         * a quarter of the way down below the toolbar and at least 48 px, or
         * the first entry at the top of the page.
         */
        const val EXPECTED = """(function () {
            if (window.scrollY <= 1) return 0;
            var toolbar = document.querySelector('.chrome').getBoundingClientRect().bottom;
            var line = toolbar + Math.max(48, (window.innerHeight - toolbar) * 0.25);
            var found = 0;
            document.querySelectorAll('#outlineList a').forEach(function (link, index) {
              var heading = document.getElementById(decodeURIComponent(link.getAttribute('href').slice(1)));
              if (heading.getBoundingClientRect().top <= line) found = index;
            });
            return found;
          })()"""
    }
}
