package com.docwallet.domain

import android.graphics.BitmapFactory
import android.net.Uri
import com.docwallet.data.barcode.BarcodeDetector
import com.docwallet.data.db.DocumentDao
import com.docwallet.data.import.DocumentImporter
import com.docwallet.data.model.Document
import com.docwallet.data.search.FtsIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportRepository(
    private val documentImporter: DocumentImporter,
    private val ftsIndexer: FtsIndexer,
    private val barcodeDetector: BarcodeDetector,
    private val documentDao: DocumentDao,
) {

    suspend fun importFromUri(uri: Uri, mimeType: String): Document? {
        val document = documentImporter.importDocument(uri, mimeType) ?: return null

        document.thumbnailPath?.let { path ->
            withContext(Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    val barcodes = barcodeDetector.detect(bitmap)
                    if (barcodes.isNotEmpty()) {
                        val barcode = barcodes.first()
                        val updated = document.copy(
                            barcodeFormat = barcode.format,
                            barcodeValue = barcode.value
                        )
                        documentDao.update(updated)
                    }
                }
            }
        }

        ftsIndexer.indexDocument(
            documentId = document.id,
            title = document.title,
            author = document.author,
            description = document.description,
            textContent = document.textContent
        )

        return document
    }

    suspend fun createNote(title: String, content: String): Document? {
        val document = documentImporter.importNote(title, content) ?: return null

        ftsIndexer.indexDocument(
            documentId = document.id,
            title = document.title,
            author = document.author,
            description = document.description,
            textContent = document.textContent
        )

        return document
    }
}
