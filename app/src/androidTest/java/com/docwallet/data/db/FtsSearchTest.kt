package com.docwallet.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.docwallet.data.model.Document
import net.sqlcipher.database.SupportFactory

@RunWith(AndroidJUnit4::class)
class FtsSearchTest {

    private lateinit var db: DocWalletDatabase
    private lateinit var dao: DocumentDao

    private val ftsQuery =
        "SELECT d.id, d.title, d.file_name, d.mime_type, d.file_size, d.page_count, d.author, d.description, d.thumbnail_path, d.imported_at, d.last_opened_at, d.is_favorite, d.collection_id, d.barcode_format, d.barcode_value, d.current_page, d.reading_position FROM documents d INNER JOIN documents_fts fts ON d.rowid = fts.rowid WHERE documents_fts MATCH ? ORDER BY rank"

    private val snippetQuery =
        "SELECT d.id, d.title, d.mime_type, d.page_count, d.author, d.thumbnail_path, highlight(documents_fts, 3, '<b>', '</b>') AS snippet FROM documents d INNER JOIN documents_fts fts ON d.rowid = fts.rowid WHERE documents_fts MATCH ? ORDER BY rank"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val passphrase = ByteArray(32).also { it[0] = 1 }
        val factory = SupportFactory(passphrase, null, false)
        db = Room.inMemoryDatabaseBuilder(
            context,
            DocWalletDatabase::class.java
        )
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .addCallback(DocWalletDatabase.FTS_CALLBACK)
            .build()
        dao = db.documentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun searchByTitle() = runBlocking {
        dao.insert(Document(id = "1", title = "Quick Brown Fox"))
        dao.insert(Document(id = "2", title = "Lazy Dog"))

        val results = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("Quick*"))
        ).first()

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun searchByTextContent() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Doc One",
                textContent = "The quick brown fox jumps over the lazy dog."
            )
        )
        dao.insert(
            Document(
                id = "2",
                title = "Doc Two",
                textContent = "Nothing about foxes here."
            )
        )

        val results = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("fox*"))
        ).first()

        assertEquals(2, results.size)
    }

    @Test
    fun searchByAuthor() = runBlocking {
        dao.insert(Document(id = "1", title = "Book", author = "Jane Austen"))
        dao.insert(Document(id = "2", title = "Article", author = "John Doe"))

        val results = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("Jane*"))
        ).first()

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun searchMultiplePrefixTerms() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Gardening Guide",
                textContent = "How to grow tomatoes and carrots."
            )
        )
        dao.insert(
            Document(
                id = "2",
                title = "Cooking Book",
                textContent = "Recipes for delicious soups."
            )
        )

        val results = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("grow* tomato*"))
        ).first()

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun searchNoMatchesReturnsEmpty() = runBlocking {
        dao.insert(Document(id = "1", title = "Something", textContent = "irrelevant text"))

        val results = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("zzzzz*"))
        ).first()

        assertTrue(results.isEmpty())
    }

    @Test
    fun searchAutoIndexesNewDocumentsViaTrigger() = runBlocking {
        dao.insert(
            Document(
                id = "2",
                title = "Newly Inserted",
                textContent = "Fresh content for searching"
            )
        )

        val results = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("fresh*"))
        ).first()

        assertEquals(1, results.size)
        assertEquals("2", results[0].id)
    }

    @Test
    fun searchAfterUpdateReflectsNewContentViaTrigger() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Original Title",
                textContent = "Original content"
            )
        )

        val beforeUpdate = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("updated*"))
        ).first()
        assertTrue(beforeUpdate.isEmpty())

        dao.update(
            Document(
                id = "1",
                title = "Updated Title",
                textContent = "Updated content now searchable"
            )
        )

        val afterUpdate = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("updated*"))
        ).first()

        assertEquals(1, afterUpdate.size)
        assertEquals("1", afterUpdate[0].id)
    }

    @Test
    fun searchAfterDeleteRemovesFromFtsViaTrigger() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "To Delete",
                textContent = "Will be deleted"
            )
        )

        val beforeDelete = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("delete*"))
        ).first()
        assertEquals(1, beforeDelete.size)

        dao.deleteById("1")

        val afterDelete = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("delete*"))
        ).first()
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun searchIgnoresNullTextContent() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Book",
                textContent = "This document has text content"
            )
        )
        dao.insert(
            Document(
                id = "2",
                title = "Empty Doc",
                textContent = null
            )
        )

        val results = dao.searchDocuments(
            SimpleSQLiteQuery(ftsQuery, arrayOf("text*"))
        ).first()

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun snippetReturnsHighlightedTextContent() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Animal Facts",
                textContent = "The quick brown fox jumps over the lazy dog."
            )
        )

        val results = dao.searchDocumentsWithSnippets(
            SimpleSQLiteQuery(snippetQuery, arrayOf("fox*"))
        ).first()

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
        assertTrue(results[0].snippet.contains("<b>fox</b>"))
    }

    @Test
    fun snippetIsEmptyWhenNoMatch() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Something",
                textContent = "irrelevant text"
            )
        )

        val results = dao.searchDocumentsWithSnippets(
            SimpleSQLiteQuery(snippetQuery, arrayOf("zzzzz*"))
        ).first()

        assertTrue(results.isEmpty())
    }

    @Test
    fun snippetWorksWithTitleMatch() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Foxes are quick animals",
                textContent = "This document contains interesting animal facts."
            )
        )

        val results = dao.searchDocumentsWithSnippets(
            SimpleSQLiteQuery(snippetQuery, arrayOf("fox*"))
        ).first()

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun snippetIsOrderedByRelevance() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Doc One",
                textContent = "The fox is quick."
            )
        )
        dao.insert(
            Document(
                id = "2",
                title = "Doc Two",
                textContent = "Fox facts for fox lovers."
            )
        )

        val results = dao.searchDocumentsWithSnippets(
            SimpleSQLiteQuery(snippetQuery, arrayOf("fox*"))
        ).first()

        assertEquals(2, results.size)
        assertEquals("2", results[0].id)
    }

    @Test
    fun snippetAfterUpdateShowsNewContent() = runBlocking {
        dao.insert(
            Document(
                id = "1",
                title = "Original",
                textContent = "Original content no animals"
            )
        )

        val before = dao.searchDocumentsWithSnippets(
            SimpleSQLiteQuery(snippetQuery, arrayOf("fox*"))
        ).first()
        assertTrue(before.isEmpty())

        dao.update(
            Document(
                id = "1",
                title = "Original",
                textContent = "Updated content about foxes and nature"
            )
        )

        val after = dao.searchDocumentsWithSnippets(
            SimpleSQLiteQuery(snippetQuery, arrayOf("fox*"))
        ).first()

        assertEquals(1, after.size)
        assertEquals("1", after[0].id)
        assertTrue(after[0].snippet.contains("<b>fox"))
    }
}
