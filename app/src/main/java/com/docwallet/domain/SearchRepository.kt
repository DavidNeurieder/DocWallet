package com.docwallet.domain

import com.docwallet.data.db.DocumentDao
import com.docwallet.data.model.Document
import com.docwallet.data.search.SearchEngine
import kotlinx.coroutines.flow.Flow

class SearchRepository(
    private val documentDao: DocumentDao,
    private val searchEngine: SearchEngine,
) {

    fun search(query: String): Flow<List<Document>> = searchEngine.search(query)

    fun getSuggestions(prefix: String): Flow<List<String>> = searchEngine.getSuggestions(prefix)

    fun getDocumentsByType(mimeType: String): Flow<List<Document>> =
        documentDao.getDocumentsByType(mimeType)

    fun getRecentDocuments(): Flow<List<Document>> {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        return documentDao.getRecentDocuments(sevenDaysAgo)
    }
}
