package ar.cryptotest.exoplayer2

import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import ar.cryptotest.exoplayer2.MainActivity.Companion.AES_TRANSFORMATION
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

const val TAG = "ENCRYPTING PROCESS"

class BlockCipherEncryptedDataSource(
    private val secretKeySpec: SecretKeySpec,
    private val uri: Uri,
    cipherTransformation: String = AES_TRANSFORMATION
) : DataSource {
    private val cipher: Cipher = Cipher.getInstance(cipherTransformation)
    private lateinit var streamingCipherInputStream: StreamingCipherInputStream
    private var bytesRemaining: Long = 0
    private var isOpen = false
    private val transferListeners = mutableListOf<TransferListener>()
    private var dataSpec: DataSpec? = null

    @Throws(EncryptedFileDataSourceException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec

        if (isOpen) return bytesRemaining

        try {
            setupInputStream()
            streamingCipherInputStream.forceSkip(dataSpec.position)
            computeBytesRemaining(dataSpec)
        } catch (e: IOException) {
            throw EncryptedFileDataSourceException(e)
        }

        isOpen = true
        transferListeners.forEach { it.onTransferStart(this, dataSpec, false) }

        return C.LENGTH_UNSET.toLong()
    }

    private fun setupInputStream() {
        val path = uri.path ?: throw RuntimeException("Tried decrypting uri with no path: $uri")
        val encryptedFileStream = File(path).inputStream()
        val initializationVector = ByteArray(cipher.blockSize)
        encryptedFileStream.read(initializationVector)
        streamingCipherInputStream =
            StreamingCipherInputStream(
                encryptedFileStream,
                cipher,
                IvParameterSpec(initializationVector),
                secretKeySpec
            )
    }

    @Throws(IOException::class)
    private fun computeBytesRemaining(dataSpec: DataSpec) {
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = dataSpec.length
            return
        }

        if (bytesRemaining == Int.MAX_VALUE.toLong()) {
            bytesRemaining = C.LENGTH_UNSET.toLong()
            return
        }

        bytesRemaining = streamingCipherInputStream.available().toLong()
    }

    @Throws(EncryptedFileDataSourceException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (bytesRemaining == 0L) {
            Log.e(TAG, "End - No bytes remaining")
            return C.RESULT_END_OF_INPUT
        }

        val bytesRead = try {
            streamingCipherInputStream.read(buffer, offset, readLength)
        } catch (e: IOException) {
            throw EncryptedFileDataSourceException(e)
        }

        // Reading -1 means an error occurred
        if (bytesRead < 0) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong())
                throw EncryptedFileDataSourceException(EOFException())
            return C.RESULT_END_OF_INPUT
        }

        // Bytes remaining will be unset if file is too large for an int
        if (bytesRemaining != C.LENGTH_UNSET.toLong())
            bytesRemaining -= bytesRead.toLong()

        dataSpec?.let { nonNullDataSpec ->
            transferListeners.forEach {
                it.onBytesTransferred(this, nonNullDataSpec, false, bytesRead)
            }
        }
        return bytesRead
    }

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
    }

    override fun getUri(): Uri = uri

    @Throws(EncryptedFileDataSourceException::class)
    override fun close() {
        Log.e(TAG, "Closing stream")
        try {
            streamingCipherInputStream.close()
        } catch (e: IOException) {
            throw EncryptedFileDataSourceException(e)
        } finally {
            if (isOpen) {
                isOpen = false
                dataSpec?.let { nonNullDataSpec ->
                    transferListeners.forEach { it.onTransferEnd(this, nonNullDataSpec, false) }
                }
            }
        }
    }

    class EncryptedFileDataSourceException(cause: IOException?) : IOException(cause)
    class StreamingCipherInputStream(
        private val sourceStream: InputStream,
        private var cipher: Cipher,
        private val initialIvParameterSpec: IvParameterSpec,
        private val secretKeySpec: SecretKeySpec
    ) : CipherInputStream(
        sourceStream, cipher
    ) {
        private val cipherBlockSize: Int = cipher.blockSize

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len)

        fun forceSkip(bytesToSkip: Long) {
            val bytesSinceStartOfCurrentBlock = bytesToSkip % cipherBlockSize

            val bytesUntilPreviousBlockStart =
                bytesToSkip - bytesSinceStartOfCurrentBlock - cipherBlockSize

            try {
                if (bytesUntilPreviousBlockStart <= 0) {
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        secretKeySpec,
                        initialIvParameterSpec
                    )
                    return
                }

                var skipped = sourceStream.skip(bytesUntilPreviousBlockStart)
                while (skipped < bytesUntilPreviousBlockStart) {
                    sourceStream.read()
                    skipped++
                }

                val previousEncryptedBlock = ByteArray(cipherBlockSize)

                sourceStream.read(previousEncryptedBlock)

                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    IvParameterSpec(previousEncryptedBlock)
                )
                skip(bytesUntilPreviousBlockStart + cipherBlockSize)

                val discardableByteArray = ByteArray(bytesSinceStartOfCurrentBlock.toInt())
                read(discardableByteArray)
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted video skipping error", e)
                throw e
            }
        }

        // We need to return the available bytes from the upstream.
        // In this implementation we're front loading it, but it's possible the value might change during the lifetime
        // of this instance, and reference to the stream should be retained and queried for available bytes instead
        @Throws(IOException::class)
        override fun available(): Int {
            return sourceStream.available()
        }
    }
}

class BlockCipherEncryptedDataSourceFactory(
    private val secretKeySpec: SecretKeySpec,
    private val uri: Uri,
    private val cipherTransformation: String = AES_TRANSFORMATION
) : DataSource.Factory {
    override fun createDataSource(): BlockCipherEncryptedDataSource {
        return BlockCipherEncryptedDataSource(secretKeySpec, uri, cipherTransformation)
    }
}