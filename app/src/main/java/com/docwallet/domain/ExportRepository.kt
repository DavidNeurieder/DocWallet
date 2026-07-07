package com.docwallet.domain

import com.docwallet.data.db.DocumentDao
import com.docwallet.vault.crypto.FileEncryptor
import com.docwallet.data.model.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ExportRepository(
    private val documentDao: DocumentDao,
    private val fileEncryptor: FileEncryptor,
) {

    suspend fun exportDocument(document: Document, destination: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val source = File(document.filePath)
                if (!source.exists()) return@withContext false
                source.copyTo(destination, overwrite = true)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
