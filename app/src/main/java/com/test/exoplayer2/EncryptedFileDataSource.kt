package com.test.exoplayer2

import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import com.test.exoplayer2.MainActivity.Companion.AES_TRANSFORMATION
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

const val TAG = "ENCRYPTING PROCESS"

/**
 * Created by michaeldunn on 3/13/17.
 */
class EncryptedFileDataSource(
    private var mCipher: Cipher,
    private val mSecretKeySpec: SecretKeySpec,
    private var mTransferListener: TransferListener
) : DataSource {
    private var mInputStream: StreamingCipherInputStream? = null
    private var mUri: Uri? = null
    private var mBytesRemaining: Long = 0
    private var mOpened = false

    @Throws(EncryptedFileDataSourceException::class)
    override fun open(dataSpec: DataSpec): Long {
        // if we're open, we shouldn't need to open again, fast-fail
        if (mOpened) {
            Log.e(TAG, "Opening an opened datasource with - $mBytesRemaining")
            return mBytesRemaining
        }

        // #getUri is part of the contract...
        mUri = dataSpec.uri
        // put all our throwable work in a single block, wrap the error in a custom Exception
        try {
            mCipher = Cipher.getInstance(AES_TRANSFORMATION)
            setupInputStream()
            Log.e(TAG, "Opening stream - available: ${mInputStream?.available()}")
            skipToPosition(dataSpec)
            computeBytesRemaining(dataSpec)
        } catch (e: IOException) {
            throw EncryptedFileDataSourceException(e)
        }
        // if we made it this far, we're open
        mOpened = true
        // notify
        // mTransferListener.onTransferStart(this, dataSpec)
        // report
        return C.LENGTH_UNSET.toLong()
    }

    @Throws(FileNotFoundException::class)
    private fun setupInputStream() {
        val encryptedFileStream = File(mUri!!.path!!).inputStream()
        mInputStream = StreamingCipherInputStream(encryptedFileStream, mCipher, mSecretKeySpec)
    }

    @Throws(IOException::class)
    private fun skipToPosition(dataSpec: DataSpec) {
        mInputStream!!.forceSkip(dataSpec.position)
    }

    @Throws(IOException::class)
    private fun computeBytesRemaining(dataSpec: DataSpec) {
        Log.e(TAG, "Compute bytes remaining start - $mBytesRemaining")
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            mBytesRemaining = dataSpec.length
        } else {
            Log.e(TAG, "Compute bytes available: ${mInputStream!!.available()}")

            mBytesRemaining = mInputStream!!.available().toLong()
            if (mBytesRemaining == Int.MAX_VALUE.toLong()) {
                mBytesRemaining = C.LENGTH_UNSET.toLong()
            }
        }
        Log.e(TAG, "Compute bytes remaining end - $mBytesRemaining")
    }

    @Throws(EncryptedFileDataSourceException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        // Log.e(TAG, "DataSource read - length: $readLength")
        // fast-fail if there's 0 quantity requested or we think we've already processed everything
        // if (readLength == 0) {
        //     return 0
        // } else if (mBytesRemaining == 0L) {
        //     Log.e(TAG, "End - No bytes remaining")
        //     return C.RESULT_END_OF_INPUT
        // }
        // constrain the read length and try to read from the cipher input stream
        val bytesToRead = getBytesToRead(readLength)
        // Log.e(TAG, "DataSource read - toRead: $bytesToRead")

        val bytesRead: Int
        bytesRead = try {
            mInputStream!!.read(buffer, offset, bytesToRead)
        } catch (e: IOException) {
            throw EncryptedFileDataSourceException(e)
        }
        // if we get a -1 that means we failed to read - we're either going to EOF error or broadcast EOF
        if (bytesRead < 0) {
            if (mBytesRemaining != C.LENGTH_UNSET.toLong()) {
                throw EncryptedFileDataSourceException(EOFException())
            }
            Log.e(TAG, "Failure to read")
            return C.RESULT_END_OF_INPUT
        }
        // we can't decrement bytes remaining if it's just a flag representation (as opposed to a mutable numeric quantity)
        if (mBytesRemaining != C.LENGTH_UNSET.toLong())
            mBytesRemaining -= bytesRead.toLong()
        // notify
        // mTransferListener.onBytesTransferred(this, bytesRead)
        // report
        return bytesRead
    }

    override fun addTransferListener(transferListener: TransferListener) {
        this.mTransferListener = transferListener
    }

    private fun getBytesToRead(bytesToRead: Int): Int {
        return if (mBytesRemaining == C.LENGTH_UNSET.toLong()) {
            bytesToRead
        } else mBytesRemaining.coerceAtMost(bytesToRead.toLong()).toInt()
    }

    override fun getUri(): Uri {
        return mUri!!
    }

    @Throws(EncryptedFileDataSourceException::class)
    override fun close() {
        Log.e(TAG, "Closing stream")
        // mBytesRemaining = 0
        mUri = null
        try {
            if (mInputStream != null) {
                mInputStream!!.close()
            }
        } catch (e: IOException) {
            throw EncryptedFileDataSourceException(e)
        } finally {
            mInputStream = null
            if (mOpened) {
                mOpened = false
                // mTransferListener.onTransferEnd(this)
            }
        }
    }

    class EncryptedFileDataSourceException(cause: IOException?) : IOException(cause)
    class StreamingCipherInputStream(
        private val mUpstream: InputStream,
        private var mCipher: Cipher,
        private val mSecretKeySpec: SecretKeySpec
    ) : CipherInputStream(
        mUpstream, mCipher
    ) {
        private val cipherBlockSize: Int = mCipher.blockSize

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len)

        fun forceSkip(bytesToSkip: Long) {
            Log.e(TAG, "Skipping - bytesToSkip: $bytesToSkip")

            val bytesSinceStartOfCurrentBlock = bytesToSkip % cipherBlockSize

            val bytesUntilPreviousBlockStart =
                bytesToSkip - bytesSinceStartOfCurrentBlock - cipherBlockSize
            try {
                Log.e(
                    TAG,
                    "Skipping - bytes until previous block start: $bytesUntilPreviousBlockStart"
                )

                if (bytesUntilPreviousBlockStart > 0) {
                    var skipped = mUpstream.skip(bytesUntilPreviousBlockStart)
                    while (skipped < bytesUntilPreviousBlockStart) {
                        mUpstream.read()
                        skipped++
                    }
                    Log.e(TAG, "Skipping - skipped: $skipped")

                    val ivFromPreviousBlock = ByteArray(cipherBlockSize)

                    mUpstream.read(ivFromPreviousBlock)

                    mCipher.init(
                        Cipher.DECRYPT_MODE,
                        mSecretKeySpec,
                        IvParameterSpec(ivFromPreviousBlock)
                    )
                    skip(bytesUntilPreviousBlockStart + cipherBlockSize)

                    val discardByteArray = ByteArray(bytesSinceStartOfCurrentBlock.toInt())
                    read(discardByteArray)

                    Log.e(TAG, "Available after skip: ${mUpstream.available()}")
                } else {
                    mCipher.init(
                        Cipher.DECRYPT_MODE,
                        mSecretKeySpec,
                        IvParameterSpec(MainActivity.initialIv)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Skip error", e)
            }
        }

        // We need to return the available bytes from the upstream.
        // In this implementation we're front loading it, but it's possible the value might change during the lifetime
        // of this instance, and reference to the stream should be retained and queried for available bytes instead
        @Throws(IOException::class)
        override fun available(): Int {
            return mUpstream.available()
        }
    }
}