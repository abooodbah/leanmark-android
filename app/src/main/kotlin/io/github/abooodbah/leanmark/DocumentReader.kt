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
     * With a declared [sizeHint], the file is read straight into an array of
     * that size, so it is held once instead of in a buffer and a copy.
     */
    fun readLimited(input: InputStream, limit: Long = MAX_BYTES, sizeHint: Long = -1): ByteArray {
        if (sizeHint > limit) throw TooLargeException()
        if (sizeHint < 0) return readGrowing(input, limit, ByteArray(0), 64 * 1024)
        val exact = ByteArray(sizeHint.toInt())
        var filled = 0
        while (filled < exact.size) {
            val read = input.read(exact, filled, exact.size - filled)
            if (read < 0) return exact.copyOf(filled) // Shorter than declared.
            filled += read
        }
        val next = input.read()
        if (next < 0) return exact
        // Longer than declared: keep what was read and go on as for no size.
        if (exact.size + 1L > limit) throw TooLargeException()
        return readGrowing(input, limit, exact + next.toByte(), exact.size + 64 * 1024)
    }

    private fun readGrowing(input: InputStream, limit: Long, start: ByteArray, capacity: Int): ByteArray {
        val out = ByteArrayOutputStream(maxOf(capacity, 32))
        out.write(start)
        val buffer = ByteArray(64 * 1024)
        var total = start.size.toLong()
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
