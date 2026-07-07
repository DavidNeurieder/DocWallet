package com.docwallet.data.encryption

import android.content.Context
import android.util.Log
import com.docwallet.vault.crypto.AesKeyGenerator
import com.docwallet.vault.crypto.Argon2Hasher
import com.docwallet.vault.crypto.Argon2HasherImpl
import com.docwallet.vault.crypto.KdfParams
import com.docwallet.vault.crypto.KeyDerivation
import com.docwallet.vault.crypto.KeyManager
import com.docwallet.vault.crypto.KeyWrap
import java.io.File
import java.util.Arrays

class EncryptionManager(
    private val context: Context,
    private val argon2Hasher: Argon2Hasher = Argon2HasherImpl(),
    internal val keyStoreCryptographer: KeyStoreCryptographer = AndroidKeyStoreCryptographer(),
) : KeyManager {

    private val keyDerivation = KeyDerivation(argon2Hasher)
    private val kdfParams = KdfParams()

    companion object {
        private const val TAG = "EncryptionManager"
        private const val KEY_DIR = "encryption"
        private const val WRAPPED_KEY_FILE = "wrapped_master_key"
        private const val DEVICE_KEY_FILE = "device_key"
        private const val ENCRYPTED_DEVICE_KEY_FILE = "encrypted_device_key"
        private const val SALT_FILE = "salt"
        private const val GCM_IV_LENGTH = 12
    }

    @Volatile private var cachedMasterKey: ByteArray? = null

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

    override fun isPasswordSet(): Boolean {
        return encryptedDeviceKeyFile.exists().not() && wrappedKeyFile.exists()
    }

    override fun isFirstLaunch(): Boolean {
        return wrappedKeyFile.exists().not()
    }

    override fun initializeDeviceKeyMode() {
        if (wrappedKeyFile.exists()) return

        val masterKey = AesKeyGenerator.generateKey()
        val deviceKey = AesKeyGenerator.generateKey()

        try {
            val wrappedKey = KeyWrap.wrap(masterKey, deviceKey)
            wrappedKeyFile.writeBytes(wrappedKey)
            val (iv, encrypted) = keyStoreCryptographer.encrypt(deviceKey)
            encryptedDeviceKeyFile.writeBytes(iv + encrypted)
            cachedMasterKey = masterKey
            Log.d(TAG, "Initialized device-key mode with KeyStore protection")
        } finally {
            Arrays.fill(deviceKey, 0)
        }
    }

    @Synchronized
    override fun getMasterKeyForSession(): ByteArray? {
        cachedMasterKey?.let { return it.copyOf() }

        if (encryptedDeviceKeyFile.exists()) {
            val data = encryptedDeviceKeyFile.readBytes()
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val deviceKey = try {
                keyStoreCryptographer.decrypt(iv, ciphertext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt device key with KeyStore", e)
                return null
            }
            val wrappedKey = wrappedKeyFile.readBytes()
            try {
                val masterKey = KeyWrap.unwrap(wrappedKey, deviceKey)
                cachedMasterKey = masterKey
                return masterKey.copyOf()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unwrap with KeyStore-protected device key", e)
                return null
            }
        }

        if (deviceKeyFile.exists()) {
            val deviceKey = deviceKeyFile.readBytes()
            val wrappedKey = wrappedKeyFile.readBytes()
            try {
                val masterKey = KeyWrap.unwrap(wrappedKey, deviceKey)
                cachedMasterKey = masterKey
                migrateDeviceKeyToKeyStore(deviceKey)
                return masterKey.copyOf()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unwrap with legacy device key", e)
                return null
            }
        }

        return null
    }

    private fun migrateDeviceKeyToKeyStore(deviceKey: ByteArray) {
        try {
            val (iv, encrypted) = keyStoreCryptographer.encrypt(deviceKey)
            encryptedDeviceKeyFile.writeBytes(iv + encrypted)
            deviceKeyFile.delete()
            Log.d(TAG, "Migrated device key to KeyStore")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to migrate device key to KeyStore", e)
        }
    }

    fun resolveDeviceKeyForBackup(): ByteArray? {
        if (encryptedDeviceKeyFile.exists()) {
            val data = encryptedDeviceKeyFile.readBytes()
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
            return try {
                keyStoreCryptographer.decrypt(iv, ciphertext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt device key for backup", e)
                null
            }
        }
        if (deviceKeyFile.exists()) {
            return deviceKeyFile.readBytes()
        }
        return null
    }

    override fun setPassword(password: String): Boolean {
        try {
            val masterKey = getMasterKeyForSession() ?: return false
            val salt = keyDerivation.generateSalt()
            val userKey = keyDerivation.deriveAndZero(password, salt, kdfParams)

            try {
                val wrappedKey = KeyWrap.wrap(masterKey, userKey)
                wrappedKeyFile.writeBytes(wrappedKey)
                saltFile.writeBytes(salt)

                if (encryptedDeviceKeyFile.exists()) encryptedDeviceKeyFile.delete()
                if (deviceKeyFile.exists()) deviceKeyFile.delete()
                keyStoreCryptographer.deleteKey()

                cachedMasterKey = masterKey
                Log.d(TAG, "Password enabled")
                return true
            } finally {
                Arrays.fill(userKey, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set password", e)
            return false
        }
    }

    override fun verifyPassword(password: String): Boolean {
        return try {
            val wrappedKey = wrappedKeyFile.readBytes()
            val salt = if (saltFile.exists()) saltFile.readBytes() else return false
            val userKey = keyDerivation.deriveAndZero(password, salt, kdfParams)

            try {
                val masterKey = KeyWrap.unwrap(wrappedKey, userKey)
                cachedMasterKey = masterKey
                Log.d(TAG, "Password verified")
                true
            } finally {
                Arrays.fill(userKey, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Password verification failed", e)
            false
        }
    }

    override fun changePassword(oldPassword: String, newPassword: String): Boolean {
        return try {
            val wrappedKey = wrappedKeyFile.readBytes()
            val salt = saltFile.readBytes()
            val oldUserKey = keyDerivation.deriveAndZero(oldPassword, salt, kdfParams)
            val masterKey = KeyWrap.unwrap(wrappedKey, oldUserKey)

            val newSalt = keyDerivation.generateSalt()
            val newUserKey = keyDerivation.deriveAndZero(newPassword, newSalt, kdfParams)

            try {
                val newWrappedKey = KeyWrap.wrap(masterKey, newUserKey)
                wrappedKeyFile.writeBytes(newWrappedKey)
                saltFile.writeBytes(newSalt)
                cachedMasterKey = masterKey
                Log.d(TAG, "Password changed")
                true
            } finally {
                Arrays.fill(oldUserKey, 0)
                Arrays.fill(newUserKey, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Password change failed", e)
            false
        }
    }

    override fun disablePassword(): Boolean {
        return try {
            val masterKey = getMasterKeyForSession() ?: return false
            val deviceKey = AesKeyGenerator.generateKey()

            try {
                val wrappedKey = KeyWrap.wrap(masterKey, deviceKey)
                wrappedKeyFile.writeBytes(wrappedKey)

                val (iv, encrypted) = keyStoreCryptographer.encrypt(deviceKey)
                encryptedDeviceKeyFile.writeBytes(iv + encrypted)

                if (saltFile.exists()) saltFile.delete()
                if (deviceKeyFile.exists()) deviceKeyFile.delete()

                cachedMasterKey = masterKey
                Log.d(TAG, "Password disabled")
                true
            } finally {
                Arrays.fill(deviceKey, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable password", e)
            false
        }
    }

    @Synchronized
    override fun lock() {
        cachedMasterKey?.let { Arrays.fill(it, 0) }
        cachedMasterKey = null
        Log.d(TAG, "Session locked")
    }
}
