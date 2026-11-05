package io.github.abooodbah.leanmark

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.fail

/** Helpers for driving the reader page from instrumented tests. */
object Reader {
    val instrumentation = InstrumentationRegistry.getInstrumentation()!!
    val context = instrumentation.targetContext!!

    fun viewIntent(uri: Uri, type: String = "text/markdown"): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun open(name: String, type: String = "text/markdown"): ActivityScenario<MainActivity> =
        ActivityScenario.launch(viewIntent(FixtureProvider.uri(name), type))

    fun webView(scenario: ActivityScenario<MainActivity>): WebView {
        var view: WebView? = null
        scenario.onActivity { view = it.webViewForTest }
        return view ?: error("no WebView")
    }

    /** Runs [script] in the page and returns its result decoded from JSON. */
    fun js(scenario: ActivityScenario<MainActivity>, script: String): Any? {
        val latch = CountDownLatch(1)
        var result: String? = null
        scenario.onActivity { activity ->
            val view = activity.webViewForTest
            if (view == null) {
                latch.countDown()
            } else {
                view.evaluateJavascript(script) { value ->
                    result = value
                    latch.countDown()
                }
            }
        }
        if (!latch.await(15, TimeUnit.SECONDS)) fail("script timed out: $script")
        // evaluateJavascript gives JSON; JSON null comes back as JSONObject.NULL.
        return result?.let { JSONTokener(it).nextValue() }?.takeIf { it != JSONObject.NULL }
    }

    fun waitFor(
        scenario: ActivityScenario<MainActivity>,
        condition: String,
        timeoutMs: Long = 30_000,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (js(scenario, "Boolean($condition)") == true) return
            SystemClock.sleep(100)
        }
        fail("timed out waiting for $condition; state: " + js(scenario, STATE))
    }

    fun waitForDocument(scenario: ActivityScenario<MainActivity>, timeoutMs: Long = 30_000) =
        waitFor(scenario, "$RENDERED && !document.getElementById('readerPane').hidden", timeoutMs)

    fun waitForError(scenario: ActivityScenario<MainActivity>) =
        waitFor(scenario, "$RENDERED && !document.getElementById('statePane').hidden")

    fun text(scenario: ActivityScenario<MainActivity>, selector: String): String? =
        js(scenario, "(function(){var n=document.querySelector(${Json.quote(selector)});return n?n.textContent:null})()")
            as? String

    /** A finger swipe up across the middle of the WebView, as a reader scrolls. */
    fun swipeUp(scenario: ActivityScenario<MainActivity>) {
        val view = webView(scenario)
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        val target = activity!!
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        val x = location[0] + view.width / 2f
        val startY = location[1] + view.height * 0.8f
        val endY = location[1] + view.height * 0.2f
        val down = SystemClock.uptimeMillis()
        Focus.touch(target, MotionEvent.ACTION_DOWN, down, down, x, startY)
        val steps = 12
        for (step in 1..steps) {
            val y = startY + (endY - startY) * step / steps
            val time = down + step * 10L
            Focus.touch(target, MotionEvent.ACTION_MOVE, down, time, x, y)
        }
        val up = down + (steps + 1) * 10L
        Focus.touch(target, MotionEvent.ACTION_UP, down, up, x, endY)
    }

    /**
     * Two fingers in a vertical line on the middle of the WebView, [fromSpan]
     * pixels apart, the lower one held and the upper one moved until they are
     * [toSpan] apart: spreading them zooms in, pinching zooms out. The point
     * between the fingers moves too, as in a real pinch, so a WebView that got
     * these touches would also scroll.
     */
    fun pinch(scenario: ActivityScenario<MainActivity>, fromSpan: Float, toSpan: Float) {
        val view = webView(scenario)
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        val target = activity!!
        val location = IntArray(2)
        val origin = IntArray(2)
        instrumentation.runOnMainSync {
            view.getLocationOnScreen(location)
            target.window.decorView.getLocationOnScreen(origin)
        }
        val x = location[0] - origin[0] + view.width / 2f
        val y = location[1] - origin[1] + view.height / 2f
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, time: Long, span: Float, pointers: Int) {
            val properties = Array(pointers) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coordinates = Array(pointers) { index ->
                MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y + fromSpan / 2 - if (index == 0) 0f else span
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                down, time, action, pointers, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            instrumentation.runOnMainSync { target.dispatchTouchEvent(event) }
            event.recycle()
        }
        val second = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        send(MotionEvent.ACTION_DOWN, down, fromSpan, 1)
        send(MotionEvent.ACTION_POINTER_DOWN or second, down + 10, fromSpan, 2)
        val steps = 20
        for (step in 1..steps) {
            send(MotionEvent.ACTION_MOVE, down + 10 + step * 16L, fromSpan + (toSpan - fromSpan) * step / steps, 2)
        }
        val end = down + 10 + (steps + 1) * 16L
        send(MotionEvent.ACTION_POINTER_UP or second, end, toSpan, 2)
        send(MotionEvent.ACTION_UP, end + 10, toSpan, 1)
        instrumentation.waitForIdleSync()
    }

    /** Bytes the fixture provider delivered for its last stream of [name]. */
    fun written(name: String): Long {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val bytes = context.contentResolver
                .call(FixtureProvider.uri(name), "written", name, null)?.getLong("bytes") ?: -1
            if (bytes >= 0) return bytes
            SystemClock.sleep(100)
        }
        fail("the fixture stream for $name did not finish")
        return -1
    }

    /** The WebView major version, or 0 when there is none. */
    val webViewMajor: Int
        get() = WebViewSupport.majorVersion(WebView.getCurrentWebViewPackage()?.versionName) ?: 0

    private const val RENDERED = "document.documentElement.dataset.renderState === 'ready'"
    private const val STATE =
        "JSON.stringify({render: document.documentElement.dataset.renderState," +
            " title: (document.getElementById('stateTitle')||{}).textContent," +
            " message: (document.getElementById('stateMessage')||{}).textContent," +
            " readerHidden: (document.getElementById('readerPane')||{}).hidden})"
}
