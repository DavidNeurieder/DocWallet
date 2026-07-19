use rusqlite::{params, Connection, Result};

/// A lightweight document row returned by query functions.
#[derive(Debug, Clone)]
pub struct DocumentRow {
    pub id: String,
    pub title: String,
    pub file_name: String,
    pub mime_type: String,
    pub file_path: String,
    pub file_size: i64,
    pub page_count: i32,
    pub author: String,
    pub description: String,
    pub thumbnail_path: Option<String>,
    pub imported_at: i64,
    pub last_opened_at: i64,
    pub modified_at: i64,
    pub is_favorite: bool,
    pub is_conflict: bool,
    pub conflict_with: Option<String>,
    pub collection_id: Option<String>,
    pub encryption_iv: Option<Vec<u8>>,
    pub current_page: i32,
}

#[derive(Debug, Clone)]
pub struct CollectionRow {
    pub id: String,
    pub name: String,
    pub icon: String,
    pub sort_order: i32,
    pub parent_id: Option<String>,
}

#[derive(Debug, Clone)]
pub struct TagRow {
    pub id: String,
    pub name: String,
    pub color: i64,
}

pub fn list_documents(conn: &Connection) -> Result<Vec<DocumentRow>> {
    let mut stmt = conn.prepare(
        "SELECT id, title, file_name, mime_type, file_path, file_size, page_count,
                author, description, thumbnail_path, imported_at, last_opened_at,
                modified_at, is_favorite, is_conflict, conflict_with, collection_id,
                encryption_iv, current_page
         FROM documents ORDER BY title",
    )?;
    let rows = stmt
        .query_map([], |row| {
            Ok(DocumentRow {
                id: row.get(0)?,
                title: row.get(1)?,
                file_name: row.get(2)?,
                mime_type: row.get(3)?,
                file_path: row.get(4)?,
                file_size: row.get(5)?,
                page_count: row.get(6)?,
                author: row.get(7)?,
                description: row.get(8)?,
                thumbnail_path: row.get(9)?,
                imported_at: row.get(10)?,
                last_opened_at: row.get(11)?,
                modified_at: row.get(12)?,
                is_favorite: row.get::<_, i32>(13)? != 0,
                is_conflict: row.get::<_, i32>(14)? != 0,
                conflict_with: row.get(15)?,
                collection_id: row.get(16)?,
                encryption_iv: row.get(17)?,
                current_page: row.get(18)?,
            })
        })?
        .filter_map(|r| r.ok())
        .collect();
    Ok(rows)
}

pub fn get_document(conn: &Connection, id: &str) -> Result<Option<DocumentRow>> {
    let mut stmt = conn.prepare(
        "SELECT id, title, file_name, mime_type, file_path, file_size, page_count,
                author, description, thumbnail_path, imported_at, last_opened_at,
                modified_at, is_favorite, is_conflict, conflict_with, collection_id,
                encryption_iv, current_page
         FROM documents WHERE id = ?",
    )?;
    let mut rows = stmt.query(params![id])?;
    match rows.next()? {
        Some(row) => Ok(Some(DocumentRow {
            id: row.get(0)?,
            title: row.get(1)?,
            file_name: row.get(2)?,
            mime_type: row.get(3)?,
            file_path: row.get(4)?,
            file_size: row.get(5)?,
            page_count: row.get(6)?,
            author: row.get(7)?,
            description: row.get(8)?,
            thumbnail_path: row.get(9)?,
            imported_at: row.get(10)?,
            last_opened_at: row.get(11)?,
            modified_at: row.get(12)?,
            is_favorite: row.get::<_, i32>(13)? != 0,
            is_conflict: row.get::<_, i32>(14)? != 0,
            conflict_with: row.get(15)?,
            collection_id: row.get(16)?,
            encryption_iv: row.get(17)?,
            current_page: row.get(18)?,
        })),
        None => Ok(None),
    }
}

pub fn add_document(conn: &Connection, doc: &DocumentRow) -> Result<()> {
    conn.execute(
        "INSERT INTO documents (id, title, file_name, mime_type, file_path, file_size, page_count,
         author, description, thumbnail_path, imported_at, last_opened_at, modified_at,
         is_favorite, is_conflict, conflict_with, collection_id, encryption_iv, current_page)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19)",
        params![
            doc.id, doc.title, doc.file_name, doc.mime_type, doc.file_path,
            doc.file_size, doc.page_count, doc.author, doc.description, doc.thumbnail_path,
            doc.imported_at, doc.last_opened_at, doc.modified_at,
            doc.is_favorite as i32, doc.is_conflict as i32, doc.conflict_with, doc.collection_id,
            doc.encryption_iv, doc.current_page,
        ],
    )?;
    Ok(())
}

pub fn delete_document(conn: &Connection, id: &str) -> Result<bool> {
    let affected = conn.execute("DELETE FROM documents WHERE id = ?", params![id])?;
    Ok(affected > 0)
}

pub fn list_collections(conn: &Connection) -> Result<Vec<CollectionRow>> {
    let mut stmt = conn.prepare(
        "SELECT id, name, icon, sort_order, parent_id FROM collections ORDER BY sort_order",
    )?;
    let rows = stmt
        .query_map([], |row| {
            Ok(CollectionRow {
                id: row.get(0)?,
                name: row.get(1)?,
                icon: row.get(2)?,
                sort_order: row.get(3)?,
                parent_id: row.get(4)?,
            })
        })?
        .filter_map(|r| r.ok())
        .collect();
    Ok(rows)
}

pub fn list_tags(conn: &Connection) -> Result<Vec<TagRow>> {
    let mut stmt = conn.prepare("SELECT id, name, color FROM tags ORDER BY name")?;
    let rows = stmt
        .query_map([], |row| {
            Ok(TagRow {
                id: row.get(0)?,
                name: row.get(1)?,
                color: row.get(2)?,
            })
        })?
        .filter_map(|r| r.ok())
        .collect();
    Ok(rows)
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
    fn test_add_and_list_documents() {
        let conn = setup_db();
        let doc = DocumentRow {
            id: "test1".into(),
            title: "Test Doc".into(),
            file_name: "test.pdf".into(),
            mime_type: "application/pdf".into(),
            file_path: "files/test.pdf".into(),
            file_size: 1024,
            page_count: 5,
            author: "Tester".into(),
            description: "A test".into(),
            thumbnail_path: None,
            imported_at: 1000,
            last_opened_at: 1000,
            modified_at: 1000,
            is_favorite: false,
            is_conflict: false,
            conflict_with: None,
            collection_id: None,
            encryption_iv: None,
            current_page: 0,
        };
        add_document(&conn, &doc).unwrap();
        let docs = list_documents(&conn).unwrap();
        assert_eq!(docs.len(), 1);
        assert_eq!(docs[0].id, "test1");
        assert_eq!(docs[0].title, "Test Doc");

        let fetched = get_document(&conn, "test1").unwrap().unwrap();
        assert_eq!(fetched.file_size, 1024);
    }

    #[test]
    fn test_delete_document() {
        let conn = setup_db();
        let doc = DocumentRow {
            id: "del1".into(),
            title: "Delete Me".into(),
            ..DocumentRow {
                id: String::new(),
                title: String::new(),
                file_name: String::new(),
                mime_type: String::new(),
                file_path: String::new(),
                file_size: 0,
                page_count: 0,
                author: String::new(),
                description: String::new(),
                thumbnail_path: None,
                imported_at: 0,
                last_opened_at: 0,
                modified_at: 0,
                is_favorite: false,
                is_conflict: false,
                conflict_with: None,
                collection_id: None,
                encryption_iv: None,
                current_page: 0,
            }
        };
        add_document(&conn, &doc).unwrap();
        assert!(delete_document(&conn, "del1").unwrap());
        assert!(!delete_document(&conn, "nonexistent").unwrap());
        assert_eq!(list_documents(&conn).unwrap().len(), 0);
    }
}
