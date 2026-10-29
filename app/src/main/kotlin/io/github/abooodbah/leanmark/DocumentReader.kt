package io.github.abooodbah.leanmark

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads a document into memory, refusing anything over the desktop's 32 MiB limit. */
object DocumentReader {
    const val MAX_BYTES = 32L * 1024 * 1024

    const val TOO_LARGE =
        "This file is larger than LeanMark's 32 MB safety limit. Open it in an " +
            "editor if you need to inspect the raw text."

    class TooLargeException : Exception(TOO_LARGE)

    /**
     * Reads all of [input]. Stops one byte past [limit] instead of reading the
     * rest, so a huge stream with no declared size costs at most the limit.
     */
    fun readLimited(input: InputStream, limit: Long = MAX_BYTES, sizeHint: Long = -1): ByteArray {
        if (sizeHint > limit) throw TooLargeException()
        val initial = if (sizeHint in 0..limit) sizeHint.toInt() else 64 * 1024
        val out = ByteArrayOutputStream(maxOf(initial, 32))
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw TooLargeException()
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
