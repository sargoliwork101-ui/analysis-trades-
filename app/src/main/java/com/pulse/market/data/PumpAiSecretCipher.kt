package com.pulse.market.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** رمزگذاری API Key با کلید سخت‌افزاری/سیستمی Android Keystore؛ کلید رمز از دستگاه خارج نمی‌شود. */
object PumpAiSecretCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "pulse_pump_ai_api_key_v1"
    private const val PREFIX = "aesgcm1:"

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$PREFIX$iv:$encrypted"
    }

    fun decrypt(encoded: String): String? {
        if (encoded.isEmpty()) return ""
        if (!encoded.startsWith(PREFIX) || encoded.length > 4096) return null
        return runCatching {
            val pieces = encoded.removePrefix(PREFIX).split(':', limit = 2)
            if (pieces.size != 2) return@runCatching null
            val iv = Base64.decode(pieces[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(pieces[1], Base64.NO_WRAP)
            if (iv.size !in 12..16 || encrypted.size !in 16..2048) return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
