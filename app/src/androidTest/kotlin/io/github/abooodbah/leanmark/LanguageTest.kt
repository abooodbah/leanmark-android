package io.github.abooodbah.leanmark

import android.annotation.TargetApi
import android.app.LocaleManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Simplified Chinese interface from desktop LeanMark 0.8.0. The reader
 * page uses the desktop's string tables; the app's own messages come from
 * Android resources (values-zh-rCN). The Language choice in the settings
 * sheet (System, English, 简体中文) is saved as "language" beside the theme.
 * The emulators run in English (en-US).
 */
@RunWith(AndroidJUnit4::class)
class LanguageTest {
    private val prefs get() = Reader.context.getSharedPreferences("reader", 0)

    @Before
    @After
    fun startFromDefaults() {
        prefs.edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 33) setAppLanguage(LocaleList.getEmptyLocaleList())
    }

    @Test
    fun settingsFromTheLastVersionHaveNoLanguageAndAnEnglishPhoneSeesNoChange() {
        assumeEnglishPhone()
        // What 0.2.0 saves: theme, zoom and width, no language.
        prefs.edit().putString("theme", "dim").putString("width", "wide").putFloat("zoom", 1f).commit()
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("en", Reader.js(scenario, "document.documentElement.lang"))
            assertEquals(ENGLISH, Reader.js(scenario, LABELS))
            assertTrue(pageUrl(scenario).endsWith("/reader.html?lang=en"))
            openSheet(scenario)
            assertEquals("the Language group is shown", false, Reader.js(scenario, "document.getElementById('languageGroup').hidden"))
            assertEquals("system", Reader.js(scenario, CHECKED_LANGUAGE))
            assertEquals("Language", Reader.text(scenario, "#languageGroup legend"))
            assertNull("nothing is saved until a language is chosen", prefs.getString("language", null))
            assertEquals("dim", Reader.js(scenario, "document.documentElement.dataset.theme"))
        }
    }

    @Test
    fun choosingSimplifiedChineseTranslatesThePageAndIsSaved() = Reader.open("showcase.md").use { scenario ->
        Reader.waitForDocument(scenario)
        openSheet(scenario)
        assertEquals(
            "the choice is on the sheet",
            true,
            Reader.js(scenario, "document.querySelector('input[name=settings-language][value=\"zh-CN\"]').getBoundingClientRect().width > 0"),
        )
        Reader.js(scenario, "document.querySelector('input[name=settings-language][value=\"zh-CN\"]').click()")
        Reader.waitFor(scenario, "document.documentElement.lang === 'zh-CN'")
        waitForSaved("language", "zh-CN")
        Reader.waitFor(scenario, "document.getElementById('settingsButton').getAttribute('aria-label') === '设置'")
        assertEquals(CHINESE, Reader.js(scenario, LABELS))
        assertEquals("语言", Reader.text(scenario, "#languageGroup legend"))
        assertEquals("跟随系统", Reader.text(scenario, "#languageGroup [data-i18n='language.system']"))
        assertEquals("zh-CN", Reader.js(scenario, CHECKED_LANGUAGE))
        Reader.js(scenario, "document.getElementById('settingsClose').click()")
        Reader.js(scenario, "document.getElementById('outlineButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('outlineSheet').hidden")
        assertEquals("本页内容", Reader.text(scenario, "#outlineSheetTitle"))
        assertEquals("关闭", Reader.js(scenario, "document.querySelector('#outlineSheet .viewer-button').getAttribute('aria-label')"))
    }

    @Test
    fun aSavedLanguageIsUsedFromTheFirstPaintAndKeptThroughScrollAndRotation() {
        prefs.edit().putString("language", "zh-CN").commit()
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            // The page is opened in Chinese, so its first paint is Chinese.
            assertTrue(pageUrl(scenario), pageUrl(scenario).endsWith("/reader.html?lang=zh-CN"))
            assertEquals("zh-CN", Reader.js(scenario, "document.documentElement.lang"))
            assertEquals(CHINESE, Reader.js(scenario, LABELS))
            assertEquals("本页内容", Reader.text(scenario, "#outlineSheetTitle"))
            Reader.scrollDownByTouch(scenario)
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Reader.waitFor(scenario, "window.innerWidth > window.innerHeight")
            assertEquals(CHINESE, Reader.js(scenario, LABELS))
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            Reader.waitFor(scenario, "window.innerWidth < window.innerHeight")
            assertEquals(CHINESE, Reader.js(scenario, LABELS))
            openSheet(scenario)
            assertEquals("zh-CN", Reader.js(scenario, CHECKED_LANGUAGE))
        }
    }

    @Test
    fun englishCanBeChosenBackAndAnUnknownSavedLanguageFollowsThePhone() {
        assumeEnglishPhone()
        prefs.edit().putString("language", "zh-CN").commit()
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            openSheet(scenario)
            Reader.js(scenario, "document.querySelector('input[name=settings-language][value=en]').click()")
            Reader.waitFor(scenario, "document.documentElement.lang === 'en'")
            waitForSaved("language", "en")
            Reader.waitFor(scenario, "document.getElementById('settingsButton').getAttribute('aria-label') === 'Settings'")
        }
        // A name from a later version gives System.
        prefs.edit().putString("language", "fr").commit()
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("en", Reader.js(scenario, "document.documentElement.lang"))
            openSheet(scenario)
            assertEquals("system", Reader.js(scenario, CHECKED_LANGUAGE))
        }
    }

    /**
     * The page also translates the desktop's English sentences itself, so
     * this reads the app's own strings, the ones it sends.
     */
    @Test
    fun theAppsOwnStringsFollowTheLanguage() {
        assumeEnglishPhone()
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("Open a document before copying.", appString(scenario, R.string.open_before_copying))
            openSheet(scenario)
            Reader.js(scenario, "document.querySelector('input[name=settings-language][value=\"zh-CN\"]').click()")
            waitForSaved("language", "zh-CN")
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (appString(scenario, R.string.open_before_copying) != OPEN_FIRST_CHINESE && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
            }
            assertEquals(OPEN_FIRST_CHINESE, appString(scenario, R.string.open_before_copying))
            assertEquals("no desktop translation", "This device has no file picker.", appString(scenario, R.string.no_file_picker))
        }
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals(OPEN_FIRST_CHINESE, appString(scenario, R.string.open_before_copying))
        }
    }

    @Test
    fun aFileOverTheLimitIsRefusedInChinese() {
        prefs.edit().putString("language", "zh-CN").commit()
        Reader.open("huge.md").use { scenario ->
            Reader.waitForError(scenario)
            assertEquals("无法打开此文档。", Reader.text(scenario, "#stateTitle"))
            assertEquals(TOO_LARGE_CHINESE, Reader.text(scenario, "#stateMessage"))
        }
    }

    @Test
    fun aCorruptFileGetsTheCoresMessageInChinese() {
        prefs.edit().putString("language", "zh-CN").commit()
        Reader.open("legacy-encoding.md").use { scenario ->
            Reader.waitForError(scenario)
            assertEquals("此文件不是有效的 UTF-8 编码。请将其转换为 UTF-8 后再打开。", Reader.text(scenario, "#stateMessage"))
            assertEquals("legacy-encoding.md", Reader.text(scenario, "#fileName"))
        }
    }

    @Test
    fun anErrorOnScreenFollowsALanguageChange() {
        prefs.edit().putString("language", "zh-CN").commit()
        Reader.open("huge.md").use { scenario ->
            Reader.waitForError(scenario)
            assertEquals(TOO_LARGE_CHINESE, Reader.text(scenario, "#stateMessage"))
            openSheet(scenario)
            Reader.js(scenario, "document.querySelector('input[name=settings-language][value=en]').click()")
            Reader.waitFor(scenario, "document.getElementById('stateMessage').textContent.indexOf('32 MB safety limit') >= 0")
            assertEquals("This document could not be opened.", Reader.text(scenario, "#stateTitle"))
        }
    }

    @Test
    fun aMessageWithoutADesktopTranslationStaysEnglish() {
        prefs.edit().putString("language", "zh-CN").commit()
        Reader.open("showcase.md").use { scenario ->
            Reader.waitForDocument(scenario)
            Reader.js(scenario, "document.querySelector('a[href^=\"linked-document.md\"]').click()")
            Reader.waitFor(
                scenario,
                "document.getElementById('statusToast').textContent === " +
                    "\"LeanMark can't follow links to other files on Android. Open that file from your file manager.\"",
            )
        }
    }

    @Test
    fun androidUsesTheChineseResourcesForSimplifiedChineseOnly() {
        val chinese = listOf("zh-CN", "zh-SG", "zh-Hans", "zh-Hans-CN", "zh-Hans-SG", "zh-Hans-HK", "zh")
        val english = listOf("en-US", "en-GB", "fr-FR", "de-DE", "ja-JP", "zh-TW", "zh-HK", "zh-MO", "zh-Hant", "zh-Hant-TW")
        for (tag in chinese) {
            assertEquals(tag, "zh-CN", resource(tag, "interface_language"))
            assertEquals(tag, "请先打开文档再复制。", resource(tag, "open_before_copying"))
        }
        for (tag in english) {
            assertEquals(tag, "en", resource(tag, "interface_language"))
            assertEquals(tag, "Open a document before copying.", resource(tag, "open_before_copying"))
        }
    }

    @Test
    fun theSystemSettingFollowsTheAppLanguageAndroidPicks() {
        // Android 13 and later let the user pick a language per app; it is
        // what this app sees as the phone's language.
        assumeTrue("per-app languages need Android 13", Build.VERSION.SDK_INT >= 33)
        setAppLanguage(LocaleList.forLanguageTags("zh-SG"))
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("zh-CN", Reader.js(scenario, "document.documentElement.lang"))
            assertTrue(pageUrl(scenario), pageUrl(scenario).endsWith("?lang=zh-CN"))
            openSheet(scenario)
            assertEquals("system", Reader.js(scenario, CHECKED_LANGUAGE))
        }
        setAppLanguage(LocaleList.forLanguageTags("zh-TW"))
        Reader.open("simple.md").use { scenario ->
            Reader.waitForDocument(scenario)
            assertEquals("Traditional Chinese keeps English", "en", Reader.js(scenario, "document.documentElement.lang"))
        }
        assertNull(prefs.getString("language", null))
    }

    private fun appString(scenario: ActivityScenario<MainActivity>, id: Int): String {
        var text = ""
        scenario.onActivity { text = it.stringsForTest.getString(id) }
        return text
    }

    private fun resource(tag: String, name: String): String {
        val config = Configuration(Reader.context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        val resources = Reader.context.createConfigurationContext(config).resources
        val id = resources.getIdentifier(name, "string", Reader.context.packageName)
        assertTrue("no string resource $name", id != 0)
        return resources.getString(id)
    }

    @TargetApi(33)
    private fun setAppLanguage(locales: LocaleList) {
        val manager = Reader.context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = locales
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (manager.applicationLocales != locales && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        Reader.instrumentation.waitForIdleSync()
    }

    private fun assumeEnglishPhone() {
        val system = android.content.res.Resources.getSystem().configuration.locales[0]
        assumeTrue("the emulator runs in English, not $system", system.language == "en")
    }

    private fun pageUrl(scenario: ActivityScenario<MainActivity>): String {
        var url: String? = null
        scenario.onActivity { url = it.webViewForTest?.url }
        return url.orEmpty()
    }

    private fun openSheet(scenario: ActivityScenario<MainActivity>) {
        Reader.js(scenario, "document.getElementById('settingsButton').click()")
        Reader.waitFor(scenario, "!document.getElementById('settingsPanel').hidden")
    }

    private fun waitForSaved(key: String, value: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (prefs.getString(key, null) != value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertEquals(value, prefs.getString(key, null))
    }

    private companion object {
        /** The labels a phone shows in the toolbar and the outline button, joined by "|". */
        const val LABELS = """[
            document.getElementById('findInput').placeholder,
            document.getElementById('copyButton').textContent.trim(),
            document.getElementById('openButton').textContent.trim(),
            document.getElementById('settingsButton').getAttribute('aria-label'),
            document.getElementById('settingsButton').title,
            document.getElementById('settingsTitle').textContent,
            document.getElementById('outlineButton').getAttribute('aria-label')
          ].join('|')"""

        // English from reader.html; Chinese from the desktop's zh-CN table.
        const val ENGLISH = "Find|Copy|Open|Settings|Settings|Settings|On this page"
        const val CHINESE = "查找|复制|打开|设置|设置|设置|本页内容"

        const val CHECKED_LANGUAGE =
            "(document.querySelector('input[name=settings-language]:checked') || {}).value || ''"

        const val OPEN_FIRST_CHINESE = "请先打开文档再复制。"

        const val TOO_LARGE_CHINESE = "此文件超过了 LeanMark 的 32 MB 安全上限。如需查看原始文本，请在编辑器中打开。"
    }
}
