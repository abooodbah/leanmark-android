package io.github.abooodbah.leanmark

/**
 * The oldest WebView the reader is tested on is 69, the version on the API 26
 * emulator image; android-host.js supplies the one newer DOM method the page
 * uses. Mermaid diagrams need 94 (class static blocks). Below that the page
 * shows its own "Diagrams are unavailable" note and the rest still renders.
 */
object WebViewSupport {
    const val MINIMUM_MAJOR = 69

    /** The major version in a WebView versionName such as "124.0.6367.82", or null. */
    fun majorVersion(versionName: String?): Int? =
        versionName?.trim()?.substringBefore('.')?.toIntOrNull()

    fun isSupported(versionName: String?): Boolean {
        val major = majorVersion(versionName) ?: return false
        return major >= MINIMUM_MAJOR
    }

    /** The version to name in R.string.webview_needed, or null for none. */
    fun found(versionName: String?): String? = versionName?.takeIf { it.isNotBlank() }
}
