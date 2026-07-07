package com.docwallet.data.model

import com.docwallet.vault.model.VaultCollection
import com.docwallet.vault.model.VaultDocument
import com.docwallet.vault.model.VaultDocumentTag
import com.docwallet.vault.model.VaultTag

fun Document.toVaultDocument(): VaultDocument = VaultDocument(
    id = id,
    title = title,
    fileName = fileName,
    mimeType = mimeType,
    filePath = filePath,
    fileSize = fileSize,
    pageCount = pageCount,
    author = author,
    description = description,
    thumbnailPath = thumbnailPath,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    isFavorite = isFavorite,
    collectionId = collectionId,
    encryptionIv = encryptionIv,
    textContent = textContent,
    barcodeFormat = barcodeFormat,
    barcodeValue = barcodeValue,
    currentPage = currentPage,
    readingPosition = readingPosition,
)

fun VaultDocument.toRoomEntity(): Document = Document(
    id = id,
    title = title,
    fileName = fileName,
    mimeType = mimeType,
    filePath = filePath,
    fileSize = fileSize,
    pageCount = pageCount,
    author = author,
    description = description,
    thumbnailPath = thumbnailPath,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    isFavorite = isFavorite,
    collectionId = collectionId,
    encryptionIv = encryptionIv,
    textContent = textContent,
    barcodeFormat = barcodeFormat,
    barcodeValue = barcodeValue,
    currentPage = currentPage,
    readingPosition = readingPosition,
)

fun Tag.toVaultTag(): VaultTag = VaultTag(
    id = id,
    name = name,
    color = color,
)

fun VaultTag.toRoomEntity(): Tag = Tag(
    id = id,
    name = name,
    color = color,
)

fun Collection.toVaultCollection(): VaultCollection = VaultCollection(
    id = id,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    parentId = parentId,
)

fun VaultCollection.toRoomEntity(): Collection = Collection(
    id = id,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    parentId = parentId,
)

fun DocumentTag.toVaultDocumentTag(): VaultDocumentTag = VaultDocumentTag(
    documentId = documentId,
    tagId = tagId,
)
