package com.docwallet.data.search

import android.database.Cursor
import com.docwallet.data.db.DocWalletDatabase
import com.docwallet.data.db.DocumentDao
import com.docwallet.data.model.Document
import com.docwallet.vault.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class SearchEngine(
    private val documentDao: DocumentDao,
    private val database: DocWalletDatabase
) {

    fun search(query: String, filterType: DocumentType? = null): Flow<List<Document>> {
        if (query.isBlank()) {
            return if (filterType != null) {
                documentDao.getDocumentsByType(filterType.mimeType.replace("/*", "/%"))
            } else {
                documentDao.getAllDocuments()
            }
        }

        return flow {
            val results = withContext(Dispatchers.IO) {
                if (query.length < 3) {
                    searchSimple(query, filterType)
                } else {
                    searchFts(query, filterType)
                }
            }
            emit(results)
        }
    }

    fun searchByType(mimeType: String): Flow<List<Document>> {
        return documentDao.getDocumentsByType(mimeType)
    }

    fun getSuggestions(prefix: String): Flow<List<String>> = flow {
        val results = withContext(Dispatchers.IO) {
            val cursor = database.openHelper.writableDatabase.query(
                "SELECT DISTINCT title FROM documents WHERE title LIKE ? ORDER BY title LIMIT 10",
                arrayOf("$prefix%")
            )
            val titles = mutableListOf<String>()
            while (cursor.moveToNext()) {
                titles.add(cursor.getString(cursor.getColumnIndexOrThrow("title")))
            }
            cursor.close()
            titles
        }
        emit(results)
    }

    private fun searchSimple(query: String, filterType: DocumentType?): List<Document> {
        val sql = buildString {
            append("SELECT * FROM documents WHERE title LIKE ?")
            if (filterType != null) {
                append(" AND mime_type LIKE ?")
            }
            append(" ORDER BY imported_at DESC")
        }
        val args = mutableListOf("%$query%")
        if (filterType != null) {
            args.add(filterType.mimeType.replace("/*", "/%"))
        }
        val cursor = database.openHelper.writableDatabase.query(sql, args.toTypedArray())
        return cursorToDocuments(cursor)
    }

    private fun searchFts(query: String, filterType: DocumentType?): List<Document> {
        val sanitized = sanitizeFtsQuery(query)
        val sql = buildString {
            append("SELECT d.* FROM documents d INNER JOIN documents_fts fts ON d.rowid = fts.rowid WHERE documents_fts MATCH ?")
            if (filterType != null) {
                append(" AND d.mime_type LIKE ?")
            }
            append(" ORDER BY rank")
        }
        val args = mutableListOf(sanitized)
        if (filterType != null) {
            args.add(filterType.mimeType.replace("/*", "/%"))
        }
        val cursor = database.openHelper.writableDatabase.query(sql, args.toTypedArray())
        return cursorToDocuments(cursor)
    }

    private fun sanitizeFtsQuery(query: String): String {
        return query.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "${it}*" }
    }

    private fun cursorToDocuments(cursor: Cursor): List<Document> {
        val documents = mutableListOf<Document>()
        while (cursor.moveToNext()) {
            documents.add(
                Document(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                    filePath = cursor.getString(cursor.getColumnIndexOrThrow("file_path")),
                    fileSize = cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
                    pageCount = cursor.getInt(cursor.getColumnIndexOrThrow("page_count")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    thumbnailPath = getStringOrNull(cursor, "thumbnail_path"),
                    importedAt = cursor.getLong(cursor.getColumnIndexOrThrow("imported_at")),
                    lastOpenedAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_opened_at")),
                    isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) != 0,
                    collectionId = getStringOrNull(cursor, "collection_id"),
                    encryptionIv = cursor.getBlob(cursor.getColumnIndexOrThrow("encryption_iv")),
                    textContent = getStringOrNull(cursor, "text_content"),
                    barcodeFormat = getStringOrNull(cursor, "barcode_format"),
                    barcodeValue = getStringOrNull(cursor, "barcode_value"),
                )
            )
        }
        cursor.close()
        return documents
    }

    private fun getStringOrNull(cursor: Cursor, columnName: String): String? {
        val index = cursor.getColumnIndexOrThrow(columnName)
        return if (cursor.isNull(index)) null else cursor.getString(index)
    }
}
