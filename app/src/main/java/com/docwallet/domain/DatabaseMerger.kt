package com.docwallet.domain

import android.content.Context
import android.util.Log
import com.docwallet.data.db.DocWalletDatabase
import com.docwallet.vault.database.SqlCipherOpener
import com.docwallet.vault.database.SqlHandleSupportAndroid
import com.docwallet.vault.database.VaultDatabase
import com.docwallet.vault.database.VaultDatabaseMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DatabaseMerger(
    private val context: Context,
    private val getDatabase: () -> DocWalletDatabase?,
    private val vaultMerger: VaultDatabaseMerger = VaultDatabaseMerger(),
) {
    suspend fun merge(backupDbFile: File, masterKey: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            val currentDb = getDatabase() ?: return@withContext false
            val opener = SqlCipherOpener(context, masterKey)

            VaultDatabase(opener.open(backupDbFile.absolutePath)).use { backupVault ->
                try {
                    val currentHandle = SqlHandleSupportAndroid(currentDb.openHelper.writableDatabase)
                    val result = backupVault.mergeFrom(currentHandle)
                    if (result) {
                        Log.d(TAG, "Database merge completed")
                    } else {
                        Log.e(TAG, "Database merge failed")
                    }
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Database merge failed", e)
                    false
                }
            }
        }
    }

    companion object {
        private const val TAG = "DatabaseMerger"
    }
}
