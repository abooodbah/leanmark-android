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

    /** Text width from desktop 0.6.0: "narrow", "wide" or "full". */
    data class Width(val value: String) : ReaderCommand

    /** The settings sheet was opened or closed (android-host.js). */
    data class Settings(val open: Boolean) : ReaderCommand

    /** The page reached a render state: "ready", or "diagrams" while Mermaid runs. */
    data class Rendered(val state: String) : ReaderCommand

    /** How long a piece of page work took, from android-host.js, for scripts/measure.sh. */
    data class Timing(val name: String, val milliseconds: Long) : ReaderCommand

    companion object {
        private const val MAX_LINK = 8192
        private const val MAX_TIMING_MS = 600_000L
        private val timingName = Regex("[a-z][a-z-]{0,23}")

        fun parse(message: String): ReaderCommand? {
            when (message) {
                "ready" -> return Ready
                "open-file" -> return OpenFile
                "reload" -> return Reload
                "viewer|open" -> return Viewer(true)
                "viewer|closed" -> return Viewer(false)
                "settings|open" -> return Settings(true)
                "settings|closed" -> return Settings(false)
            }
            val bar = message.indexOf('|')
            if (bar < 0) return null
            val name = message.substring(0, bar).lowercase()
            val rest = message.substring(bar + 1)
            return when (name) {
                "theme" -> Theme(themeName(rest))
                "width" -> Width(widthName(rest))
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
                "timing" -> parseTiming(rest)
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

        /**
         * The text widths in desktop LeanMark 0.6.0's order. As in the core's
         * ParseTextWidth, names are matched ignoring ASCII case, and anything
         * else, including no saved width (0.1.x), gives "narrow".
         */
        val WIDTHS = listOf("narrow", "wide", "full")

        fun widthName(name: String?): String {
            val lower = name.orEmpty().lowercase(java.util.Locale.ROOT)
            return if (lower in WIDTHS) lower else "narrow"
        }

        // name|milliseconds, where the name is a short lowercase word.
        private fun parseTiming(arguments: String): ReaderCommand? {
            val name = arguments.substringBefore('|')
            val value = arguments.substringAfter('|', "")
            if (!timingName.matches(name) || value.isEmpty() || !value.all { it in '0'..'9' }) return null
            val milliseconds = value.takeIf { it.length <= 7 }?.toLong()?.takeIf { it <= MAX_TIMING_MS } ?: return null
            return Timing(name, milliseconds)
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
