package com.test.exoplayer2

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import kotlinx.coroutines.launch
import java.io.File
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {
    private var mCipher: Cipher? = null
    private val secretKeySpec: SecretKeySpec get() = SecretKeySpec(secretKey, AES_ALGORITHM)
    private val ivParameterSpec: IvParameterSpec? get() = IvParameterSpec(initialIv)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            mCipher = Cipher.getInstance(AES_TRANSFORMATION)
            mCipher!!.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun encryptVideo(view: View) {
        lifecycleScope.launch {
            val encryptionCipher = Cipher.getInstance(AES_TRANSFORMATION)
            encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            encryptFile(encryptionCipher)
        }
    }

    fun playVideo(view: View) {
        val player = SimpleExoPlayer.Builder(view.context).build()
        findViewById<PlayerView>(R.id.simpleexoplayerview).player = player

        val uri = Uri.fromFile(encryptedFile)
        val dataSourceFactory: DataSource.Factory = BlockCipherEncryptedDataSourceFactory(
            secretKeySpec,
            uri,
            AES_TRANSFORMATION
        )
        try {
            val videoSource =
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(
                    MediaItem.Builder().setUri(uri).build()
                )
            player.setMediaSource(videoSource)
            // player.seekTo(28000)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val AES_ALGORITHM = "AES"
        const val AES_TRANSFORMATION = "AES/CBC/PKCS7Padding"

        val secretKey = "85BE62F9AC34D107".map { it.toByte() }.toByteArray()
        var initialIv = "1234567890ABCDEF".map { it.toByte() }.toByteArray()
    }

    private val encryptedFile get() = File(filesDir, "encrypted.mp4")

    private fun encryptFile(cipher: Cipher) {
        val unencryptedFile = File(filesDir, "unencrypted.mp4")
        encryptedFile.delete()

        unencryptedFile.inputStream().use { unencryptedFileInputStream ->
            encryptedFile.outputStream().use { fileOutputStream ->
                fileOutputStream.write(initialIv)

                CipherOutputStream(fileOutputStream, cipher).use { cipherOutputStream ->
                    unencryptedFileInputStream.copyTo(cipherOutputStream)
                }
            }
        }

        Log.d(TAG, "File encrypted successfully.")
    }
}

