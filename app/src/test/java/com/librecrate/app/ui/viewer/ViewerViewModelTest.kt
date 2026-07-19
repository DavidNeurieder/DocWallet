package com.librecrate.app.ui.viewer

import com.librecrate.app.LibreCrateApplication
import com.librecrate.app.data.SessionStore
import com.librecrate.app.data.model.Document
import com.librecrate.app.data.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewerViewModelTest {

    private val testScheduler = StandardTestDispatcher().scheduler
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val ioDispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var context: android.content.Context
    private lateinit var mockApp: LibreCrateApplication
    private lateinit var mockVault: VaultRepository
    private lateinit var viewModel: ViewerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = RuntimeEnvironment.getApplication().applicationContext

        mockVault = mockk(relaxed = true)
        mockApp = mockk(relaxed = true)
        every { mockApp.vaultRepository } returns mockVault
        every { mockApp.filesDir } returns context.filesDir
        every { mockApp.cacheDir } returns context.cacheDir
        every { mockApp.getSharedPreferences(any(), any()) } answers {
            context.getSharedPreferences(firstArg(), secondArg())
        }

        viewModel = ViewerViewModel(mockApp, ioDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        File(context.filesDir, "files").deleteRecursively()
    }

    @Test
    fun `loadDocument loads PDF successfully`() = runTest(testDispatcher) {
        val content = "Test PDF content".toByteArray()
        val doc = Document(
            id = "test-pdf-id",
            title = "Test PDF",
            fileName = "test.pdf",
            mimeType = "application/pdf",
            fileSize = content.size.toLong(),
        )

        coEvery { mockVault.getDocument("test-pdf-id") } returns doc
        coEvery { mockVault.exportDocumentFile("test-pdf-id") } returns content

        viewModel.loadDocument("test-pdf-id")
        advanceUntilIdle()

        assertNull("There should be no error", viewModel.error.value)
        assertNotNull("Decrypted file should be set", viewModel.decryptedFile.value)
        assertEquals("Test PDF", viewModel.document.value?.title)
        assertEquals("application/pdf", viewModel.document.value?.mimeType)

        val decryptedFile = viewModel.decryptedFile.value!!
        assertTrue("Decrypted file should exist", decryptedFile.exists())
        assertEquals("Content should match", content.size.toLong(), decryptedFile.length())
    }

    @Test
    fun `loadDocument handles document not found`() = runTest(testDispatcher) {
        coEvery { mockVault.getDocument("nonexistent") } returns null

        viewModel.loadDocument("nonexistent")
        advanceUntilIdle()

        assertEquals("Document not found", viewModel.error.value)
        assertNull(viewModel.decryptedFile.value)
        assertNull(viewModel.document.value)
    }

    @Test
    fun `loadDocument creates new note for UUID-pattern document ID`() = runTest(testDispatcher) {
        val noteId = "123e4567-e89b-12d3-a456-426614174000"
        val content = "".encodeToByteArray()

        val doc = Document(
            id = noteId, title = "New Note", fileName = "note.md",
            mimeType = "text/markdown",
        )
        coEvery { mockVault.getDocument(noteId) } returns null andThen doc
        coEvery { mockVault.importDocument(any(), any(), any(), any(), any(), any(), any()) } returns noteId

        viewModel.loadDocument(noteId, isNewNote = true)
        advanceUntilIdle()

        assertEquals("New Note", viewModel.document.value?.title)
        assertEquals("text/markdown", viewModel.document.value?.mimeType)
        assertNotNull("Decrypted file should exist", viewModel.decryptedFile.value)
        assertNull("No error expected", viewModel.error.value)
    }

    @Test
    fun `isLoading state transitions correctly during load`() = runTest(testDispatcher) {
        val content = "Loading state test".toByteArray()
        val doc = Document(
            id = "loading-test-id",
            title = "Loading Test",
            fileName = "test.pdf",
            mimeType = "application/pdf",
            fileSize = content.size.toLong(),
        )

        coEvery { mockVault.getDocument("loading-test-id") } returns doc
        coEvery { mockVault.exportDocumentFile("loading-test-id") } returns content

        assertFalse("Should not be loading yet", viewModel.isLoading.value)

        viewModel.loadDocument("loading-test-id")
        advanceUntilIdle()

        assertFalse("Should finish loading", viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `loadDocument saves ID to SessionStore on success`() = runTest(testDispatcher) {
        val content = "SessionStore test".toByteArray()
        val doc = Document(
            id = "session-test-id",
            title = "Session Test",
            fileName = "test.pdf",
            mimeType = "application/pdf",
            fileSize = content.size.toLong(),
        )

        coEvery { mockVault.getDocument("session-test-id") } returns doc
        coEvery { mockVault.exportDocumentFile("session-test-id") } returns content

        viewModel.loadDocument("session-test-id")
        advanceUntilIdle()

        assertEquals("session-test-id", SessionStore.getLastDocumentId(mockApp))
    }

    @Test
    fun `loadDocument clears SessionStore when document not found`() = runTest(testDispatcher) {
        SessionStore.saveLastDocumentId(mockApp, "stale-id")
        coEvery { mockVault.getDocument("missing") } returns null

        viewModel.loadDocument("missing")
        advanceUntilIdle()

        assertNull("Stale ID should be cleared", SessionStore.getLastDocumentId(mockApp))
    }

    @Test
    fun `loadDocument saves EPUB document ID to SessionStore`() = runTest(testDispatcher) {
        val content = "EPUB content".toByteArray()
        val doc = Document(
            id = "epub-session-test",
            title = "Test EPUB",
            fileName = "book.epub",
            mimeType = "application/epub+zip",
            pageCount = 42,
            fileSize = content.size.toLong(),
        )

        coEvery { mockVault.getDocument("epub-session-test") } returns doc
        coEvery { mockVault.exportDocumentFile("epub-session-test") } returns content

        viewModel.loadDocument("epub-session-test")
        advanceUntilIdle()

        assertEquals("SessionStore should have EPUB doc ID", "epub-session-test", SessionStore.getLastDocumentId(mockApp))
        assertEquals("Test EPUB", viewModel.document.value?.title)
        assertNull("No error expected", viewModel.error.value)
    }

    @Test
    fun `loadDocument reports error when file data is null`() = runTest(testDispatcher) {
        val doc = Document(
            id = "no-file-id",
            title = "No File",
            fileName = "test.pdf",
            mimeType = "application/pdf",
        )

        coEvery { mockVault.getDocument("no-file-id") } returns doc
        coEvery { mockVault.exportDocumentFile("no-file-id") } returns null

        viewModel.loadDocument("no-file-id")
        advanceUntilIdle()

        assertEquals("Failed to read document file", viewModel.error.value)
        assertNull(viewModel.decryptedFile.value)
    }

    @Test
    fun `after restart EPUB document is still loadable`() = runTest(testDispatcher) {
        val content = "Persistent EPUB".toByteArray()
        val docId = "epub-restart-test"
        val doc = Document(
            id = docId,
            title = "Restart EPUB",
            fileName = "book.epub",
            mimeType = "application/epub+zip",
            pageCount = 10,
            fileSize = content.size.toLong(),
        )

        coEvery { mockVault.getDocument(docId) } returns doc
        coEvery { mockVault.exportDocumentFile(docId) } returns content

        viewModel.loadDocument(docId)
        advanceUntilIdle()
        assertEquals("First load should succeed", "Restart EPUB", viewModel.document.value?.title)

        val savedId = SessionStore.getLastDocumentId(mockApp)
        assertEquals("SessionStore should have the ID after first load", docId, savedId)

        val viewModel2 = ViewerViewModel(mockApp, ioDispatcher)
        coEvery { mockVault.getDocument(savedId!!) } returns doc
        coEvery { mockVault.exportDocumentFile(savedId!!) } returns content

        viewModel2.loadDocument(savedId!!)
        advanceUntilIdle()

        assertEquals("After restart, EPUB should still load", "Restart EPUB", viewModel2.document.value?.title)
        assertNull("No error after restart", viewModel2.error.value)
    }
}
