package com.docwallet.reader.epub

import com.docwallet.vault.reader.DocumentProcessor
import com.docwallet.vault.reader.DocumentReader
import com.docwallet.vault.reader.ReaderDocument
import com.docwallet.vault.reader.ReaderFactory

class EpubReaderFactory : ReaderFactory {

    override fun createReader(document: ReaderDocument): DocumentReader? {
        return if (isEpub(document.filePath)) {
            EpubDocumentReader(document.filePath)
        } else null
    }

    override fun getProcessor(mimeType: String): DocumentProcessor? {
        return if (mimeType == "application/epub+zip") {
            EpubDocumentProcessor()
        } else null
    }

    private fun isEpub(filePath: String): Boolean {
        return filePath.endsWith(".epub", ignoreCase = true)
    }
}
