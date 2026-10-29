package io.github.abooodbah.leanmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderCommandTest {
    @Test
    fun plainCommands() {
        assertEquals(ReaderCommand.Ready, ReaderCommand.parse("ready"))
        assertEquals(ReaderCommand.OpenFile, ReaderCommand.parse("open-file"))
        assertEquals(ReaderCommand.Reload, ReaderCommand.parse("reload"))
        assertEquals(ReaderCommand.Viewer(true), ReaderCommand.parse("viewer|open"))
        assertEquals(ReaderCommand.Viewer(false), ReaderCommand.parse("viewer|closed"))
        assertEquals(ReaderCommand.Rendered("ready"), ReaderCommand.parse("render|ready"))
        assertEquals(ReaderCommand.Rendered("diagrams"), ReaderCommand.parse("render|diagrams"))
        assertNull(ReaderCommand.parse("render|done"))
    }

    @Test
    fun themeFallsBackToSystem() {
        assertEquals(ReaderCommand.Theme("dark"), ReaderCommand.parse("theme|dark"))
        assertEquals(ReaderCommand.Theme("light"), ReaderCommand.parse("THEME|Light"))
        assertEquals(ReaderCommand.Theme("system"), ReaderCommand.parse("theme|sepia"))
    }

    @Test
    fun zoom() {
        assertEquals(ReaderCommand.Zoom(1), ReaderCommand.parse("zoom|in"))
        assertEquals(ReaderCommand.Zoom(-1), ReaderCommand.parse("zoom|out"))
        assertEquals(ReaderCommand.Zoom(0), ReaderCommand.parse("zoom|reset"))
        assertNull(ReaderCommand.parse("zoom|sideways"))
    }

    @Test
    fun links() {
        assertEquals(ReaderCommand.OpenLink("https://a.example/x|y"), ReaderCommand.parse("open-link|https://a.example/x|y"))
        assertEquals(ReaderCommand.OpenLink("other.md"), ReaderCommand.parse("open-link-tab|other.md"))
        assertNull(ReaderCommand.parse("open-link|"))
        assertNull(ReaderCommand.parse("open-link|" + "a".repeat(8193)))
    }

    @Test
    fun copySourceNeedsFourValidFields() {
        assertEquals(ReaderCommand.CopySource(7, 2, 5), ReaderCommand.parse("copy-source|7|0|2|5"))
        assertEquals(ReaderCommand.CopySource(1, -1, 0), ReaderCommand.parse("copy-source|1|0|-1|0"))
        assertNull(ReaderCommand.parse("copy-source|7|0|2"))
        assertNull(ReaderCommand.parse("copy-source|7|0|2|5|9"))
        assertNull(ReaderCommand.parse("copy-source|x|0|2|5"))
        assertNull(ReaderCommand.parse("copy-source|-1|0|2|5"))
        assertNull(ReaderCommand.parse("copy-source|1|0|-2|5"))
        assertNull(ReaderCommand.parse("copy-source|4294967296|0|2|5"))
    }

    @Test
    fun unknownMessagesAreIgnored() {
        assertNull(ReaderCommand.parse(""))
        assertNull(ReaderCommand.parse("smoke-ok"))
        assertNull(ReaderCommand.parse("tab-close|3"))
    }
}
