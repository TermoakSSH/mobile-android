package com.termoak.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.termoak.ffi.generateVaultKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Key of the local vault. The Android Keystore does not store arbitrary
 * values: it keeps an AES key that encrypts the vault key, and the result goes
 * to the app preferences.
 */
object Vault {
    private const val ALIAS = "termoak-vault"
    private const val PREFS = "vault"
    private const val KEY = "key"

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun key(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { stored ->
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, data, 0, 12))
            return String(cipher.doFinal(data, 12, data.size - 12))
        }
        val fresh = generateVaultKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val sealed = cipher.iv + cipher.doFinal(fresh.toByteArray())
        prefs.edit().putString(KEY, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
        return fresh
    }
}
