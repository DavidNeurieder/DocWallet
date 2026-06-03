package com.docwallet.data.search

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.docwallet.data.db.DocWalletDatabase
import com.docwallet.data.db.DocumentDao
import com.docwallet.data.model.Document
import com.docwallet.data.model.DocumentType

@OptIn(ExperimentalCoroutinesApi::class)
class SearchEngineTest {

    private lateinit var mockDao: DocumentDao
    private lateinit var mockDb: DocWalletDatabase
    private lateinit var engine: SearchEngine

    @Before
    fun setUp() {
        mockDao = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        engine = SearchEngine(mockDao, mockDb)
    }

    @Test
    fun `search with blank query returns all documents`() = runTest {
        val docs = listOf(Document(id = "1"), Document(id = "2"))
        every { mockDao.getAllDocuments() } returns flowOf(docs)

        engine.search("").test {
            assertEquals(docs, awaitItem())
            awaitComplete()
        }
        verify { mockDao.getAllDocuments() }
    }

    @Test
    fun `search with blank query and filter type returns filtered`() = runTest {
        val docs = listOf(Document(id = "3"))
        every { mockDao.getDocumentsByType(any()) } returns flowOf(docs)

        engine.search("", DocumentType.PDF).test {
            assertEquals(docs, awaitItem())
            awaitComplete()
        }
        verify { mockDao.getDocumentsByType("application/pdf") }
    }

    @Test
    fun `search with short query runs searchSimple path`() = runTest {
        engine.search("ab").test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `search with long query runs searchFts path`() = runTest {
        engine.search("abc").test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `searchByType delegates to DAO`() = runTest {
        val docs = listOf(Document(id = "4"))
        every { mockDao.getDocumentsByType("image/%") } returns flowOf(docs)

        engine.searchByType("image/%").test {
            assertEquals(docs, awaitItem())
            awaitComplete()
        }
        verify { mockDao.getDocumentsByType("image/%") }
    }

    @Test
    fun `getSuggestions returns flow`() = runTest {
        engine.getSuggestions("test").test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            awaitComplete()
        }
    }
}
