package io.github.abooodbah.leanmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
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
 * The desktop shows "On this page" beside the document only from 1180 px
 * wide, so a phone shows none. On a phone a button opens the same entries as
 * a sheet at the bottom of the screen.
 */
@RunWith(AndroidJUnit4::class)
class OutlineSheetTest {
    @Before
    @After
    fun startFromDefaults() {
        Reader.context.getSharedPreferences("reader", 0).edit().clear().commit()
    }

    @Test
    fun theButtonOpensTheOutlineAsASheetOnAPhone() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        assertEquals("", Reader.js(scenario, BUTTON_PROBLEMS))
        openSheet(scenario)
        assertEquals("", Reader.js(scenario, SHEET_PROBLEMS))
        assertEquals(
            Reader.js(scenario, "Array.from(document.querySelectorAll('#outlineList a')).map(function (a) { return a.textContent; }).join('|')"),
            Reader.js(scenario, "Array.from(document.querySelectorAll('#outlineSheet a')).map(function (a) { return a.textContent; }).join('|')"),
        )
    }

    @Test
    fun tappingAnEntryGoesToItsSectionAndClosesTheSheet() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        openSheet(scenario)
        // The third entry, "Code is content, not executable markup".
        Reader.js(scenario, "document.querySelectorAll('#outlineSheet a')[2].click()")
        Reader.waitFor(scenario, "document.getElementById('outlineSheet').hidden")
        Reader.waitFor(
            scenario,
            "(function () { var h = document.getElementById('code-is-content-not-executable-markup'); var top = h.getBoundingClientRect().top;" +
                " return top >= 0 && top < window.innerHeight / 2; })()",
        )
        assertTrue((Reader.js(scenario, "window.scrollY") as Number).toDouble() > 100)
    }

    @Test
    fun backClosesTheSheetBeforeLeaving() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        openSheet(scenario)
        val deadline = SystemClock.uptimeMillis() + 5_000
        var open = false
        while (!open && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { open = it.outlineOpenForTest }
            if (!open) SystemClock.sleep(50)
        }
        assertTrue("the app knows the sheet is open", open)
        Focus.ensure()
        Reader.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        Reader.waitFor(scenario, "document.getElementById('outlineSheet').hidden")
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }

    @Test
    fun onlyOneSheetIsOpenAtATime() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        openSheet(scenario)
        Reader.js(scenario, "document.getElementById('settingsButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('settingsPanel').hidden && document.getElementById('outlineSheet').hidden")
        Reader.js(scenario, "document.getElementById('outlineButton').click()")
        Reader.waitFor(scenario, "document.getElementById('settingsPanel').hidden && !document.getElementById('outlineSheet').hidden")
    }

    @Test
    fun aDocumentWithoutAnOutlineHasNoButton() = Reader.open("simple.md").use { scenario ->
        Reader.waitForDocument(scenario)
        assertEquals(true, Reader.js(scenario, "document.getElementById('outlineButton').getBoundingClientRect().width === 0"))
    }

    @Test
    fun aWideScreenShowsTheOutlineBesideTheDocumentInstead() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        scenario.onActivity { activity ->
            val view = activity.webViewForTest!!
            view.layoutParams = view.layoutParams.apply {
                width = (1300 * activity.resources.displayMetrics.density).roundToInt()
            }
        }
        Reader.waitFor(scenario, "document.documentElement.dataset.layout === 'wide'")
        assertEquals(true, Reader.js(scenario, "document.getElementById('outline').getBoundingClientRect().width > 100"))
        assertEquals(true, Reader.js(scenario, "document.getElementById('outlineButton').getBoundingClientRect().width === 0"))
    }

    private fun openSheet(scenario: ActivityScenario<MainActivity>) {
        Reader.js(scenario, "document.getElementById('outlineButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('outlineSheet').hidden")
    }

    private companion object {
        /** An empty string when the outline button is on screen in the bottom corner, clear of the toolbar. */
        const val BUTTON_PROBLEMS = """(function () {
            var problems = [];
            var button = document.getElementById('outlineButton');
            var box = button.getBoundingClientRect();
            var width = document.documentElement.clientWidth;
            if (box.width < 40 || box.height < 40) problems.push('the button is ' + Math.round(box.width) + ' by ' + Math.round(box.height));
            if (box.right > width || box.bottom > window.innerHeight || box.top < window.innerHeight / 2) problems.push('the button is not in the bottom corner');
            if (button.getAttribute('aria-label') !== 'On this page') problems.push('the button is not named');
            return problems.join('; ');
          })()"""

        /** An empty string when the open outline is a sheet at the bottom of a phone. */
        const val SHEET_PROBLEMS = """(function () {
            var problems = [];
            var width = document.documentElement.clientWidth;
            var height = window.innerHeight;
            var box = document.getElementById('outlineSheet').getBoundingClientRect();
            if (Math.abs(box.left) > 1 || Math.abs(box.right - width) > 1) problems.push('the sheet spans ' + Math.round(box.left) + ' to ' + Math.round(box.right));
            if (Math.abs(box.bottom - height) > 1) problems.push('the sheet ends at ' + Math.round(box.bottom) + ' of ' + height);
            if (box.top < height * 0.3) problems.push('the sheet starts at ' + Math.round(box.top) + ' and covers the screen');
            var link = document.querySelector('#outlineSheet a').getBoundingClientRect();
            if (link.height < 36) problems.push('an entry is only ' + Math.round(link.height) + ' px tall to tap');
            return problems.join('; ');
          })()"""
    }
}
