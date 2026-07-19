use rusqlite::{params, Connection, Result};

pub fn rebuild_index(conn: &Connection) -> Result<()> {
    conn.execute("INSERT INTO documents_fts(documents_fts) VALUES('rebuild')", [])?;
    Ok(())
}

#[derive(Debug)]
pub struct FtsResult {
    pub rank: f64,
    pub id: String,
    pub title: String,
}

/// Search documents using FTS5. Returns document id/title ordered by relevance.
pub fn search(conn: &Connection, query: &str) -> Result<Vec<FtsResult>> {
    let mut stmt = conn.prepare(
        "SELECT d.id, d.title, f.rank
         FROM documents_fts f
         JOIN documents d ON d.rowid = f.rowid
         WHERE f.documents_fts MATCH ?1
         ORDER BY f.rank
         LIMIT 100",
    )?;
    let results = stmt
        .query_map(params![query], |row| {
            Ok(FtsResult {
                id: row.get(0)?,
                title: row.get(1)?,
                rank: row.get(2)?,
            })
        })?
        .filter_map(|r| r.ok())
        .collect();
    Ok(results)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::schema::create_all_tables;

    fn setup_db() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        create_all_tables(&conn).unwrap();
        conn
    }

    #[test]
    fn test_fts_search() {
        let conn = setup_db();
        conn.execute(
            "INSERT INTO documents (id, title, file_name, mime_type, file_path, file_size, page_count, author, description, imported_at, last_opened_at, modified_at, is_favorite, is_conflict, current_page, text_content)
             VALUES (?1, ?2, ?3, ?4, ?5, 0, 0, '', '', 0, 0, 0, 0, 0, 0, ?6)",
            params!["doc1", "The quick brown fox", "fox.txt", "text/plain", "", "This is a document about the quick brown fox"],
        ).unwrap();
        conn.execute(
            "INSERT INTO documents (id, title, file_name, mime_type, file_path, file_size, page_count, author, description, imported_at, last_opened_at, modified_at, is_favorite, is_conflict, current_page, text_content)
             VALUES (?1, ?2, ?3, ?4, ?5, 0, 0, '', '', 0, 0, 0, 0, 0, 0, ?6)",
            params!["doc2", "Lazy dog", "dog.txt", "text/plain", "", "The lazy dog sleeps all day"],
        ).unwrap();

        rebuild_index(&conn).unwrap();

        let results = search(&conn, "fox").unwrap();
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].id, "doc1");
        assert_eq!(results[0].title, "The quick brown fox");
        assert!(results[0].rank.is_finite());
    }
}
