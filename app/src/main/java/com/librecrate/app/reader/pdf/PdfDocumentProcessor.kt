package com.librecrate.app.reader.pdf

import com.librecrate.app.vault.reader.DocumentProcessor
import com.librecrate.app.vault.reader.ProcessorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.vault_native.processDocument
import java.io.File

class PdfDocumentProcessor : DocumentProcessor {

    override suspend fun process(input: File, mimeType: String): ProcessorResult = withContext(Dispatchers.IO) {
        val result = processDocument(input.absolutePath, mimeType, input.nameWithoutExtension)
        ProcessorResult(
            title = result.title,
            author = result.author,
            pageCount = result.pageCount.toInt(),
            textContent = result.textContent,
            thumbnailData = result.thumbnailData,
        )
    }
}
