package com.monolith.notes

import com.monolith.notes.data.sync.VaultCrypto
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class VaultCryptoTest {

    @Test
    fun `test AES-256-GCM encryption and decryption roundtrip`() {
        val originalText = "# Secret Vault Note\n\nConfidential research data that must remain zero-knowledge."
        val passphrase = "SuperSecureVaultMasterKey123!@#"

        val encrypted = VaultCrypto.encrypt(originalText.toByteArray(Charsets.UTF_8), passphrase)

        // 1. Should be detected as encrypted
        assertTrue(VaultCrypto.isEncrypted(encrypted))

        // 2. Plaintext should NOT be visible in encrypted bytes
        val encryptedStr = String(encrypted, Charsets.ISO_8859_1)
        assertFalse(encryptedStr.contains("Confidential"))

        // 3. Decrypt with correct passphrase
        val decryptedBytes = VaultCrypto.decrypt(encrypted, passphrase)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(originalText, decryptedText)
    }

    @Test
    fun `test wrong passphrase fails authentication tag verification`() {
        val originalText = "Sensitive financial records"
        val correctPass = "CorrectPassword123"
        val wrongPass = "WrongPassword456"

        val encrypted = VaultCrypto.encrypt(originalText.toByteArray(Charsets.UTF_8), correctPass)

        try {
            VaultCrypto.decrypt(encrypted, wrongPass)
            fail("Decryption with wrong password should fail with AEADBadTagException")
        } catch (_: AEADBadTagException) {
            // Expected
        } catch (e: Exception) {
            // Can be generic security exception wrapping bad tag
            assertTrue(e.message?.contains("Tag", ignoreCase = true) == true || e.cause is AEADBadTagException)
        }
    }

    @Test
    fun `test tampering with ciphertext fails decryption`() {
        val originalText = "Uncompromised Data"
        val passphrase = "MasterPassphrase"

        val encrypted = VaultCrypto.encrypt(originalText.toByteArray(Charsets.UTF_8), passphrase)

        // Flip one bit in the middle of ciphertext
        val tampered = encrypted.clone()
        val tamperIndex = tampered.size - 5
        tampered[tamperIndex] = (tampered[tamperIndex].toInt() xor 0xFF).toByte()

        try {
            VaultCrypto.decrypt(tampered, passphrase)
            fail("Decryption of tampered ciphertext should fail")
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun `test plaintext is not recognized as encrypted`() {
        val rawMarkdown = "# Normal Note\n\nJust plain text markdown.".toByteArray(Charsets.UTF_8)
        assertFalse(VaultCrypto.isEncrypted(rawMarkdown))
    }
}
