package io.github.abooodbah.leanmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves test documents through pipes, as many real providers do, so the app
 * cannot rely on seeking or on a file path. Names ending in "-nosize" report no
 * size, like providers that stream from the network or an archive.
 */
class FixtureProvider : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri): String = when {
        uri.lastPathSegment.orEmpty().endsWith(".txt") -> "text/plain"
        else -> "text/markdown"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException("$uri")
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        cursor.addRow(
            columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> name.replace("-nosize", "")
                    OpenableColumns.SIZE -> if (name.contains("-nosize")) null else sizeOf(name)
                    else -> null
                }
            }.toTypedArray(),
        )
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("$uri")
        sizeOf(name) // Throws for unknown names before a pipe is made.
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        written.remove(name)
        Thread {
            var total = 0L
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                try {
                    write(name) { chunk ->
                        out.write(chunk)
                        total += chunk.size
                    }
                } catch (closed: IOException) {
                    // The reader stopped early, which is what the size limit should do.
                }
            }
            written[name] = total
        }.start()
        return read
    }

    private fun sizeOf(name: String): Long {
        var total = 0L
        write(name) { total += it.size }
        return total
    }

    private fun write(name: String, out: (ByteArray) -> Unit) {
        val assets = requireNotNull(context).assets
        when (name.replace("-nosize", "")) {
            "simple.md", "showcase.md", "security.md", "linked-document.md" ->
                out(assets.open("fixtures/$name".replace("-nosize", "")).use { it.readBytes() })
            "notes.txt" -> out(assets.open("fixtures/simple.md").use { it.readBytes() })
            // The showcase as a legacy editor would save it in Windows-1252.
            // Characters outside that code page become "?", as they do there.
            "legacy-encoding.md" -> out(
                String(assets.open("fixtures/showcase.md").use { it.readBytes() }, Charsets.UTF_8)
                    .toByteArray(Charset.forName("windows-1252")),
            )
            "large.md" -> repeatReal(LARGE_BYTES, out)
            "huge.md" -> repeatReal(HUGE_BYTES, out)
            else -> throw FileNotFoundException(name)
        }
    }

    /** The desktop repositories' own documentation, repeated up to [minimum] bytes. */
    private fun repeatReal(minimum: Long, out: (ByteArray) -> Unit) {
        val assets = requireNotNull(context).assets
        val parts = requireNotNull(assets.list("real")).sorted().map { name ->
            assets.open("real/$name").use { it.readBytes() } + "\n\n".toByteArray()
        }
        var written = 0L
        while (written < minimum) {
            for (part in parts) {
                out(part)
                written += part.size
            }
        }
    }

    /** "written": how many bytes the last stream of [arg] delivered, or -1 while it runs. */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
        if (method == "written" && arg != null) Bundle().apply { putLong("bytes", written[arg] ?: -1) } else null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "io.github.abooodbah.leanmark.fixtures"

        /** About 1 MiB: the "300-page" document used for memory and scrolling. */
        const val LARGE_BYTES = 1L * 1024 * 1024

        /** Well past the 32 MiB limit, so a reader that stops at the limit visibly stops early. */
        const val HUGE_BYTES = 40L * 1024 * 1024

        private val written = ConcurrentHashMap<String, Long>()

        fun uri(name: String): Uri = Uri.parse("content://$AUTHORITY/$name")
    }
}
