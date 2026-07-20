use crate::crypto::aes_gcm;
use crate::db::queries::{self, DocumentRow};
use rusqlite::Connection;
use sha2::{Digest, Sha256};
use std::path::Path;

/// Save a thumbnail blob at `base_dir/files/<id>.thumb`.
pub fn store_thumbnail(base_dir: &Path, id: &str, data: &[u8], key: Option<&[u8]>) -> std::io::Result<()> {
    let path = base_dir.join("files").join(format!("{id}.thumb"));
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let blob = if let Some(k) = key {
        let (iv, ct) = aes_gcm::encrypt_bytes(data, k).unwrap_or_else(|| (vec![], vec![]));
        if iv.is_empty() { return Err(std::io::Error::new(std::io::ErrorKind::Other, "encryption failed")); }
        let mut out = iv;
        out.extend_from_slice(&ct);
        out
    } else {
        data.to_vec()
    };
    std::fs::write(&path, blob)
}

/// Load a thumbnail blob from `base_dir/files/<id>.thumb`.
pub fn load_thumbnail(base_dir: &Path, id: &str, key: Option<&[u8]>) -> Option<Vec<u8>> {
    let path = base_dir.join("files").join(format!("{id}.thumb"));
    let raw = if path.exists() { std::fs::read(&path).ok()? } else { return None };
    if let Some(k) = key {
        if raw.len() < aes_gcm::IV_LENGTH { return None; }
        let iv = &raw[..aes_gcm::IV_LENGTH];
        let ct = &raw[aes_gcm::IV_LENGTH..];
        aes_gcm::decrypt_bytes(ct, k, iv)
    } else {
        Some(raw)
    }
}

/// Save a file blob at `base_dir/files/<id>`.
pub fn save_file(base_dir: &Path, id: &str, data: &[u8]) -> std::io::Result<()> {
    let path = base_dir.join("files").join(id);
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(&path, data)
}

/// Load a file blob from `base_dir/files/<id>`.
pub fn load_file(base_dir: &Path, id: &str) -> Option<Vec<u8>> {
    let path = base_dir.join("files").join(id);
    if path.exists() {
        std::fs::read(&path).ok()
    } else {
        None
    }
}

/// Delete a file blob at `base_dir/files/<id>`.
pub fn delete_file(base_dir: &Path, id: &str) {
    let path = base_dir.join("files").join(id);
    let _ = std::fs::remove_file(&path);
}

/// Derive a `file_name` for a document. We prefer the original file name carried
/// in `title` (e.g. "book.epub") so downstream viewers can detect the type from the
/// extension. If `title` has no usable extension, fall back to one inferred from the
/// MIME type, and finally to the document `id`.
fn derive_file_name(id: &str, title: &str, mime_type: &str) -> String {
    let title_ext = std::path::Path::new(title)
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_lowercase());
    if let Some(ext) = title_ext {
        if !ext.is_empty() && ext.chars().all(|c| c.is_ascii_alphanumeric()) {
            return format!("{id}.{ext}");
        }
    }
    if let Some(ext) = extension_for_mime(mime_type) {
        return format!("{id}.{ext}");
    }
    id.to_string()
}

/// Best-effort file extension for a MIME type (subset relevant to this app).
fn extension_for_mime(mime: &str) -> Option<&'static str> {
    match mime {
        "application/pdf" => Some("pdf"),
        "application/epub+zip" => Some("epub"),
        "application/vnd.apple.pkpass" => Some("pkpass"),
        "application/vnd.comicbook+zip" | "application/x-cbr" => Some("cbz"),
        "image/png" => Some("png"),
        "image/jpeg" => Some("jpg"),
        "image/gif" => Some("gif"),
        "image/webp" => Some("webp"),
        "image/bmp" => Some("bmp"),
        "text/markdown" | "text/plain" => Some("md"),
        _ => None,
    }
}

/// Import a document: store the file blob (encrypted if key is provided), insert DB row, and index into FTS5.
/// Returns the document ID.
pub fn import_document(
    conn: &Connection,
    base_dir: &Path,
    id: &str,
    title: &str,
    file_data: &[u8],
    mime_type: &str,
    author: &str,
    description: &str,
    text_content: Option<&str>,
    key: Option<&[u8]>,
) -> rusqlite::Result<String> {
    // Compute content hash for deduplication
    let content_hash = hex::encode(Sha256::digest(file_data));

    // Check for existing document with the same hash
    if let Some(existing) = queries::find_document_by_hash(conn, &content_hash)? {
        return Ok(existing.id);
    }

    let (stored_data, iv): (Vec<u8>, Vec<u8>) = if let Some(k) = key {
        let (real_iv, ct) = aes_gcm::encrypt_bytes(file_data, k)
            .ok_or_else(|| rusqlite::Error::ToSqlConversionFailure(
                Box::new(std::io::Error::new(std::io::ErrorKind::Other, "encryption failed"))
            ))?;
        let mut out = real_iv.clone();
        out.extend_from_slice(&ct);
        (out, real_iv)
    } else {
        // No key — store plaintext with random garbage IV (legacy behavior)
        let dummy_iv: Vec<u8> = (0..aes_gcm::IV_LENGTH).map(|_| rand::random::<u8>()).collect();
        (file_data.to_vec(), dummy_iv)
    };

    save_file(base_dir, id, &stored_data).map_err(|e| {
        rusqlite::Error::ToSqlConversionFailure(Box::new(e))
    })?;

    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;
    let file_path = format!("files/{id}");

    let doc = DocumentRow {
        id: id.to_string(),
        title: title.to_string(),
        file_name: derive_file_name(id, title, mime_type),
        mime_type: mime_type.to_string(),
        file_path,
        file_size: file_data.len() as i64,
        author: author.to_string(),
        description: description.to_string(),
        imported_at: now,
        last_opened_at: now,
        modified_at: now,
        encryption_iv: Some(iv),
        content_hash: Some(content_hash),
        ..Default::default()
    };

    queries::add_document_full(conn, &doc, text_content)?;
    Ok(id.to_string())
}

/// Export a document's file blob from storage (decrypted if key is provided).
pub fn export_document_file(conn: &Connection, base_dir: &Path, id: &str, key: Option<&[u8]>) -> Option<Vec<u8>> {
    let doc = queries::get_document(conn, id).ok()??;
    let raw = load_file(base_dir, &doc.id)?;
    if let Some(k) = key {
        let iv = doc.encryption_iv.as_deref()?;
        if raw.len() < aes_gcm::IV_LENGTH { return None; }
        let ct = &raw[aes_gcm::IV_LENGTH..];
        aes_gcm::decrypt_bytes(ct, k, iv)
    } else {
        Some(raw)
    }
}

/// Delete a document: remove file blob, delete DB row.
/// FTS index is cleaned up automatically by the fts_after_delete trigger.
pub fn delete_document_full(conn: &Connection, base_dir: &Path, id: &str) -> rusqlite::Result<bool> {
    delete_file(base_dir, id);
    queries::delete_document(conn, id)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::schema::create_encrypted_db;

    #[test]
    fn test_import_export_roundtrip_with_key() {
        let mk = (0..32).collect::<Vec<u8>>();
        let tmp = tempfile::TempDir::new().unwrap();

        let db_path = tmp.path().join("databases/librecrate.db");
        std::fs::create_dir_all(db_path.parent().unwrap()).unwrap();
        let conn = create_encrypted_db(db_path.to_str().unwrap(), &mk).unwrap();

        let data = b"Hello, world!".to_vec();
        let doc_id = import_document(
            &conn, tmp.path(), "test-doc-1",
            "Test Doc", &data, "text/plain",
            "Author", "Description", Some("hello world content"),
            Some(&mk),
        ).unwrap();
        assert_eq!(doc_id, "test-doc-1");

        // Verify file exists (as encrypted blob)
        let file_path = tmp.path().join("files/test-doc-1");
        assert!(file_path.exists());
        let raw = std::fs::read(&file_path).unwrap();
        assert!(raw.len() > data.len()); // iv + ciphertext > plaintext
        let db_doc = queries::get_document(&conn, "test-doc-1").unwrap().unwrap();
        assert_eq!(&raw[..aes_gcm::IV_LENGTH], db_doc.encryption_iv.as_deref().unwrap());

        // Verify list
        let docs = queries::list_documents(&conn).unwrap();
        assert_eq!(docs.len(), 1);

        // Export back (decrypted)
        let exported = export_document_file(&conn, tmp.path(), "test-doc-1", Some(&mk)).unwrap();
        assert_eq!(exported, data);

        // Without key — returns encrypted blob
        let raw_export = export_document_file(&conn, tmp.path(), "test-doc-1", None).unwrap();
        assert_ne!(raw_export, data); // encrypted, not plaintext

        // Verify FTS
        let results = crate::db::fts::search(&conn, "hello").unwrap();
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].id, "test-doc-1");

        // Delete
        delete_document_full(&conn, tmp.path(), "test-doc-1").unwrap();
        assert!(!file_path.exists());
        assert_eq!(queries::list_documents(&conn).unwrap().len(), 0);
    }

    #[test]
    fn test_import_export_roundtrip_no_key() {
        let tmp = tempfile::TempDir::new().unwrap();

        let db_path = tmp.path().join("databases/librecrate.db");
        std::fs::create_dir_all(db_path.parent().unwrap()).unwrap();
        let conn = crate::db::schema::open_plain(db_path.to_str().unwrap()).unwrap();
        crate::db::schema::create_all_tables(&conn).unwrap();

        let data = b"Hello, plaintext!".to_vec();
        let _doc_id = import_document(
            &conn, tmp.path(), "test-plain",
            "Test Doc", &data, "text/plain",
            "Author", "Description", None,
            None,
        ).unwrap();

        // File stored as plaintext
        let raw = std::fs::read(tmp.path().join("files/test-plain")).unwrap();
        assert_eq!(raw, data);

        // Export back
        let exported = export_document_file(&conn, tmp.path(), "test-plain", None).unwrap();
        assert_eq!(exported, data);
    }
}
