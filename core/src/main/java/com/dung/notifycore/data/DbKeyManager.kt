package com.dung.notifycore.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-device SQLCipher passphrase.
 * The passphrase itself is random per device, encrypted with an AES key that
 * never leaves the Android Keystore, and the ciphertext sits in SharedPreferences.
 * Consequence (by design): a DB file copied to another device cannot be decrypted.
 */
object DbKeyManager {
    private const val KEY_ALIAS = "notifycore_db_wrap"
    private const val PREFS = "dbkey"
    private const val K_WRAPPED = "wrapped"
    private const val K_IV = "iv"

    @Synchronized
    fun getPassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(K_WRAPPED, null)
        val iv = prefs.getString(K_IV, null)
        if (wrapped != null && iv != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, keystoreKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            return String(cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP)), Charsets.UTF_8)
        }
        val passStr = Base64.encodeToString(
            ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val ct = cipher.doFinal(passStr.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(K_WRAPPED, Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString(K_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return passStr
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}
