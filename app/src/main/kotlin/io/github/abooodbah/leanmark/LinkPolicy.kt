package io.github.abooodbah.leanmark

/** What a tapped link in a document does. The page handles #anchors itself. */
sealed interface LinkAction {
    /** Handed to another app, such as the browser or mail app. */
    data class External(val uri: String) : LinkAction
    /** Refused, with the string resource that says why. */
    data class Refuse(val message: Int) : LinkAction
    data object Ignore : LinkAction

    companion object {
        private val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

        fun forHref(href: String): LinkAction {
            val trimmed = href.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return Ignore
            val match = scheme.find(trimmed)
            if (match != null) {
                return when (match.groupValues[1].lowercase()) {
                    "http", "https", "mailto" -> External(trimmed)
                    else -> Refuse(R.string.link_scheme_blocked)
                }
            }
            if (trimmed.startsWith("//")) {
                return Refuse(R.string.link_scheme_blocked)
            }
            // A shared file comes without access to the folder around it, so a
            // relative link has nothing to resolve against.
            return Refuse(R.string.link_other_files)
        }
    }
}
