package io.github.abooodbah.leanmark

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderTest {

    private fun fixture(name: String): String =
        Reader.instrumentation.context.assets.open("fixtures/$name").use { String(it.readBytes(), Charsets.UTF_8) }

    @Test
    fun opensAContentUriFromAViewIntent() {
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("A simple Markdown note", Reader.text(scenario, "#article h1")?.trim())
            assertEquals("simple.md", Reader.text(scenario, "#fileName"))
            assertEquals(3L, Reader.js(scenario, "document.querySelectorAll('#article input[type=checkbox]').length").asLong())
        }
    }

    @Test
    fun rendersTheShowcaseLikeTheDesktop() {
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("LeanMark renderer showcase", Reader.text(scenario, "#article h1")?.trim())
            assertEquals(1L, Reader.js(scenario, "document.querySelectorAll('#article table').length").asLong())
            assertTrue(Reader.text(scenario, "#article").orEmpty().contains("日本語, العربية, and 🚀"))
            assertTrue(Reader.js(scenario, "document.querySelectorAll('#article pre code').length").asLong() >= 2)
            // Without access to the file's folder, a relative image shows its alt text.
            assertTrue(Reader.text(scenario, ".blocked-image").orEmpty().startsWith("Image unavailable"))
        }
    }

    @Test
    fun drawsMermaidDiagramsOnTheDevice() {
        assumeTrue("Mermaid needs WebView 94 or later", Reader.webViewMajor >= 94)
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario, timeoutMs = 60_000)
            assertEquals(2L, Reader.js(scenario, "document.querySelectorAll('#article .diagram svg').length").asLong())
        }
    }

    @Test
    fun hostileMarkdownRunsNothingAndLoadsNothing() {
        Reader.open("security.md").use { scenario ->
            Reader.waitForDocument(scenario, timeoutMs = 60_000)
            assertEquals(null, Reader.js(scenario, "window.__leanmarkXss === undefined ? null : window.__leanmarkXss"))
            assertEquals(0L, Reader.js(scenario, "document.querySelectorAll('#article script, #article iframe, #article object, #article embed, #article form').length").asLong())
            val blockedImages = Reader.js(scenario, "Array.from(document.querySelectorAll('.blocked-image')).map(n => n.textContent).join('|')") as String
            assertTrue(blockedImages, blockedImages.contains("Remote image blocked"))
            assertTrue(Reader.text(scenario, "#article").orEmpty().contains("The final sentinel proves the rest of the document still rendered."))
        }
    }

    @Test
    fun thePageCannotReachTheNetwork() {
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            Reader.js(scenario, "window.__fetch = 'pending'; fetch('https://example.com/').then(() => { window.__fetch = 'loaded' }, () => { window.__fetch = 'refused' })")
            Reader.waitFor(scenario, "window.__fetch !== 'pending'")
            assertEquals("refused", Reader.js(scenario, "window.__fetch"))
        }
    }

    @Test
    fun theAppRefusesRemotePagesEvenWithoutThePagePolicy() {
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            scenario.onActivity { it.webViewForTest!!.loadUrl("https://example.com/") }
            val deadline = SystemClock.uptimeMillis() + 10_000
            var blocked = false
            while (!blocked && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { blocked = "https://example.com/" in it.blockedRequests }
                if (!blocked) SystemClock.sleep(100)
            }
            assertTrue("the request was answered by the app, not the network", blocked)
            assertFalse(Reader.js(scenario, "document.title").toString().contains("Example Domain"))
        }
    }

    @Test
    fun aFileInALegacyEncodingShowsAClearError() {
        Reader.open("legacy-encoding.md").use { scenario ->
            Reader.waitForError(scenario)
            assertEquals("This document could not be opened.", Reader.text(scenario, "#stateTitle"))
            assertTrue(Reader.text(scenario, "#stateMessage").orEmpty().contains("not valid UTF-8"))
            assertEquals("legacy-encoding.md", Reader.text(scenario, "#fileName"))
        }
    }

    @Test
    fun aFileOverTheLimitIsRefusedByItsSize() {
        Reader.open("huge.md").use { scenario ->
            Reader.waitForError(scenario)
            assertTrue(Reader.text(scenario, "#stateMessage").orEmpty().contains("32 MB safety limit"))
        }
    }

    @Test
    fun aStreamOverTheLimitIsRefusedWhileReading() {
        Reader.open("huge.md-nosize").use { scenario ->
            Reader.waitForError(scenario)
            assertTrue(Reader.text(scenario, "#stateMessage").orEmpty().contains("32 MB safety limit"))
        }
        // The app stopped reading near the limit instead of taking the whole
        // stream; the core alone would give the same message after reading it all.
        val written = Reader.written("huge.md-nosize")
        assertTrue("delivered $written bytes", written in 1 until 33L * 1024 * 1024)
    }

    @Test
    fun aLargeDocumentRendersAndScrollsByTouch() {
        Reader.open("large.md").use { scenario ->
            Reader.waitForDocument(scenario, timeoutMs = 90_000)
            val headings = Reader.js(scenario, "document.querySelectorAll('#article h1, #article h2, #article h3').length").asLong()
            assertTrue("$headings headings", headings > 500)
            assertEquals(0L, Reader.js(scenario, "Math.round(window.scrollY)").asLong())
            Reader.swipeUp(scenario)
            Reader.waitFor(scenario, "window.scrollY > 100")
            Reader.swipeUp(scenario)
            Reader.waitFor(scenario, "Number(document.getElementById('readingProgress').getAttribute('aria-valuenow')) >= 0")
        }
    }

    @Test
    fun rotatingKeepsTheDocumentAndThePlace() {
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            Reader.js(scenario, "window.scrollTo(0, 600)")
            Reader.waitFor(scenario, "window.scrollY >= 500")
            var before: Activity? = null
            scenario.onActivity {
                before = it
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Reader.waitFor(scenario, "window.innerWidth > window.innerHeight")
            scenario.onActivity { assertTrue("the activity was kept", it === before) }
            assertEquals("LeanMark renderer showcase", Reader.text(scenario, "#article h1")?.trim())
            assertTrue(Reader.js(scenario, "window.scrollY").asLong() > 0)
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            Reader.waitFor(scenario, "window.innerWidth < window.innerHeight")
            assertEquals("LeanMark renderer showcase", Reader.text(scenario, "#article h1")?.trim())
        }
    }

    @Test
    fun findCountsMatchesInTheDocument() {
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            Reader.js(scenario, "var f=document.getElementById('findInput'); f.value='bullet'; f.dispatchEvent(new Event('input'))")
            Reader.waitFor(scenario, "document.getElementById('findCount').textContent === '1/2'")
        }
    }

    @Test
    fun copyingASectionPutsItsExactMarkdownOnTheClipboard() {
        val source = fixture("showcase.md")
        val start = source.indexOf("## Lists and tasks")
        val end = source.indexOf("## A compact table")
        val expected = source.substring(start, end).trimEnd()
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            // Heading 0 is the title, heading 1 is "Lists and tasks".
            Reader.js(scenario, "document.querySelectorAll('.copy-section')[1].click()")
            assertEquals(expected, waitForClip(scenario, expected))
        }
    }

    @Test
    fun copyingTheDocumentCopiesTheWholeFile() {
        val expected = fixture("simple.md").trimEnd()
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            Reader.js(scenario, "document.getElementById('copyButton').click()")
            assertEquals(expected, waitForClip(scenario, expected))
        }
    }

    private fun waitForClip(scenario: ActivityScenario<MainActivity>, expected: String): String? {
        var clip: String? = null
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity {
                clip = it.getSystemService(ClipboardManager::class.java)
                    .primaryClip?.getItemAt(0)?.text?.toString()
            }
            if (clip == expected) break
            SystemClock.sleep(100)
        }
        return clip
    }

    @Test
    fun sharedFileStreamsOpen() {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/markdown")
            .putExtra(Intent.EXTRA_STREAM, FixtureProvider.uri("simple.md"))
            .setClass(Reader.context, MainActivity::class.java)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<MainActivity>(share).use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("A simple Markdown note", Reader.text(scenario, "#article h1")?.trim())
        }
    }

    @Test
    fun sharedTextOpens() {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/markdown")
            .putExtra(Intent.EXTRA_TEXT, "# Shared note\n\n- one\n- two\n")
            .putExtra(Intent.EXTRA_SUBJECT, "note.md")
            .setClass(Reader.context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(share).use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("Shared note", Reader.text(scenario, "#article h1")?.trim())
            assertEquals("note.md", Reader.text(scenario, "#fileName"))
        }
    }

    @Test
    fun textPlainFilesOpenAsMarkdown() {
        Reader.open("notes.txt", "text/plain").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("A simple Markdown note", Reader.text(scenario, "#article h1")?.trim())
        }
    }

    @Test
    fun filePathsAreRefusedEvenWhenTheAppCouldReadThem() {
        // A file:// URI would be read with this app's own access, not the
        // sender's. Its private files are the case that matters.
        val private = java.io.File(Reader.context.filesDir, "private.md")
        private.writeText("# Private note\n")
        try {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.fromFile(private), "text/markdown")
                .setClass(Reader.context, MainActivity::class.java)
            ActivityScenario.launch<MainActivity>(view).use { scenario ->
                Reader.waitForError(scenario)
                assertTrue(Reader.text(scenario, "#stateMessage").orEmpty().contains("could not read this file"))
                assertEquals(null, Reader.text(scenario, "#article h1"))
            }
        } finally {
            private.delete()
        }
    }

    @Test
    fun thePageCannotNavigateAway() {
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            Reader.js(scenario, "location.href = 'https://example.com/'")
            SystemClock.sleep(2_000)
            assertEquals("A simple Markdown note", Reader.text(scenario, "#article h1")?.trim())
            scenario.onActivity {
                assertFalse("the navigation was never requested", "https://example.com/" in it.blockedRequests)
            }
        }
    }

    @Test
    fun theLauncherShowsTheWelcomePage() {
        val launch = Reader.context.packageManager.getLaunchIntentForPackage(Reader.context.packageName)!!
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            Reader.waitForError(scenario)
            assertEquals("Read Markdown without opening an editor.", Reader.text(scenario, "#stateTitle"))
        }
    }

    @Test
    fun theOpenButtonUsesTheSystemFilePicker() {
        val picker = object : Instrumentation.ActivityMonitor() {
            var seen: Intent? = null
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                seen = intent
                val result = Intent().setData(FixtureProvider.uri("showcase.md"))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                return Instrumentation.ActivityResult(Activity.RESULT_OK, result)
            }
        }
        Reader.instrumentation.addMonitor(picker)
        try {
            Reader.open("simple.md").use { scenario ->
                Reader.waitForDocument(scenario)
                Reader.js(scenario, "document.getElementById('openButton').click()")
                Reader.waitFor(scenario, "(document.querySelector('#article h1')||{}).textContent === 'LeanMark renderer showcase'")
                assertTrue(picker.seen!!.hasCategory(Intent.CATEGORY_OPENABLE))
            }
        } finally {
            Reader.instrumentation.removeMonitor(picker)
        }
    }

    @Test
    fun webLinksGoToAnotherAppAndFileLinksExplainWhyNot() {
        val browser = object : Instrumentation.ActivityMonitor() {
            var seen: Uri? = null
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW || intent.data?.scheme != "https") return null
                seen = intent.data
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        Reader.instrumentation.addMonitor(browser)
        try {
            Reader.open("showcase.md").use { scenario ->
                Reader.waitForDocument(scenario)
                Reader.js(scenario, "document.querySelector('a[href=\"https://commonmark.org/\"]').click()")
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (browser.seen == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                assertEquals(Uri.parse("https://commonmark.org/"), browser.seen)

                Reader.js(scenario, "document.querySelector('a[href^=\"linked-document.md\"]').click()")
                Reader.waitFor(scenario, "document.getElementById('statusToast').textContent.indexOf('other files') >= 0")
            }
        } finally {
            Reader.instrumentation.removeMonitor(browser)
        }
    }

    @Test
    fun backClosesAnOpenDiagramBeforeLeaving() {
        assumeTrue("Mermaid needs WebView 94 or later", Reader.webViewMajor >= 94)
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario, timeoutMs = 60_000)
            assertEquals(true, Reader.js(scenario, "window.LeanMarkTest.openViewer(0)"))
            val deadline = SystemClock.uptimeMillis() + 5_000
            var open = false
            while (!open && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { open = it.viewerOpenForTest }
                if (!open) SystemClock.sleep(50)
            }
            assertTrue("the app knows the viewer is open", open)
            Reader.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            Reader.waitFor(scenario, "document.getElementById('figureViewer').hidden")
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            // The second back leaves the app. From Android 12 a root launcher
            // activity is moved to the background instead of finishing.
            Reader.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val gone = SystemClock.uptimeMillis() + 5_000
            while (scenario.state == Lifecycle.State.RESUMED && SystemClock.uptimeMillis() < gone) SystemClock.sleep(50)
            assertTrue("left the app: ${scenario.state}", scenario.state != Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun aCrashedRendererIsReplacedAndTheDocumentReturns() {
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            val before = Reader.webView(scenario)
            scenario.onActivity { it.webViewForTest!!.loadUrl("chrome://crash") }
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (Reader.webView(scenario) === before && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            assertNotSame(before, Reader.webView(scenario))
            Reader.waitForDocument(scenario)
            assertEquals("A simple Markdown note", Reader.text(scenario, "#article h1")?.trim())
        }
    }

    private fun Any?.asLong(): Long = (this as Number).toLong()
}
