package com.docwallet.vault.reader

interface ReaderFactory {
    fun createReader(document: ReaderDocument): DocumentReader?
    fun getProcessor(mimeType: String): DocumentProcessor?
}
