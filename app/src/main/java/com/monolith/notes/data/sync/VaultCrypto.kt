package com.monolith.notes.data.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {

    private val MAGIC_HEADER = "MLTH_E2EE".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_LEN_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LEN_BITS = 256

    private val secureRandom = SecureRandom()

    /**
     * Checks if a byte array starts with the Monolith E2EE magic header.
     */
    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_HEADER.size + SALT_LEN + IV_LEN + (GCM_TAG_LEN_BITS / 8)) return false
        for (i in MAGIC_HEADER.indices) {
            if (bytes[i] != MAGIC_HEADER[i]) return false
        }
        return true
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a passphrase-derived key.
     * Output format: [MAGIC_HEADER (9B)][SALT (16B)][IV (12B)][CIPHERTEXT + 16B GCM TAG]
     */
    fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotBlank()) { "Passphrase cannot be empty" }

        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }

        val key = deriveKey(passphrase.toCharArray(), salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LEN_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        cipher.updateAAD(MAGIC_HEADER)

        val ciphertext = cipher.doFinal(plaintext)

        val result = ByteArray(MAGIC_HEADER.size + SALT_LEN + IV_LEN + ciphertext.size)
        var offset = 0

        System.arraycopy(MAGIC_HEADER, 0, result, offset, MAGIC_HEADER.size)
        offset += MAGIC_HEADER.size

        System.arraycopy(salt, 0, result, offset, SALT_LEN)
        offset += SALT_LEN

        System.arraycopy(iv, 0, result, offset, IV_LEN)
        offset += IV_LEN

        System.arraycopy(ciphertext, 0, result, offset, ciphertext.size)

        return result
    }

    /**
     * Decrypts AES-256-GCM ciphertext bytes using the passphrase.
     * Throws Exception if corrupted, tampered with, or invalid password.
     */
    fun decrypt(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotBlank()) { "Passphrase cannot be empty" }
        require(isEncrypted(encryptedBytes)) { "Data does not contain valid Monolith E2EE header" }

        var offset = MAGIC_HEADER.size

        val salt = ByteArray(SALT_LEN)
        System.arraycopy(encryptedBytes, offset, salt, 0, SALT_LEN)
        offset += SALT_LEN

        val iv = ByteArray(IV_LEN)
        System.arraycopy(encryptedBytes, offset, iv, 0, IV_LEN)
        offset += IV_LEN

        val ciphertextLen = encryptedBytes.size - offset
        val ciphertext = ByteArray(ciphertextLen)
        System.arraycopy(encryptedBytes, offset, ciphertext, 0, ciphertextLen)

        val key = deriveKey(passphrase.toCharArray(), salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LEN_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.updateAAD(MAGIC_HEADER)

        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LEN_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }
}
