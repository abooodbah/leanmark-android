package io.github.abooodbah.leanmark

/**
 * The reader page is served from the app's assets under an https origin that
 * cannot exist (.invalid is reserved), so its absolute paths such as
 * /reader.css work as on the desktop. Only the listed files are served. Every
 * other request, local or remote, gets no answer.
 */
object ReaderAssets {
    const val ORIGIN = "https://reader.leanmark.invalid"
    const val PAGE_URL = "$ORIGIN/reader.html"
    const val ASSET_ROOT = "reader"

    private val files = mapOf(
        "/reader.html" to "text/html",
        "/reader.css" to "text/css",
        "/reader.js" to "text/javascript",
        "/android-host.js" to "text/javascript",
        "/vendor/mermaid.min.js" to "text/javascript",
        "/fonts/ibm-plex-sans-latin-400-normal.woff2" to "font/woff2",
        "/fonts/ibm-plex-sans-latin-500-normal.woff2" to "font/woff2",
        "/fonts/ibm-plex-sans-latin-600-normal.woff2" to "font/woff2",
        "/fonts/ibm-plex-serif-latin-600-normal.woff2" to "font/woff2",
        "/fonts/ibm-plex-mono-latin-400-normal.woff2" to "font/woff2",
    )

    val paths: Set<String> get() = files.keys

    data class Asset(val assetPath: String, val mimeType: String)

    /** The packaged file for [url], or null when the request must be blocked. */
    fun route(url: String): Asset? {
        if (!url.startsWith("$ORIGIN/")) return null
        // Queries and fragments never select a different file.
        val path = url.substring(ORIGIN.length).substringBefore('#').substringBefore('?')
        val mimeType = files[path] ?: return null
        return Asset(ASSET_ROOT + path, mimeType)
    }
}
