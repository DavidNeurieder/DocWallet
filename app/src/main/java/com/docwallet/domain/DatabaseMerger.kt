package com.docwallet.domain

import android.util.Log
import com.docwallet.data.db.DocWalletDatabase
import com.docwallet.data.model.Collection
import com.docwallet.data.model.Document
import com.docwallet.data.model.DocumentTag
import com.docwallet.data.model.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

class DatabaseMerger(
    private val getDatabase: () -> DocWalletDatabase?,
) {
    suspend fun merge(backupDbFile: File, masterKey: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            val currentDb = getDatabase() ?: return@withContext false
            val backupDb = SQLiteDatabase.openOrCreateDatabase(
                backupDbFile.absolutePath, masterKey, null
            )

            try {
                mergeCollections(backupDb, currentDb)
                mergeTags(backupDb, currentDb)
                mergeDocuments(backupDb, currentDb)
                mergeDocumentTags(backupDb, currentDb)
                Log.d(TAG, "Database merge completed")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Database merge failed", e)
                false
            } finally {
                backupDb.close()
            }
        }
    }

    private suspend fun mergeCollections(backupDb: SQLiteDatabase, currentDb: DocWalletDatabase) {
        val collections = mutableListOf<Collection>()
        backupDb.rawQuery("SELECT * FROM collections", null).use { cursor ->
            while (cursor.moveToNext()) {
                collections.add(
                    Collection(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        icon = cursor.getString(cursor.getColumnIndexOrThrow("icon")),
                        sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
                        parentId = getStringOrNull(cursor, "parent_id"),
                    )
                )
            }
        }
        for (item in collections) {
            runCatching { currentDb.collectionDao().insertOrIgnore(item) }
        }
    }

    private suspend fun mergeTags(backupDb: SQLiteDatabase, currentDb: DocWalletDatabase) {
        val tags = mutableListOf<Tag>()
        backupDb.rawQuery("SELECT * FROM tags", null).use { cursor ->
            while (cursor.moveToNext()) {
                tags.add(
                    Tag(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        color = cursor.getLong(cursor.getColumnIndexOrThrow("color")),
                    )
                )
            }
        }
        for (item in tags) {
            runCatching { currentDb.tagDao().insertOrIgnore(item) }
        }
    }

    private suspend fun mergeDocuments(backupDb: SQLiteDatabase, currentDb: DocWalletDatabase) {
        val documents = mutableListOf<Document>()
        backupDb.rawQuery("SELECT * FROM documents", null).use { cursor ->
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
                        author = getStringOrNull(cursor, "author") ?: "",
                        description = getStringOrNull(cursor, "description") ?: "",
                        thumbnailPath = getStringOrNull(cursor, "thumbnail_path"),
                        importedAt = cursor.getLong(cursor.getColumnIndexOrThrow("imported_at")),
                        lastOpenedAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_opened_at")),
                        isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) != 0,
                        collectionId = getStringOrNull(cursor, "collection_id"),
                        encryptionIv = cursor.getBlob(cursor.getColumnIndexOrThrow("encryption_iv")),
                        textContent = getStringOrNull(cursor, "text_content"),
                        barcodeFormat = getStringOrNull(cursor, "barcode_format"),
                        barcodeValue = getStringOrNull(cursor, "barcode_value"),
                        currentPage = cursor.getInt(cursor.getColumnIndexOrThrow("current_page")),
                        readingPosition = getStringOrNull(cursor, "reading_position"),
                    )
                )
            }
        }
        for (item in documents) {
            runCatching { currentDb.documentDao().insertOrIgnore(item) }
        }
    }

    private suspend fun mergeDocumentTags(backupDb: SQLiteDatabase, currentDb: DocWalletDatabase) {
        backupDb.rawQuery("SELECT * FROM document_tags", null).use { cursor ->
            while (cursor.moveToNext()) {
                val dt = DocumentTag(
                    documentId = cursor.getString(cursor.getColumnIndexOrThrow("document_id")),
                    tagId = cursor.getString(cursor.getColumnIndexOrThrow("tag_id")),
                )
                runCatching { currentDb.documentTagDao().insertOrIgnore(dt) }
            }
        }
    }

    private fun getStringOrNull(cursor: net.sqlcipher.Cursor, columnName: String): String? {
        val index = cursor.getColumnIndexOrThrow(columnName)
        return if (cursor.isNull(index)) null else cursor.getString(index)
    }

    companion object {
        private const val TAG = "DatabaseMerger"
    }
}
