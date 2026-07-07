package com.docwallet.cli

import com.docwallet.vault.database.VaultDatabase
import com.docwallet.vault.database.VaultFtsIndexer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SearchIntegrationTest {
    private lateinit var dbPath: String
    private lateinit var vault: VaultDatabase
    private lateinit var indexer: VaultFtsIndexer

    @Before
    fun setUp() {
        dbPath = File.createTempFile("search", ".db").absolutePath
        vault = VaultDatabase(SqlHandleJdbc.open(dbPath))
        vault.initialize()
        indexer = vault.ftsIndexer

        vault.handle.execSQL(
            """INSERT INTO documents(id, title, file_name, mime_type, file_path, file_size, page_count,
               author, description, imported_at, last_opened_at, is_favorite, text_content)
               VALUES(?, ?, '', ?, '', 0, 0, ?, ?, ?, 0, 0, ?)""".trimIndent(),
            arrayOf("1", "Quick Fox", "text/plain", "John", "A brown fox", 1000L, "quick brown fox")
        )
        vault.handle.execSQL(
            """INSERT INTO documents(id, title, file_name, mime_type, file_path, file_size, page_count,
               author, description, imported_at, last_opened_at, is_favorite, text_content)
               VALUES(?, ?, '', ?, '', 0, 0, ?, ?, ?, 0, 0, ?)""".trimIndent(),
            arrayOf("2", "Lazy Dog", "text/plain", "Jane", "A lazy dog", 1000L, "lazy dog slept")
        )
        vault.handle.execSQL(
            """INSERT INTO documents(id, title, file_name, mime_type, file_path, file_size, page_count,
               author, description, imported_at, last_opened_at, is_favorite, text_content)
               VALUES(?, ?, '', ?, '', 0, 0, ?, ?, ?, 0, 0, ?)""".trimIndent(),
            arrayOf("3", "Unrelated", "text/plain", "Bob", "Something else", 1000L, "nothing in common")
        )
        indexer.indexDocument("1", "Quick Fox", "John", "A brown fox", "quick brown fox")
        indexer.indexDocument("2", "Lazy Dog", "Jane", "A lazy dog", "lazy dog slept")
        indexer.indexDocument("3", "Unrelated", "Bob", "Something else", "nothing in common")
    }

    @After
    fun tearDown() {
        vault.close()
        File(dbPath).delete()
    }

    @Test
    fun `search by title with FTS finds matching document`() {
        val results = vault.searchEngine.search("fox")
        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun `search by content finds matching document`() {
        val results = vault.searchEngine.search("lazy")
        assertEquals(1, results.size)
        assertEquals("2", results[0].id)
    }

    @Test
    fun `search with short query uses LIKE fallback`() {
        val results = vault.searchEngine.search("ox")
        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun `search with blank query returns all`() {
        val results = vault.searchEngine.search("")
        assertEquals(3, results.size)
    }

    @Test
    fun `suggestions return matching titles`() {
        val suggestions = vault.searchEngine.getSuggestions("Q")
        assertTrue(suggestions.contains("Quick Fox"))
    }

    @Test
    fun `remove document from FTS`() {
        val before = vault.searchEngine.search("common")
        assertEquals(1, before.size)
        indexer.removeDocument("3")
        val after = vault.searchEngine.search("common")
        assertEquals(0, after.size)
    }
}
