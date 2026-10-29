package io.github.abooodbah.leanmark

/** The desktop app's C++ core (md4c with GitHub flavour, raw HTML off). */
object MarkdownCore {
    init {
        System.loadLibrary("leanmark")
    }

    /**
     * Renders UTF-8 Markdown and returns the document fields of a host message
     * as UTF-8: `"hasMermaid":true,"html":"..."`, already JSON-escaped.
     */
    @JvmStatic
    external fun renderFields(markdown: ByteArray): ByteArray

    /** The Markdown source of heading [headingIndex]'s section, or all of it when negative. */
    @JvmStatic
    external fun extractSection(markdown: ByteArray, headingIndex: Long, headingCount: Long): ByteArray

    @JvmStatic
    external fun stepZoom(current: Double, direction: Int): Double

    @JvmStatic
    external fun normalizeZoom(value: Double): Double
}

/** Thrown by the native core. [stale] means the file no longer matches the page. */
class MarkdownException(message: String, val stale: Boolean) : Exception(message)
