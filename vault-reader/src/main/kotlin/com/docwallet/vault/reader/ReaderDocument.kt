package com.docwallet.vault.reader

data class ReaderDocument(
    val id: String,
    val filePath: String,
    val mimeType: String = "",
)
