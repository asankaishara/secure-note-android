package com.ishara.securenotes.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.ishara.securenotes.model.NotesJson
import com.ishara.securenotes.model.Section
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores all notes in one file, encrypted with AES-256-GCM.
 * The key lives in the Android Keystore (hardware-backed on most phones) and never leaves the device,
 * so the file is unreadable if copied elsewhere.
 */
class SecureStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "notes.enc"))

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    fun load(): List<Section> {
        if (!file.baseFile.exists()) return emptyList()
        val bytes = file.readFully()
        if (bytes.size <= IV_LENGTH) return emptyList()
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val cipherText = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val json = String(cipher.doFinal(cipherText), Charsets.UTF_8)
        return NotesJson.fromJson(json)
    }

    @Synchronized
    fun save(sections: List<Section>) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(NotesJson.toJson(sections).toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "Unexpected IV length" }

        val out = file.startWrite()
        try {
            out.write(iv)
            out.write(cipherText)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "secure_notes_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
