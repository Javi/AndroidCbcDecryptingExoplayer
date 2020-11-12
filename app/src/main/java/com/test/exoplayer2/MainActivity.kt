package com.test.exoplayer2

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.DefaultRenderersFactory.ExtensionRendererMode
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import java.io.File
import java.util.jar.Manifest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {
    private var mCipher: Cipher? = null
    private var mSecretKeySpec: SecretKeySpec? = null
    private var mIvParameterSpec: IvParameterSpec? = null
    private var mEncryptedFile: File? = null
    private var mSimpleExoPlayerView: PlayerView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mSimpleExoPlayerView = findViewById(R.id.simpleexoplayerview)
        mEncryptedFile = File(filesDir, ENCRYPTED_FILE_NAME)
        mSecretKeySpec = SecretKeySpec(secretKey, AES_ALGORITHM)
        mIvParameterSpec = IvParameterSpec(initialIv)
        try {
            mCipher = Cipher.getInstance(AES_TRANSFORMATION)
            mCipher!!.init(Cipher.DECRYPT_MODE, mSecretKeySpec, mIvParameterSpec)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasFile(): Boolean {
        return (mEncryptedFile != null && mEncryptedFile!!.exists()
            && mEncryptedFile!!.length() > 0)
    }

    fun encryptVideo(view: View) {
        if (hasFile()) {
            Log.d(javaClass.canonicalName, "encrypted file found, no need to recreate")
            return
        }
        try {
            val encryptionCipher = Cipher.getInstance(AES_TRANSFORMATION)
            encryptionCipher.init(Cipher.ENCRYPT_MODE, mSecretKeySpec, mIvParameterSpec)
            DownloadAndEncryptFileTask(
                // "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4",
                "https://file-examples-com.github.io/uploads/2017/04/file_example_MP4_480_1_5MG.mp4",
                mEncryptedFile,
                encryptionCipher
            ).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playVideo(view: View) {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(view.context).build()

        val player = SimpleExoPlayer.Builder(view.context).build()
        mSimpleExoPlayerView!!.player = player

        val dataSourceFactory: DataSource.Factory = EncryptedFileDataSourceFactory(
            mCipher!!,
            mSecretKeySpec!!,
            bandwidthMeter
        )
        val extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory()
        try {
            val uri = Uri.fromFile(mEncryptedFile)
            val videoSource: MediaSource =
                ExtractorMediaSource(uri, dataSourceFactory, extractorsFactory, null, null)
            player.setMediaSource(videoSource, true)
            player.seekTo(5000)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val AES_ALGORITHM = "AES"
        const val AES_TRANSFORMATION = "AES/CBC/PKCS7Padding"
        private const val ENCRYPTED_FILE_NAME = "encrypted.mp4"

        val secretKey = byteArrayOf(
            'c'.toByte(),
            'o'.toByte(),
            'd'.toByte(),
            'i'.toByte(),
            'n'.toByte(),
            'g'.toByte(),
            'a'.toByte(),
            'f'.toByte(),
            'f'.toByte(),
            'a'.toByte(),
            'i'.toByte(),
            'r'.toByte(),
            's'.toByte(),
            'c'.toByte(),
            'o'.toByte(),
            'm'.toByte()
        )
        var initialIv = byteArrayOf(
            'l'.toByte(),
            'o'.toByte(),
            'd'.toByte(),
            'i'.toByte(),
            'n'.toByte(),
            'g'.toByte(),
            'a'.toByte(),
            'f'.toByte(),
            'f'.toByte(),
            'a'.toByte(),
            'i'.toByte(),
            'r'.toByte(),
            's'.toByte(),
            'c'.toByte(),
            'o'.toByte(),
            'f'.toByte()
        )
    }
}