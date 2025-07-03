package com.aak.tilsynsapp

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit


object SecurePrefs {
    private const val PREF_NAME = "secure_prefs"
    //private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "tilsyn_key"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_SEPARATOR = "]"

    private const val KEY_API = "api_key"
    private const val KEY_TOKEN = "login_token"
    //private const val KEY_EMAIL = "user_email"
    private const val KEY_LOGIN_TIME = "login_time"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        // If key already exists, use it
        keyStore.getKey(KEY_ALIAS, null)?.let {
            return it as SecretKey
        }

        val keyGenParams = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(keyGenParams)

        return keyGenerator.generateKey()
    }


    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charset.defaultCharset()))

        return Base64.encodeToString(iv, Base64.NO_WRAP) +
                IV_SEPARATOR +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String? {
        return try {
            val parts = data.split(IV_SEPARATOR)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

            val plaintext = cipher.doFinal(ciphertext)
            String(plaintext, Charset.defaultCharset())
        } catch (e: Exception) {
            null
        }
    }

    private fun saveEncrypted(context: Context, key: String, value: String) {
        val enc = encrypt(value)
        prefs(context).edit { putString(key, enc) }
    }

    private fun loadDecrypted(context: Context, key: String): String? {
        return prefs(context).getString(key, null)?.let { decrypt(it) }
    }

    fun saveApiKey(context: Context, key: String) = saveEncrypted(context, KEY_API, key)
    fun getApiKey(context: Context): String? = loadDecrypted(context, KEY_API)

    fun saveToken(context: Context, token: String) = saveEncrypted(context, KEY_TOKEN, token)
    fun getToken(context: Context): String? = loadDecrypted(context, KEY_TOKEN)

    // fun saveEmail(context: Context, email: String) = saveEncrypted(context, KEY_EMAIL, email)
    // fun getEmail(context: Context): String? = loadDecrypted(context, KEY_EMAIL)

    fun saveLoginTimestamp(context: Context) {
        prefs(context).edit { putLong(KEY_LOGIN_TIME, System.currentTimeMillis()) }
    }
    fun isLoginExpired(context: Context, maxAgeMillis: Long = 90L * 24 * 60 * 60 * 1000): Boolean {
        val lastLogin = prefs(context).getLong(KEY_LOGIN_TIME, 0L)
        return System.currentTimeMillis() - lastLogin > maxAgeMillis
    }

    fun clearAll(context: Context) {
        prefs(context).edit { clear() }
    }
}
