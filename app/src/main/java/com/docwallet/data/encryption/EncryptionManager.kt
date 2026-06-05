package com.docwallet.data.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionManager(
    private val context: Context,
    private val argon2Hasher: Argon2Hasher = Argon2HasherImpl(),
) {

    companion object {
        private const val TAG = "EncryptionManager"
        private const val KEY_DIR = "encryption"
        private const val WRAPPED_KEY_FILE = "wrapped_master_key"
        private const val DEVICE_KEY_FILE = "device_key"
        private const val ENCRYPTED_DEVICE_KEY_FILE = "encrypted_device_key"
        private const val KEYSTORE_DEVICE_KEY_ALIAS = "docwallet_device_key"
        private const val SALT_FILE = "salt"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ALGORITHM = "AES"
        private const val KEY_WRAP_ALGORITHM = "AESWrap"
        private const val KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val ARGON_MEMORY_COST = 19456
        private const val ARGON_ITERATIONS = 2
        private const val ARGON_PARALLELISM = 2
    }

    private var cachedMasterKey: ByteArray? = null

    private val encryptionDir: File
        get() = File(context.filesDir, KEY_DIR).also { it.mkdirs() }

    private val wrappedKeyFile: File
        get() = File(encryptionDir, WRAPPED_KEY_FILE)

    private val deviceKeyFile: File
        get() = File(encryptionDir, DEVICE_KEY_FILE)

    private val encryptedDeviceKeyFile: File
        get() = File(encryptionDir, ENCRYPTED_DEVICE_KEY_FILE)

    private val saltFile: File
        get() = File(encryptionDir, SALT_FILE)

    fun isPasswordSet(): Boolean {
        return encryptedDeviceKeyFile.exists().not() && wrappedKeyFile.exists()
    }

    fun isFirstLaunch(): Boolean {
        return wrappedKeyFile.exists().not()
    }

    fun initializeDeviceKeyMode() {
        if (wrappedKeyFile.exists()) return

        val masterKey = generateAesKey()
        val deviceKey = generateAesKey()

        val wrappedKey = wrapKey(masterKey, deviceKey)
        wrappedKeyFile.writeBytes(wrappedKey)

        val (iv, encrypted) = encryptWithKeyStore(deviceKey)
        encryptedDeviceKeyFile.writeBytes(iv + encrypted)

        cachedMasterKey = masterKey
        Log.d(TAG, "Initialized device-key mode with KeyStore protection")
    }

    fun getMasterKeyForSession(): ByteArray? {
        cachedMasterKey?.let { return it }

        if (encryptedDeviceKeyFile.exists()) {
            val data = encryptedDeviceKeyFile.readBytes()
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val deviceKey = try {
                decryptWithKeyStore(iv, ciphertext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt device key with KeyStore", e)
                return null
            }
            val wrappedKey = wrappedKeyFile.readBytes()
            try {
                val masterKey = unwrapKey(wrappedKey, deviceKey)
                cachedMasterKey = masterKey
                return masterKey
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unwrap with KeyStore-protected device key", e)
                return null
            }
        }

        if (deviceKeyFile.exists()) {
            val deviceKey = deviceKeyFile.readBytes()
            val wrappedKey = wrappedKeyFile.readBytes()
            try {
                val masterKey = unwrapKey(wrappedKey, deviceKey)
                cachedMasterKey = masterKey
                migrateDeviceKeyToKeyStore(deviceKey)
                return masterKey
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unwrap with legacy device key", e)
                return null
            }
        }

        return null
    }

    private fun migrateDeviceKeyToKeyStore(deviceKey: ByteArray) {
        try {
            val (iv, encrypted) = encryptWithKeyStore(deviceKey)
            encryptedDeviceKeyFile.writeBytes(iv + encrypted)
            deviceKeyFile.delete()
            Log.d(TAG, "Migrated device key to KeyStore")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to migrate device key to KeyStore", e)
        }
    }

    fun setPassword(password: String): Boolean {
        try {
            val masterKey = getMasterKeyForSession() ?: return false
            val salt = generateSalt()
            val userKey = deriveKey(password, salt)

            val wrappedKey = wrapKey(masterKey, userKey)
            wrappedKeyFile.writeBytes(wrappedKey)
            saltFile.writeBytes(salt)

            if (encryptedDeviceKeyFile.exists()) encryptedDeviceKeyFile.delete()
            if (deviceKeyFile.exists()) deviceKeyFile.delete()
            deleteKeyStoreKey()

            cachedMasterKey = masterKey
            Log.d(TAG, "Password enabled")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set password", e)
            return false
        }
    }

    fun verifyPassword(password: String): Boolean {
        return try {
            val wrappedKey = wrappedKeyFile.readBytes()
            val salt = if (saltFile.exists()) saltFile.readBytes() else return false
            val userKey = deriveKey(password, salt)

            val masterKey = unwrapKey(wrappedKey, userKey)
            cachedMasterKey = masterKey
            Log.d(TAG, "Password verified")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Password verification failed", e)
            false
        }
    }

    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        return try {
            val wrappedKey = wrappedKeyFile.readBytes()
            val salt = saltFile.readBytes()
            val oldUserKey = deriveKey(oldPassword, salt)
            val masterKey = unwrapKey(wrappedKey, oldUserKey)

            val newSalt = generateSalt()
            val newUserKey = deriveKey(newPassword, newSalt)

            val newWrappedKey = wrapKey(masterKey, newUserKey)
            wrappedKeyFile.writeBytes(newWrappedKey)
            saltFile.writeBytes(newSalt)

            cachedMasterKey = masterKey
            Log.d(TAG, "Password changed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Password change failed", e)
            false
        }
    }

    fun disablePassword(): Boolean {
        return try {
            val masterKey = getMasterKeyForSession() ?: return false
            val deviceKey = generateAesKey()
            val wrappedKey = wrapKey(masterKey, deviceKey)

            wrappedKeyFile.writeBytes(wrappedKey)

            val (iv, encrypted) = encryptWithKeyStore(deviceKey)
            encryptedDeviceKeyFile.writeBytes(iv + encrypted)

            if (saltFile.exists()) saltFile.delete()
            if (deviceKeyFile.exists()) deviceKeyFile.delete()

            cachedMasterKey = masterKey
            Log.d(TAG, "Password disabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable password", e)
            false
        }
    }

    fun lock() {
        cachedMasterKey = null
        Log.d(TAG, "Session locked")
    }

    private var keyStoreAvailable: Boolean? = null

    private fun isKeyStoreAvailable(): Boolean {
        if (keyStoreAvailable == null) {
            keyStoreAvailable = try {
                KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
                true
            } catch (e: Exception) {
                Log.w(TAG, "AndroidKeyStore not available, using plaintext device key", e)
                false
            }
        }
        return keyStoreAvailable!!
    }

    private fun getOrCreateKeyStoreKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(KEYSTORE_DEVICE_KEY_ALIAS)) {
                return (keyStore.getEntry(KEYSTORE_DEVICE_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            }
            val keyGen = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_DEVICE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .build()
            )
            keyGen.generateKey()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to access AndroidKeyStore", e)
            null
        }
    }

    private fun encryptWithKeyStore(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val key = getOrCreateKeyStoreKey()
        if (key != null) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                return Pair(cipher.iv, cipher.doFinal(plaintext))
            } catch (e: Exception) {
                Log.w(TAG, "KeyStore encrypt failed, falling back", e)
            }
        }
        return Pair(ByteArray(GCM_IV_LENGTH), plaintext)
    }

    private fun decryptWithKeyStore(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        if (iv.all { it == 0.toByte() }) {
            return ciphertext
        }
        val key = getOrCreateKeyStoreKey()
        if (key != null) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                return cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                Log.w(TAG, "KeyStore decrypt failed", e)
            }
        }
        return ciphertext
    }

    private fun deleteKeyStoreKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(KEYSTORE_DEVICE_KEY_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_DEVICE_KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete KeyStore key", e)
        }
    }

    private fun generateAesKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE)
        return keyGen.generateKey().encoded
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        return argon2Hasher.hash(
            password = password.toByteArray(),
            salt = salt,
            tCostInIterations = ARGON_ITERATIONS,
            mCostInKibibyte = ARGON_MEMORY_COST,
            parallelism = ARGON_PARALLELISM,
            hashLengthInBytes = KEY_SIZE / 8,
        )
    }

    private fun wrapKey(key: ByteArray, wrappingKey: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(wrappingKey, ALGORITHM)
        val cipher = Cipher.getInstance(KEY_WRAP_ALGORITHM)
        cipher.init(Cipher.WRAP_MODE, keySpec)
        val secretKey = SecretKeySpec(key, ALGORITHM)
        return cipher.wrap(secretKey)
    }

    private fun unwrapKey(wrappedKey: ByteArray, wrappingKeyBytes: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(wrappingKeyBytes, ALGORITHM)
        val cipher = Cipher.getInstance(KEY_WRAP_ALGORITHM)
        cipher.init(Cipher.UNWRAP_MODE, keySpec)
        return cipher.unwrap(wrappedKey, ALGORITHM, Cipher.SECRET_KEY).encoded
    }
}
