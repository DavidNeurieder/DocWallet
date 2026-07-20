package com.librecrate.app.ui.library

import com.librecrate.app.LibreCrateApplication
import com.librecrate.app.data.model.Document
import com.librecrate.app.data.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uniffi.vault_native.SnippetResultFfi

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val documentsFlow = MutableStateFlow<List<Document>>(emptyList())
    private val mockVault = mockk<VaultRepository>(relaxed = true)
    private val mockApp = mockk<LibreCrateApplication>(relaxed = true)
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        documentsFlow.value = emptyList()
        every { mockApp.vaultRepository } returns mockVault
        every { mockVault.documents } returns documentsFlow
        viewModel = LibraryViewModel(mockApp)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.keepSearchActive() {
        backgroundScope.launch { viewModel.searchResults.collect { } }
    }

    @Test
    fun `search results empty when query is blank`() = runTest(testDispatcher) {
        keepSearchActive()
        viewModel.search("")
        advanceUntilIdle()
        assertTrue(viewModel.searchResults.value.isEmpty())
    }

    @Test
    fun `search results returns documents from vault`() = runTest(testDispatcher) {
        keepSearchActive()
        documentsFlow.value = listOf(
            Document(
                id = "1", title = "Doc One", fileName = "doc.pdf",
                mimeType = "application/pdf", pageCount = 10, author = "Author",
                description = "The quick fox jumps",
            )
        )
        coEvery { mockVault.searchDocumentsWithSnippet("fox") } returns listOf(
            SnippetResultFfi(rank = 1.0, id = "1", title = "Doc One", snippet = "The quick <b>fox</b> jumps"),
        )

        viewModel.search("fox")
        advanceUntilIdle()

        assertEquals(1, viewModel.searchResults.value.size)
        assertTrue(viewModel.searchResults.value[0].matches[0].snippet.contains("fox"))
    }

    @Test
    fun `search results are empty after clearing query`() = runTest(testDispatcher) {
        keepSearchActive()
        documentsFlow.value = listOf(
            Document(
                id = "1", title = "Doc", fileName = "doc.txt",
                mimeType = "text/plain",
                description = "some text",
            )
        )
        coEvery { mockVault.searchDocumentsWithSnippet("text") } returns listOf(
            SnippetResultFfi(rank = 1.0, id = "1", title = "Doc", snippet = "some <b>text</b>"),
        )

        viewModel.search("text")
        advanceUntilIdle()
        assertEquals(1, viewModel.searchResults.value.size)

        viewModel.search("")
        advanceUntilIdle()
        assertTrue(viewModel.searchResults.value.isEmpty())
    }

    @Test
    fun `search results are updated on new query`() = runTest(testDispatcher) {
        keepSearchActive()

        viewModel.search("fox")
        advanceUntilIdle()
        assertTrue(viewModel.searchResults.value.isEmpty())

        documentsFlow.value = listOf(
            Document(
                id = "2", title = "Rabbit Facts", fileName = "rabbit.pdf",
                mimeType = "application/pdf", author = "Nature",
                description = "the quick rabbit",
            )
        )
        coEvery { mockVault.searchDocumentsWithSnippet("rabbit") } returns listOf(
            SnippetResultFfi(rank = 1.0, id = "2", title = "Rabbit Facts", snippet = "the quick <b>rabbit</b>"),
        )

        viewModel.search("rabbit")
        advanceUntilIdle()
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("2", viewModel.searchResults.value[0].id)
    }

    @Test
    fun `search does not trigger when query is blank`() = runTest(testDispatcher) {
        keepSearchActive()
        viewModel.search("")
        advanceUntilIdle()
        assertTrue(viewModel.searchResults.value.isEmpty())
    }
}
