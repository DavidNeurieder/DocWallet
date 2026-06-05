package com.docwallet.domain

import android.content.Context
import com.docwallet.data.db.DocumentDao
import com.docwallet.data.db.TagDao
import com.docwallet.data.encryption.EncryptionManager
import com.docwallet.data.encryption.FileEncryptor
import com.docwallet.data.model.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val tagDao: TagDao,
    private val context: Context,
    private val fileEncryptor: FileEncryptor,
    private val encryptionManager: EncryptionManager,
) {

    fun getAllDocuments(): Flow<List<Document>> = documentDao.getAllDocuments()

    suspend fun getDocumentById(id: String): Document? = documentDao.getDocumentById(id)

    suspend fun deleteDocument(document: Document) {
        withContext(Dispatchers.IO) {
            File(document.filePath).delete()
            document.thumbnailPath?.let { File(it).delete() }
            documentDao.deleteById(document.id)
        }
    }

    suspend fun toggleFavorite(document: Document) {
        val updated = document.copy(isFavorite = !document.isFavorite)
        documentDao.update(updated)
    }

    suspend fun updateDocument(document: Document) {
        documentDao.update(document)
    }

    suspend fun getDecryptedFile(document: Document): File {
        return withContext(Dispatchers.IO) {
            val masterKey = encryptionManager.getMasterKeyForSession()
                ?: throw IllegalStateException("No master key available for decryption")
            val tempFile = File(context.cacheDir, "decrypted_${document.id}_${document.fileName}")
            if (tempFile.exists()) tempFile.delete()
            val encryptedFile = File(document.filePath)
            val iv = document.encryptionIv
                ?: throw IllegalArgumentException("Document has no encryption IV")
            fileEncryptor.decrypt(encryptedFile, tempFile, masterKey, iv)
            tempFile
        }
    }

    fun getDocumentsByType(mimeType: String): Flow<List<Document>> =
        documentDao.getDocumentsByType(mimeType)

    fun getFavoriteDocuments(): Flow<List<Document>> =
        documentDao.getFavoriteDocuments()

    fun getRecentDocuments(since: Long): Flow<List<Document>> =
        documentDao.getRecentDocuments(since)

    fun searchDocuments(query: String): Flow<List<Document>> {
        return documentDao.getAllDocuments().map { documents ->
            documents.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
}
