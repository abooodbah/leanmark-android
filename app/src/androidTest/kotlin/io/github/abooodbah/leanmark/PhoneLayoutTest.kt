package io.github.abooodbah.leanmark

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * The reader page's toolbar must fit a phone: every control fully on screen,
 * in one row, with nothing to scroll sideways. The desktop CSS hides some
 * controls below 760 px but still let the row grow past a phone's width, so
 * the Open button was cut at the right edge.
 */
@RunWith(AndroidJUnit4::class)
class PhoneLayoutTest {

    @Test
    fun theToolbarFitsThisPhone() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        assertEquals("", Reader.js(scenario, TOOLBAR_PROBLEMS))
    }

    @Test
    fun theToolbarFitsASmallPhone() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        narrow(scenario, 320)
        assertEquals("", Reader.js(scenario, TOOLBAR_PROBLEMS))
    }

    @Test
    fun theToolbarFitsInLandscape() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        narrow(scenario, 640)
        assertEquals("", Reader.js(scenario, TOOLBAR_PROBLEMS))
    }

    /** Makes the WebView [dp] wide, as on a smaller screen, and waits for the page to follow. */
    private fun narrow(scenario: ActivityScenario<MainActivity>, dp: Int) {
        scenario.onActivity { activity ->
            val view = activity.webViewForTest!!
            view.layoutParams = view.layoutParams.apply {
                width = (dp * activity.resources.displayMetrics.density).roundToInt()
            }
        }
        Reader.waitFor(scenario, "document.documentElement.clientWidth <= $dp")
    }

    private companion object {
        /** An empty string when the toolbar fits, otherwise what is wrong. */
        const val TOOLBAR_PROBLEMS = """(function () {
            var width = document.documentElement.clientWidth;
            var problems = [];
            var tops = [];
            ['findInput', 'copyButton', 'themeButton', 'openButton'].forEach(function (id) {
              var element = document.getElementById(id);
              var box = element.getBoundingClientRect();
              if (getComputedStyle(element).display === 'none' || box.width < 1) {
                problems.push(id + ' is hidden');
                return;
              }
              if (box.left < -0.5 || box.right > width + 0.5) {
                problems.push(id + ' spans ' + Math.round(box.left) + ' to ' + Math.round(box.right) + ' of ' + width);
              }
              tops.push(box.top);
            });
            var find = document.getElementById('findInput').getBoundingClientRect();
            if (find.width < 96) problems.push('the find field is only ' + Math.round(find.width) + ' px wide');
            if (tops.length && Math.max.apply(null, tops) - Math.min.apply(null, tops) > 4) {
              problems.push('the controls wrap to more than one row');
            }
            var bar = document.querySelector('.topbar');
            if (bar.scrollWidth > bar.clientWidth + 0.5) problems.push('the top bar scrolls sideways');
            if (document.documentElement.scrollWidth > width + 0.5) problems.push('the page scrolls sideways');
            return problems.join('; ');
          })()"""
    }
}
