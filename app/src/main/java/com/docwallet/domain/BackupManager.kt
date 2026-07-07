package com.docwallet.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.docwallet.data.db.DocWalletDatabase
import com.docwallet.data.encryption.EncryptionManager
import com.docwallet.vault.crypto.FileEncryptor
import com.docwallet.vault.crypto.KdfParams
import com.docwallet.vault.crypto.KeyDerivation
import com.docwallet.vault.format.VaultManifest
import com.docwallet.vault.format.VaultPackage
import com.docwallet.vault.format.VaultPackageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val getDatabase: () -> DocWalletDatabase? = { null },
) {
    private val keyDerivation = KeyDerivation()
    private val kdfParams = KdfParams()
    private val databaseMerger = DatabaseMerger(getDatabase)

    suspend fun exportBackup(destination: File, backupPassword: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val encryptionDir = File(context.filesDir, "encryption")
                val zipEntries = mutableMapOf<String, ByteArray>()

                val wrappedKey = File(encryptionDir, "wrapped_master_key")
                if (wrappedKey.exists()) {
                    zipEntries["keys/wrapped_master_key"] = wrappedKey.readBytes()
                }

                encryptionManager.resolveDeviceKeyForBackup()?.let { deviceKey ->
                    zipEntries["keys/device_key"] = deviceKey
                }

                val saltFile = File(encryptionDir, "salt")
                if (saltFile.exists()) {
                    zipEntries["keys/salt"] = saltFile.readBytes()
                }

                val dbFile = context.getDatabasePath("docwallet.db")
                if (dbFile.exists()) {
                    getDatabase()?.openHelper?.writableDatabase?.let { writableDb ->
                        writableDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                            cursor.moveToFirst()
                        }
                    }
                    zipEntries["db/docwallet.db"] = dbFile.readBytes()
                }

                val filesDir = File(context.filesDir, "files")
                if (filesDir.exists()) {
                    filesDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val entryName = "files/${file.relativeTo(filesDir).path}"
                            zipEntries[entryName] = file.readBytes()
                        }
                    }
                }

                val salt = keyDerivation.generateSalt()
                val derivedKey = keyDerivation.deriveAndZero(backupPassword, salt, kdfParams)

                try {
                    val plainZip = VaultPackage.createZipBlob(zipEntries)
                    val (iv, ciphertext) = FileEncryptor().encryptBytes(plainZip, derivedKey)
                    val encryptedBlob = iv + ciphertext

                    val documentCount = zipEntries.keys.count { it.startsWith("files/") && it != "files/" }
                    val manifest = VaultManifest(
                        version = 1,
                        kdf = "argon2id",
                        salt = java.util.Base64.getEncoder().encodeToString(salt),
                        argon2Memory = kdfParams.memoryCost,
                        argon2Iterations = kdfParams.iterations,
                        argon2Parallelism = kdfParams.parallelism,
                        documentCount = documentCount,
                    )

                    val vaultBytes = VaultPackage.write(manifest, encryptedBlob)
                    destination.writeBytes(vaultBytes)

                    Log.d(TAG, "Export complete: ${vaultBytes.size} bytes, $documentCount documents")
                    true
                } finally {
                    java.util.Arrays.fill(derivedKey, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "exportBackup failed", e)
                false
            }
        }
    }

    suspend fun importBackup(source: File, backupPassword: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val vaultData: VaultPackageData = try {
                    VaultPackage.read(source.readBytes())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read vault package, trying legacy format", e)
                    return@withContext importLegacyBackup(source, backupPassword)
                }

                val manifest = vaultData.manifest
                val saltBytes = try {
                    java.util.Base64.getDecoder().decode(manifest.salt)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid salt in manifest", e)
                    return@withContext false
                }

                val importKdfParams = KdfParams(
                    memoryCost = manifest.argon2Memory,
                    iterations = manifest.argon2Iterations,
                    parallelism = manifest.argon2Parallelism,
                )
                val derivedKey = keyDerivation.deriveAndZero(backupPassword, saltBytes, importKdfParams)

                try {
                    val encryptedBlob = vaultData.encryptedBlob
                    val iv = encryptedBlob.copyOfRange(0, 12)
                    val ciphertext = encryptedBlob.copyOfRange(12, encryptedBlob.size)
                    val plainZipBytes = FileEncryptor().decryptBytes(ciphertext, derivedKey, iv)

                    val entries = VaultPackage.readZipBlob(plainZipBytes)
                    restoreEntries(entries)

                    Log.d(TAG, "Import complete from vault format")
                    true
                } finally {
                    java.util.Arrays.fill(derivedKey, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "importBackup failed", e)
                false
            }
        }
    }

    private suspend fun restoreEntries(entries: Map<String, ByteArray>) {
        val encryptionDir = File(context.filesDir, "encryption").also { it.mkdirs() }

        entries["keys/wrapped_master_key"]?.let {
            File(encryptionDir, "wrapped_master_key").writeBytes(it)
        }
        entries["keys/device_key"]?.let {
            File(encryptionDir, "device_key").writeBytes(it)
        }
        entries["keys/salt"]?.let {
            File(encryptionDir, "salt").writeBytes(it)
        }

        var masterKey = encryptionManager.getMasterKeyForSession()
        if (masterKey == null) {
            masterKey = encryptionManager.getMasterKeyForSession()
        }

        masterKey?.let { mk ->
            entries["db/docwallet.db"]?.let { dbData ->
                val tempDb = File(context.cacheDir, "restore_db_${System.currentTimeMillis()}.db")
                try {
                    tempDb.writeBytes(dbData)
                    val currentDb = getDatabase()
                    if (currentDb != null) {
                        databaseMerger.merge(tempDb, mk)
                    } else {
                        val dbFile = context.getDatabasePath("docwallet.db")
                        dbFile.parentFile?.mkdirs()
                        tempDb.copyTo(dbFile, overwrite = true)
                    }
                } finally {
                    tempDb.delete()
                }
            }

            val filesDir = File(context.filesDir, "files").also { it.mkdirs() }
            for ((entryName, data) in entries) {
                if (entryName.startsWith("files/")) {
                    val relativePath = entryName.removePrefix("files/")
                    if (relativePath.isNotEmpty()) {
                        val targetFile = File(filesDir, relativePath)
                        targetFile.parentFile?.mkdirs()
                        if (!targetFile.exists()) {
                            targetFile.writeBytes(data)
                        }
                    }
                }
            }
        }
    }

    private suspend fun importLegacyBackup(source: File, backupPassword: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = source.readBytes()
                val salt = encryptedBytes.copyOfRange(0, 16)
                val iv = encryptedBytes.copyOfRange(16, 28)
                val ciphertext = encryptedBytes.copyOfRange(28, encryptedBytes.size)

                val derivedKey = keyDerivation.deriveAndZero(backupPassword, salt, kdfParams)

                try {
                    val plainZipBytes = FileEncryptor().decryptBytes(ciphertext, derivedKey, iv)

                    val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}")
                    tempDir.mkdirs()

                    ZipInputStream(plainZipBytes.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outputFile = File(tempDir, entry.name)
                            if (entry.isDirectory) {
                                outputFile.mkdirs()
                            } else {
                                outputFile.parentFile?.mkdirs()
                                outputFile.writeBytes(zis.readBytes())
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }

                    restoreLegacyFiles(tempDir)
                    tempDir.deleteRecursively()
                    true
                } finally {
                    java.util.Arrays.fill(derivedKey, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "importLegacyBackup failed", e)
                false
            }
        }
    }

    private suspend fun restoreLegacyFiles(tempDir: File) {
        val encryptionDir = File(context.filesDir, "encryption").also { it.mkdirs() }

        val wrappedKey = File(tempDir, "wrapped_master_key")
        if (wrappedKey.exists()) {
            wrappedKey.copyTo(File(encryptionDir, "wrapped_master_key"), overwrite = true)
        }

        val deviceKey = File(tempDir, "device_key")
        if (deviceKey.exists()) {
            deviceKey.copyTo(File(encryptionDir, "device_key"), overwrite = true)
        }

        val saltFile = File(tempDir, "salt")
        if (saltFile.exists()) {
            saltFile.copyTo(File(encryptionDir, "salt"), overwrite = true)
        }

        var masterKey = encryptionManager.getMasterKeyForSession()
        if (masterKey == null) {
            masterKey = encryptionManager.getMasterKeyForSession()
        }

        masterKey?.let { mk ->
            val tempDb = File(tempDir, "docwallet.db")
            if (tempDb.exists()) {
                val currentDb = getDatabase()
                if (currentDb != null) {
                    databaseMerger.merge(tempDb, mk)
                } else {
                    val dbFile = context.getDatabasePath("docwallet.db")
                    dbFile.parentFile?.mkdirs()
                    tempDb.copyTo(dbFile, overwrite = true)
                }
            }

            val tempFiles = File(tempDir, "files")
            if (tempFiles.exists()) {
                mergeFiles(tempFiles)
            }
        }
    }

    private fun mergeFiles(sourceDir: File) {
        val filesDir = File(context.filesDir, "files")
        filesDir.mkdirs()
        sourceDir.listFiles()?.forEach { file ->
            val dest = File(filesDir, file.name)
            if (file.isDirectory) {
                mergeFiles(file)
            } else if (!dest.exists()) {
                file.copyTo(dest)
            }
        }
    }

    suspend fun exportBackupToUri(uri: Uri, backupPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "backup_export_${System.currentTimeMillis()}.vault")
            val success = exportBackup(tempFile, backupPassword)
            if (!success) return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                tempFile.inputStream().use { it.copyTo(outputStream) }
            } ?: return@withContext false
            tempFile.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "exportBackupToUri failed", e)
            false
        }
    }

    suspend fun importBackupFromUri(uri: Uri, backupPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}.vault")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext false
            val success = importBackup(tempFile, backupPassword)
            tempFile.delete()
            success
        } catch (e: Exception) {
            Log.e(TAG, "importBackupFromUri failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "BackupManager"
    }
}
