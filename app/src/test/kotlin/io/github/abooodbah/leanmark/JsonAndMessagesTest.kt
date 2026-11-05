package io.github.abooodbah.leanmark

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonAndMessagesTest {
    @Test
    fun quoteEscapesWhatJsonAndJavaScriptNeed() {
        val raw = "a\"b\\c\nd\re\tf\u0001g\u2028h\u2029i"
        val quoted = Json.quote(raw)
        assertEquals(raw, JSONObject("""{"v":$quoted}""").getString("v"))
        assertFalse("no raw line separators", quoted.contains('\u2028') || quoted.contains('\u2029'))
        assertFalse("no raw control characters", quoted.any { it < ' ' })
    }

    @Test
    fun quoteKeepsUnicodeText() {
        val text = "café 日本語 العربية 🚀"
        assertEquals("\"$text\"", Json.quote(text))
    }

    @Test
    fun documentMessageIsOneValidObject() {
        val fields = "\"hasMermaid\":true,\"html\":\"<h1>T\\u2028 café 🚀</h1>\"".toByteArray(Charsets.UTF_8)
        val message = JSONObject(HostMessages.document("dark", "Notes \"1\".md", "content://x/1", 42, fields))
        assertEquals("document", message.getString("type"))
        assertEquals("dark", message.getString("theme"))
        assertEquals("Notes \"1\".md", message.getString("fileName"))
        assertEquals("content://x/1", message.getString("path"))
        assertEquals(42, message.getLong("sourceBytes"))
        assertEquals(0, message.getInt("tabId"))
        assertTrue(message.getBoolean("hasMermaid"))
        assertEquals("<h1>T\u2028 café 🚀</h1>", message.getString("html"))
    }

    /**
     * Bytes this thread has allocated, from the JVM that runs the tests. The
     * management classes are not in android.jar, so they are reached by name.
     */
    private fun allocatedBytes(): Long {
        val threads = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val allocated = Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        return allocated.invoke(threads, Thread.currentThread().id) as Long
    }

    @Test
    fun theDocumentMessageIsBuiltFromTheRenderedBytesInOnePass() {
        // The rendered HTML stays UTF-8 bytes until it is sent, and the message
        // is one array and one string, not the HTML as text and a second copy
        // of it inside the message.
        val html = "<p>" + "x".repeat(4 * 1024 * 1024) + "</p>"
        val fields = ("\"hasMermaid\":false,\"html\":\"" + html + "\"").toByteArray(Charsets.UTF_8)
        HostMessages.document("light", "a.md", "content://x/a", 1, fields) // warm up
        val before = allocatedBytes()
        val message = HostMessages.document("light", "a.md", "content://x/a", 1, fields)
        val allocated = allocatedBytes() - before
        assertTrue("allocated $allocated bytes for ${fields.size} bytes of fields", allocated < fields.size * 2.5)
        assertEquals(html, JSONObject(message).getString("html"))
    }

    @Test
    fun otherMessagesMatchTheReaderProtocol() {
        val host = JSONObject(HostMessages.HOST)
        assertEquals("host", host.getString("type"))
        assertTrue(host.getBoolean("copySource"))
        assertFalse(host.getBoolean("tabs"))

        val error = JSONObject(HostMessages.error("system", "a.md", "Bad\nfile"))
        assertEquals("error", error.getString("type"))
        assertEquals("Bad\nfile", error.getString("message"))
        assertEquals("a.md", error.getString("fileName"))

        assertEquals("empty", JSONObject(HostMessages.empty("light")).getString("type"))
        assertEquals("warning", JSONObject(HostMessages.status("x", "warning")).getString("tone"))
        assertEquals("dark", JSONObject(HostMessages.theme("dark")).getString("value"))
        assertEquals(125, JSONObject(HostMessages.zoom(125)).getInt("percent"))
        val copied = JSONObject(HostMessages.copied(4294967295L, true))
        assertEquals(4294967295L, copied.getLong("requestId"))
        assertTrue(copied.getBoolean("ok"))
        assertEquals("android-back", JSONObject(HostMessages.BACK).getString("type"))
    }
}
