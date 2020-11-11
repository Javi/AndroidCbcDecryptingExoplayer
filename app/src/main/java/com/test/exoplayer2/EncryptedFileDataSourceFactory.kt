package com.test.exoplayer2

import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.TransferListener
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Created by michaeldunn on 3/13/17.
 */
class EncryptedFileDataSourceFactory(
    private val mCipher: Cipher,
    private val mSecretKeySpec: SecretKeySpec,
    private val mTransferListener: TransferListener
) : DataSource.Factory {
    override fun createDataSource(): EncryptedFileDataSource {
        return EncryptedFileDataSource(mCipher, mSecretKeySpec, mTransferListener)
    }
}