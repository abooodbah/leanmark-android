package io.github.abooodbah.leanmark

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The Simplified Chinese interface from desktop LeanMark 0.8.0. The setting
 * follows the desktop core's ParseLanguageSetting and LanguageSettingName;
 * the Chinese text in values-zh-rCN must be the desktop's own, from the
 * tables in reader.js.
 */
class InterfaceLanguageTest {
    @Test
    fun eachSavedLanguageNameReadsBackAsItself() {
        assertEquals(listOf("system", "en", "zh-CN"), InterfaceLanguage.SETTINGS)
        for (name in InterfaceLanguage.SETTINGS) assertEquals(name, InterfaceLanguage.settingName(name))
    }

    @Test
    fun namesAreMatchedWithoutRegardToAsciiCase() {
        assertEquals("zh-CN", InterfaceLanguage.settingName("ZH-cn"))
        assertEquals("en", InterfaceLanguage.settingName("EN"))
        assertEquals("system", InterfaceLanguage.settingName("SYSTEM"))
    }

    @Test
    fun aMissingOrUnknownLanguageFollowsThePhone() {
        // Settings from 0.2.0 have no language; a later version may save one
        // this version does not have.
        for (name in listOf(null, "", "fr", "zh", "zh-TW", "en ", "zh_CN")) {
            assertEquals("$name", "system", InterfaceLanguage.settingName(name))
        }
    }

    @Test
    fun theInterfaceIsTheChosenLanguageOrThePhones() {
        assertEquals("zh-CN", InterfaceLanguage.resolve("system", "zh-CN"))
        assertEquals("en", InterfaceLanguage.resolve("system", "en"))
        assertEquals("en", InterfaceLanguage.resolve("en", "zh-CN"))
        assertEquals("zh-CN", InterfaceLanguage.resolve("zh-CN", "en"))
        // The page has tables for these two only.
        assertEquals("en", InterfaceLanguage.resolve("system", "fr"))
        assertEquals("en", InterfaceLanguage.resolve("system", ""))
    }

    @Test
    fun thePageReportsALanguageChoice() {
        assertEquals(ReaderCommand.Language("zh-CN"), ReaderCommand.parse("language|zh-CN"))
        assertEquals(ReaderCommand.Language("en"), ReaderCommand.parse("LANGUAGE|En"))
        assertEquals(ReaderCommand.Language("system"), ReaderCommand.parse("language|system"))
        assertEquals(ReaderCommand.Language("system"), ReaderCommand.parse("language|klingon"))
    }

    @Test
    fun theHostMessageNamesTheSettingAndThePhonesLanguage() {
        val host = JSONObject(HostMessages.host(125, "wide", "zh-CN", "en"))
        assertEquals("zh-CN", host.getString("language"))
        assertEquals("en", host.getString("systemLanguage"))
        assertEquals(125, host.getInt("zoom"))
        assertEquals("wide", host.getString("width"))
    }

    @Test
    fun thePageOpensInTheInterfaceLanguage() {
        assertEquals("https://reader.leanmark.invalid/reader.html?lang=zh-CN", ReaderAssets.pageUrl("zh-CN"))
        assertEquals("https://reader.leanmark.invalid/reader.html?lang=en", ReaderAssets.pageUrl("en"))
        assertEquals("https://reader.leanmark.invalid/reader.html?lang=en", ReaderAssets.pageUrl("fr&x=<"))
        assertEquals(ReaderAssets.Asset("reader/reader.html", "text/html"), ReaderAssets.route(ReaderAssets.pageUrl("zh-CN")))
    }

    @Test
    fun everyChineseStringIsTheDesktopsTranslationOfTheSameEnglish() {
        val english = strings("values")
        val chinese = strings("values-zh-rCN")
        assertTrue("values-zh-rCN has strings", chinese.isNotEmpty())
        for ((name, text) in chinese) {
            val source = english[name]
            assertNotNull("$name has no English string", source)
            assertEquals("$name: \"$source\"", desktopChinese[source], text)
        }
    }

    @Test
    fun everyEnglishStringTheDesktopTranslatesIsTranslated() {
        val chinese = strings("values-zh-rCN")
        for ((name, text) in strings("values")) {
            if (desktopChinese.containsKey(text)) assertTrue("$name is translated on the desktop", name in chinese)
        }
    }

    /**
     * The app's strings the desktop has no Chinese for. They stay English,
     * the fallback, in a Chinese interface. A new string must be added here
     * or translated from the desktop's tables.
     */
    @Test
    fun theseStringsHaveNoDesktopTranslation() {
        val untranslated = strings("values").keys - strings("values-zh-rCN").keys
        assertEquals(
            setOf(
                "cannot_read",
                "too_large_for_device",
                "no_file_picker",
                "no_app_for_link",
                "changed_copy_again",
                "section_too_large",
                "link_scheme_blocked",
                "link_other_files",
                "shared_text",
                "untitled",
                "webview_needed",
                "webview_none",
            ),
            untranslated,
        )
    }

    @Test
    fun androidPicksTheInterfaceLanguageThroughItsResources() {
        assertEquals("en", strings("values", "language.xml")["interface_language"])
        assertEquals("zh-CN", strings("values-zh-rCN", "language.xml")["interface_language"])
        // Only zh-rCN: a bare values-zh would also be used for Traditional Chinese.
        val folders = File("src/main/res").list()!!.filter { it.startsWith("values-zh") || it.startsWith("values-b+zh") }
        assertEquals(listOf("values-zh-rCN"), folders)
    }

    /** Translatable strings in res/[folder]/[file], by name, unescaped. */
    private fun strings(folder: String, file: String = "strings.xml"): Map<String, String> {
        val path = File("src/main/res/$folder/$file")
        if (!path.isFile) return emptyMap()
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent.replace("\\'", "'").replace("\\\"", "\"") }
    }

    /** English to Simplified Chinese, from the desktop tables in reader.js. */
    private val desktopChinese: Map<String, String> by lazy {
        val script = File("src/main/assets/reader/reader.js").readText()
        val block = script.substringAfter("// BEGIN interface strings").substringBefore("// END interface strings")
        val entry = Regex("""^\s*"((?:[^"\\]|\\.)*)": "((?:[^"\\]|\\.)*)",?\s*$""")
        val tables = mutableMapOf<String, MutableMap<String, String>>()
        var table = ""
        for (line in block.lines()) {
            val trimmed = line.trim()
            when {
                trimmed == "en: {" -> table = "en"
                trimmed == "\"zh-CN\": {" -> table = if ("hostMessages" in tables) "host" else "zh-CN"
                trimmed.startsWith("var hostMessages") -> tables["hostMessages"] = mutableMapOf()
                else -> entry.find(line)?.let { match ->
                    tables.getOrPut(table) { mutableMapOf() }[match.groupValues[1]] = match.groupValues[2]
                }
            }
        }
        val en = tables.getValue("en")
        val zh = tables.getValue("zh-CN")
        assertTrue("reader.js has the desktop's tables", en.size > 100 && zh.size > 100)
        val result = mutableMapOf<String, String>()
        for ((key, text) in en) zh[key]?.let { result[text] = it }
        result.putAll(tables.getValue("host"))
        result
    }
}
