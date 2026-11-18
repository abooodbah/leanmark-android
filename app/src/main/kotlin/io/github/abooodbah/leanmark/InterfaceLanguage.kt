package io.github.abooodbah.leanmark

/**
 * The interface language from desktop LeanMark 0.8.0. The setting is
 * "system", "en" or "zh-CN", saved as the desktop names it; English is the
 * fallback for every string without a translation. This follows the core's
 * ParseLanguageSetting and ResolveInterfaceLanguage. On Android, "system"
 * is the language Android picks for the app from the phone's languages
 * (R.string.interface_language), so the page and the app's own strings
 * always agree.
 */
object InterfaceLanguage {
    val SETTINGS = listOf("system", "en", "zh-CN")

    /** The languages the reader page has tables for. */
    val LANGUAGES = listOf("en", "zh-CN")

    /** A saved or page-requested name, ignoring ASCII case; anything else gives "system". */
    fun settingName(name: String?): String {
        val text = name.orEmpty()
        return SETTINGS.firstOrNull { setting ->
            setting.length == text.length && setting.indices.all { asciiLower(setting[it]) == asciiLower(text[it]) }
        } ?: "system"
    }

    private fun asciiLower(c: Char): Char = if (c in 'A'..'Z') c + ('a' - 'A') else c

    /** The language the interface shows: the chosen one, or the phone's. */
    fun resolve(setting: String, systemLanguage: String): String {
        val chosen = if (setting == "system") systemLanguage else setting
        return if (chosen in LANGUAGES) chosen else "en"
    }
}
