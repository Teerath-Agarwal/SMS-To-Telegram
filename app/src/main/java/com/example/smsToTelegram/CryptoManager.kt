package com.example.smsToTelegram

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles hardware-backed encryption using the Android Keystore System.
 * This avoids using the deprecated security-crypto library.
 */
object CryptoManager {

    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    private const val KEY_ALIAS = "secret_relay_key"

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    fun encrypt(bytes: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(bytes)
        
        // Combine IV and Ciphertext for storage
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    fun decrypt(base64String: String): ByteArray {
        val combined = Base64.decode(base64String, Base64.DEFAULT)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        // GCM IV is always 12 bytes
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Build-time de-obfuscation for the seeding phase.
     */
    fun deobfuscate(obfuscatedBase64: String): String {
        val decoded = String(Base64.decode(obfuscatedBase64, Base64.DEFAULT))
        val key = "dVNHpXazVOYmtKU1RraGtUVTlITlZOTldIQlZUbTFLV2sx"
        val sb = StringBuilder()
        for (i in decoded.indices) {
            sb.append((decoded[i].code xor key[i % key.length].code).toChar())
        }
        return sb.toString()
    }
}
