package com.test.exoplayer2

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class SkipInputStream
/**
 * Creates a `FilterInputStream`
 * by assigning the  argument `in`
 * to the field `this.in` so as
 * to remember it for later use.
 *
 * @param `in` the underlying input stream, or `null` if
 * this instance is to be created without an underlying stream.
 */
constructor(inputStream: InputStream?) : FilterInputStream(inputStream) {
    /**
     * Same implementation as InputStream#skip
     *
     * @param n
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        var remaining = n
        var nr: Int
        if (n <= 0) {
            return 0
        }
        val size = MAX_SKIP_BUFFER_SIZE.toLong().coerceAtMost(remaining).toInt()
        val skipBuffer = ByteArray(size)
        while (remaining > 0) {
            nr = `in`.read(skipBuffer, 0, size.toLong().coerceAtMost(remaining).toInt())
            if (nr < 0) {
                break
            }
            remaining -= nr.toLong()
        }
        return n - remaining
    }

    companion object {
        private const val MAX_SKIP_BUFFER_SIZE = 2048
    }
}