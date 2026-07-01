package com.docwallet.ui.library

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwallet.DocWalletApplication
import com.docwallet.data.db.DocumentListItem
import com.docwallet.data.model.Document
import com.docwallet.data.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

enum class SortOption(val label: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    SIZE_LARGEST("Largest first"),
    TYPE("By type"),
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DocWalletApplication
    private val documentDao = app.documentDao

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val searchQuery = MutableStateFlow("")
    val selectedSort = MutableStateFlow(SortOption.DATE_NEWEST)
    val filterType = MutableStateFlow<DocumentType?>(null)
    val favoritesOnly = MutableStateFlow(false)

    val renamingDocument = MutableStateFlow<Document?>(null)
    val renameInput = MutableStateFlow("")
    val showRenameDialog = MutableStateFlow(false)

    val continueReading: StateFlow<List<Document>> = documentDao.getRecentDocuments(
        System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 // last 7 days
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<Document>> = combine(
        documentDao.getDocumentList(),
        searchQuery,
        selectedSort,
        filterType,
        favoritesOnly
    ) { items, query, sort, type, favs ->
        var result = items.map { item ->
            Document(
                id = item.id,
                title = item.title,
                fileName = item.fileName,
                mimeType = item.mimeType,
                fileSize = item.fileSize,
                pageCount = item.pageCount,
                author = item.author,
                description = item.description,
                thumbnailPath = item.thumbnailPath,
                importedAt = item.importedAt,
                lastOpenedAt = item.lastOpenedAt,
                isFavorite = item.isFavorite,
                collectionId = item.collectionId,
                barcodeFormat = item.barcodeFormat,
                barcodeValue = item.barcodeValue,
                currentPage = item.currentPage,
                readingPosition = item.readingPosition,
            )
        }

        if (type != null) {
            result = result.filter { doc ->
                if (type.mimeType.endsWith("/*")) {
                    doc.mimeType.startsWith(type.mimeType.removeSuffix("/*"))
                } else {
                    doc.mimeType == type.mimeType
                }
            }
        }

        if (favs) {
            result = result.filter { it.isFavorite }
        }

        if (query.isNotBlank()) {
            result = result.filter { it.title.contains(query, ignoreCase = true) }
        }

        result = when (sort) {
            SortOption.NAME_ASC -> result.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> result.sortedByDescending { it.title.lowercase() }
            SortOption.DATE_NEWEST -> result.sortedByDescending { it.importedAt }
            SortOption.DATE_OLDEST -> result.sortedBy { it.importedAt }
            SortOption.SIZE_LARGEST -> result.sortedByDescending { it.fileSize }
            SortOption.TYPE -> result.sortedBy { it.mimeType }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            documentDao.getDocumentList().collect {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch {
            documentDao.update(document.copy(isFavorite = !document.isFavorite))
        }
    }

    fun renameDocument(id: String, newTitle: String) {
        viewModelScope.launch {
            val doc = documentDao.getDocumentById(id) ?: return@launch
            documentDao.update(doc.copy(title = newTitle))
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            documentDao.deleteById(document.id)
        }
    }

    fun setSort(sort: SortOption) {
        selectedSort.value = sort
    }

    fun setFilter(type: DocumentType?) {
        filterType.value = type
    }

    fun search(query: String) {
        searchQuery.value = query
    }

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun importDocuments(uris: List<Uri>) {
        viewModelScope.launch {
            val app = getApplication<DocWalletApplication>()
            var successCount = 0
            var failCount = 0
            for (uri in uris) {
                try {
                    val mimeType = withContext(Dispatchers.IO) {
                        app.contentResolver.getType(uri) ?: "application/octet-stream"
                    }
                    val doc = withContext(Dispatchers.IO) {
                        app.documentImporter.importDocument(uri, mimeType)
                    }
                    if (doc != null) successCount++ else failCount++
                } catch (e: Exception) {
                    failCount++
                }
            }
            _snackbarMessage.value = when {
                failCount == 0 -> "Imported $successCount document${if (successCount != 1) "s" else ""}"
                successCount == 0 -> "Import failed"
                else -> "Imported $successCount document${if (successCount != 1) "s" else ""}, $failCount failed"
            }
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    private val _thumbnails = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, ImageBitmap>> = _thumbnails.asStateFlow()

    private val thumbnailSemaphore = Semaphore(4)

    fun loadThumbnail(documentId: String, thumbnailPath: String) {
        viewModelScope.launch {
            thumbnailSemaphore.acquire()
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decryptThumbnail(thumbnailPath)
                }
                if (bitmap != null) {
                    _thumbnails.value = _thumbnails.value + (documentId to bitmap)
                }
            } finally {
                thumbnailSemaphore.release()
            }
        }
    }

    private fun decryptThumbnail(path: String): ImageBitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val bytes = FileInputStream(file).use { input ->
                DataInputStream(input).use { dis ->
                    val iv = ByteArray(12)
                    dis.readFully(iv)
                    val encrypted = dis.readBytes()
                    val masterKey = app.encryptionManager.getMasterKeyForSession()
                        ?: return null
                    app.fileEncryptor.decryptBytes(encrypted, masterKey, iv)
                }
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
