package io.github.abooodbah.leanmark

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PoliciesTest {
    @Test
    fun linksToTheWebAndMailGoToOtherApps() {
        assertEquals(LinkAction.External("https://commonmark.org/"), LinkAction.forHref("https://commonmark.org/"))
        assertEquals(LinkAction.External("HTTP://a.example"), LinkAction.forHref(" HTTP://a.example "))
        assertEquals(LinkAction.External("mailto:a@b.example"), LinkAction.forHref("mailto:a@b.example"))
    }

    @Test
    fun otherLinksAreRefusedOrLeftToThePage() {
        assertEquals(LinkAction.Ignore, LinkAction.forHref("#diagrams"))
        assertEquals(LinkAction.Ignore, LinkAction.forHref("  "))
        for (href in listOf("javascript:alert(1)", "data:text/html,x", "file:///etc/hosts", "intent://x#Intent;end", "//evil.example/x")) {
            assertTrue(href, LinkAction.forHref(href) is LinkAction.Refuse)
        }
        val local = LinkAction.forHref("linked-document.md#linked-destination")
        assertTrue(local is LinkAction.Refuse)
        assertTrue((local as LinkAction.Refuse).message.contains("other files"))
    }

    @Test
    fun readsUpToTheLimit() {
        val bytes = ByteArray(1000) { (it % 251).toByte() }
        assertArrayEquals(bytes, DocumentReader.readLimited(ByteArrayInputStream(bytes), limit = 1000))
        assertArrayEquals(bytes, DocumentReader.readLimited(ByteArrayInputStream(bytes), limit = 1000, sizeHint = 1000))
        assertArrayEquals(ByteArray(0), DocumentReader.readLimited(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun aDeclaredSizeIsReadIntoOneArrayWithoutACopy() {
        // 8 MiB with its size known, as most providers report it. A growing
        // buffer and a copy of it would allocate about twice the file.
        val size = 8 * 1024 * 1024
        val bytes = ByteArray(size) { (it % 251).toByte() }
        DocumentReader.readLimited(ByteArrayInputStream(bytes), sizeHint = size.toLong()) // warm up
        val before = allocatedBytes()
        val read = DocumentReader.readLimited(ByteArrayInputStream(bytes), sizeHint = size.toLong())
        val allocated = allocatedBytes() - before
        assertArrayEquals(bytes, read)
        assertTrue("allocated $allocated bytes for a $size-byte file", allocated < size * 1.25)
    }

    /**
     * Bytes this thread has allocated, from the JVM that runs the tests. The
     * management classes are not in android.jar, so they are reached by name.
     */
    private fun allocatedBytes(): Long {
        val threads = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val allocated = Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        return allocated.invoke(threads, Thread.currentThread().id) as Long
    }

    @Test
    fun aStreamShorterOrLongerThanItsDeclaredSizeIsStillReadWhole() {
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        assertArrayEquals(bytes, DocumentReader.readLimited(ByteArrayInputStream(bytes), sizeHint = 6000))
        assertArrayEquals(bytes, DocumentReader.readLimited(ByteArrayInputStream(bytes), sizeHint = 4000))
        assertArrayEquals(bytes, DocumentReader.readLimited(ByteArrayInputStream(bytes), limit = 5000, sizeHint = 4999))
        try {
            DocumentReader.readLimited(ByteArrayInputStream(bytes), limit = 4999, sizeHint = 4000)
            fail("expected TooLargeException")
        } catch (expected: DocumentReader.TooLargeException) {
            assertEquals(DocumentReader.TOO_LARGE, expected.message)
        }
    }

    @Test
    fun refusesOneByteOverTheLimit() {
        try {
            DocumentReader.readLimited(ByteArrayInputStream(ByteArray(1001)), limit = 1000)
            fail("expected TooLargeException")
        } catch (expected: DocumentReader.TooLargeException) {
            assertEquals(DocumentReader.TOO_LARGE, expected.message)
        }
    }

    @Test
    fun refusesADeclaredSizeWithoutReading() {
        var reads = 0
        val stream = object : InputStream() {
            override fun read(): Int {
                reads++
                return 0
            }
        }
        try {
            DocumentReader.readLimited(stream, limit = 10, sizeHint = 11)
            fail("expected TooLargeException")
        } catch (expected: DocumentReader.TooLargeException) {
            assertEquals(0, reads)
        }
    }

    @Test
    fun stopsReadingAnEndlessStream() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                served += len
                return len
            }
        }
        try {
            DocumentReader.readLimited(endless, limit = 1_000_000)
            fail("expected TooLargeException")
        } catch (expected: DocumentReader.TooLargeException) {
            assertTrue("read $served bytes", served <= 1_000_000 + 64 * 1024)
        }
    }

    @Test
    fun theLimitIsTheDesktops() {
        assertEquals(32L * 1024 * 1024, DocumentReader.MAX_BYTES)
    }

    @Test
    fun webViewVersions() {
        assertEquals(124, WebViewSupport.majorVersion("124.0.6367.82"))
        assertEquals(null, WebViewSupport.majorVersion("dev"))
        assertEquals(null, WebViewSupport.majorVersion(null))
        assertTrue(WebViewSupport.isSupported("69.0.3497.100"))
        assertTrue(WebViewSupport.isSupported("140.0.1"))
        assertFalse(WebViewSupport.isSupported("68.0.3440.91"))
        assertFalse(WebViewSupport.isSupported("58.0.3029.125"))
        assertFalse(WebViewSupport.isSupported(null))
        assertTrue(WebViewSupport.message("58.0.3029.125").contains("58.0.3029.125"))
        assertTrue(WebViewSupport.message(null).contains("none"))
    }
}
