package io.github.abooodbah.leanmark

/** A message the reader page posts to the app. Unknown messages are ignored. */
sealed interface ReaderCommand {
    data object Ready : ReaderCommand
    data object OpenFile : ReaderCommand
    data object Reload : ReaderCommand
    data class Theme(val value: String) : ReaderCommand
    data class Zoom(val direction: Int) : ReaderCommand
    data class OpenLink(val href: String) : ReaderCommand
    data class CopySource(val requestId: Long, val headingIndex: Long, val headingCount: Long) : ReaderCommand
    data class Viewer(val open: Boolean) : ReaderCommand

    /** The page reached a render state: "ready", or "diagrams" while Mermaid runs. */
    data class Rendered(val state: String) : ReaderCommand

    companion object {
        private const val MAX_LINK = 8192

        fun parse(message: String): ReaderCommand? {
            when (message) {
                "ready" -> return Ready
                "open-file" -> return OpenFile
                "reload" -> return Reload
                "viewer|open" -> return Viewer(true)
                "viewer|closed" -> return Viewer(false)
            }
            val bar = message.indexOf('|')
            if (bar < 0) return null
            val name = message.substring(0, bar).lowercase()
            val rest = message.substring(bar + 1)
            return when (name) {
                "theme" -> Theme(themeName(rest))
                "zoom" -> when (rest.lowercase()) {
                    "in" -> Zoom(1)
                    "out" -> Zoom(-1)
                    "reset" -> Zoom(0)
                    else -> null
                }
                // Ctrl-click asks for a new tab; this host has none, so it is a plain open.
                "open-link", "open-link-tab" ->
                    if (rest.isEmpty() || rest.length > MAX_LINK) null else OpenLink(rest)
                "copy-source" -> parseCopy(rest)
                "render" -> if (rest == "ready" || rest == "diagrams") Rendered(rest) else null
                else -> null
            }
        }

        // requestId|tabId|headingIndex|headingCount, as reader.js sends it.
        /**
         * The theme button's order in desktop LeanMark 0.5.0. Names are
         * matched ignoring ASCII case, as the core's ParseTheme does; anything
         * else, such as a name from a later version, gives "system". This is
         * plain Kotlin so the JVM tests can check it; ThemeTest checks that it
         * agrees with the core on the device.
         */
        val THEMES = listOf("system", "light", "dark", "dim", "dusk")

        fun themeName(name: String?): String {
            val lower = name.orEmpty().lowercase(java.util.Locale.ROOT)
            return if (lower in THEMES) lower else "system"
        }

        private fun parseCopy(arguments: String): ReaderCommand? {
            val fields = arguments.split('|')
            if (fields.size != 4) return null
            val requestId = fields[0].toLongOrNull()?.takeIf { it in 0..4_294_967_295L } ?: return null
            val headingIndex = fields[2].toLongOrNull()?.takeIf { it in -1..Int.MAX_VALUE.toLong() } ?: return null
            val headingCount = fields[3].toLongOrNull()?.takeIf { it in 0..Int.MAX_VALUE.toLong() } ?: return null
            return CopySource(requestId, headingIndex, headingCount)
        }
    }
}
