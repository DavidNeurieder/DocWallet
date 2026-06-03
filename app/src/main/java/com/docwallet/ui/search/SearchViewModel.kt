package com.docwallet.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwallet.DocWalletApplication
import com.docwallet.data.model.Document
import com.docwallet.data.model.DocumentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val documentDao = (application as DocWalletApplication).documentDao

    val query = MutableStateFlow("")
    val selectedFilter = MutableStateFlow<DocumentType?>(null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val results: StateFlow<List<Document>> = combine(
        documentDao.getAllDocuments(),
        query,
        selectedFilter,
    ) { allDocs, q, filter ->
        var filtered = allDocs

        if (q.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(q, ignoreCase = true) }
        }

        if (filter != null) {
            filtered = filtered.filter { doc ->
                if (filter.mimeType.endsWith("/*")) {
                    doc.mimeType.startsWith(filter.mimeType.removeSuffix("/*"))
                } else {
                    doc.mimeType == filter.mimeType
                }
            }
        }

        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggestions: StateFlow<List<String>> = combine(
        documentDao.getAllDocuments(),
        query,
    ) { allDocs, q ->
        if (q.isBlank()) emptyList()
        else allDocs
            .map { it.title }
            .filter { it.contains(q, ignoreCase = true) }
            .distinct()
            .take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(query: String) {
        this.query.value = query
    }

    fun setFilter(type: DocumentType?) {
        selectedFilter.value = type
    }

    fun clearSearch() {
        query.value = ""
        selectedFilter.value = null
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch {
            documentDao.update(document.copy(isFavorite = !document.isFavorite))
        }
    }
}
