package io.github.abooodbah.leanmark

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAssetsTest {
    private val assetDir = File("src/main/assets/reader")

    @Test
    fun servesOnlyListedFilesFromTheReaderOrigin() {
        assertEquals(
            ReaderAssets.Asset("reader/reader.html", "text/html"),
            ReaderAssets.route("https://reader.leanmark.invalid/reader.html"),
        )
        assertEquals(
            ReaderAssets.Asset("reader/vendor/mermaid.min.js", "text/javascript"),
            ReaderAssets.route("https://reader.leanmark.invalid/vendor/mermaid.min.js?v=1#x"),
        )
        val refused = listOf(
            "https://example.com/reader.html",
            "http://reader.leanmark.invalid/reader.html",
            "https://reader.leanmark.invalid.example.com/reader.html",
            "https://reader.leanmark.invalid:8443/reader.html",
            "https://reader.leanmark.invalid/",
            "https://reader.leanmark.invalid/../reader.html",
            "https://reader.leanmark.invalid/fonts/../reader.html",
            "https://reader.leanmark.invalid/%2e%2e/THIRD_PARTY_NOTICES.md",
            "https://reader.leanmark.invalid/THIRD_PARTY_NOTICES.md",
            "https://reader.leanmark.invalid/missing.png",
            "data:text/html,hi",
            "file:///android_asset/reader/reader.html",
            "content://io.github.abooodbah.leanmark.fixtures/simple.md",
        )
        for (url in refused) assertNull(url, ReaderAssets.route(url))
    }

    @Test
    fun everyServedFileIsPackagedAndEveryPackagedFileIsServed() {
        val packaged = assetDir.walkTopDown().filter { it.isFile }
            .map { "/" + it.relativeTo(assetDir).invariantSeparatorsPath }.toSet()
        assertEquals(packaged, ReaderAssets.paths)
    }

    @Test
    fun pageLoadsTheAndroidBridgeBeforeTheReader() {
        val page = File(assetDir, "reader.html").readText()
        val bridge = page.indexOf("""<script src="/android-host.js"></script>""")
        val reader = page.indexOf("""<script src="/reader.js" defer></script>""")
        assertTrue("bridge script present", bridge > 0)
        assertTrue("bridge runs first", bridge < reader)
        // The desktop policy stays in force: no connections, frames or objects.
        assertTrue(page.contains("connect-src 'none'"))
        assertTrue(page.contains("default-src 'none'"))
        assertTrue(page.contains("script-src 'self';"))
    }

    @Test
    fun copiedFilesMatchTheirRecordedSums() {
        val sums = File("../third_party/reader-assets.sha256").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line -> line.substringAfter("  ") to line.substringBefore("  ") }
        assertTrue("sums recorded", sums.size >= 15)
        for ((path, expected) in sums) {
            val bytes = File("..", path).readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals("$path changed; run scripts/update-reader-assets.sh", expected, actual)
        }
    }
}
