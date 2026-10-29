package io.github.abooodbah.leanmark

/**
 * Messages from the app to the desktop reader page, in the shape reader.js
 * handles in handleNativeMessage. They match what the Linux host sends.
 */
object HostMessages {
    // This host reads the file again for Copy, so copies are exact Markdown. It
    // shows one document per window, so there are no tabs.
    const val HOST = """{"type":"host","copySource":true,"tabs":false}"""

    fun empty(theme: String) = """{"type":"empty","theme":${Json.quote(theme)}}"""

    fun error(theme: String, fileName: String, message: String) =
        """{"type":"error","theme":${Json.quote(theme)},"fileName":${Json.quote(fileName)},""" +
            """"path":${Json.quote(fileName)},"message":${Json.quote(message)}}"""

    /** [renderedFields] is the output of [MarkdownCore.renderFields]. */
    fun document(theme: String, fileName: String, path: String, sourceBytes: Long, renderedFields: String) =
        """{"type":"document","theme":${Json.quote(theme)},"tabId":0,""" +
            """"fileName":${Json.quote(fileName)},"path":${Json.quote(path)},""" +
            """"sourceBytes":$sourceBytes,$renderedFields}"""

    fun status(message: String, tone: String = "info") =
        """{"type":"status","tone":${Json.quote(tone)},"message":${Json.quote(message)}}"""

    fun theme(value: String) = """{"type":"theme","value":${Json.quote(value)}}"""

    fun zoom(percent: Int) = """{"type":"zoom","percent":$percent}"""

    fun copied(requestId: Long, ok: Boolean) = """{"type":"copied","requestId":$requestId,"ok":$ok}"""

    /** Handled by android-host.js, not reader.js: closes the figure viewer. */
    const val BACK = """{"type":"android-back"}"""
}
