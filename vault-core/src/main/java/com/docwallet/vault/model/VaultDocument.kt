package com.docwallet.vault.model

data class VaultDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val filePath: String,
    val fileSize: Long,
    val pageCount: Int,
    val author: String,
    val description: String,
    val thumbnailPath: String?,
    val importedAt: Long,
    val lastOpenedAt: Long,
    val isFavorite: Boolean,
    val collectionId: String?,
    val encryptionIv: ByteArray?,
    val textContent: String?,
    val barcodeFormat: String?,
    val barcodeValue: String?,
    val currentPage: Int,
    val readingPosition: String?,
)
