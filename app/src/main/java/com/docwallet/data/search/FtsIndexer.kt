package com.docwallet.data.search

import com.docwallet.data.db.DocWalletDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FtsIndexer(private val database: DocWalletDatabase) {

    suspend fun indexDocument(
        documentId: String,
        title: String,
        author: String,
        description: String,
        textContent: String?
    ) {
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL(
                """INSERT OR REPLACE INTO documents_fts(rowid, title, author, description, text_content)
                   VALUES ((SELECT rowid FROM documents WHERE id = ?), ?, ?, ?, ?)""".trimIndent(),
                arrayOf(documentId, title, author, description, textContent)
            )
        }
    }

    suspend fun removeDocument(documentId: String) {
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM documents_fts WHERE rowid IN (SELECT rowid FROM documents WHERE id = ?)",
                arrayOf(documentId)
            )
        }
    }
}
