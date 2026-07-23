use std::path::Path;

/// Extract text from a PDF file for FTS indexing.
/// Returns `None` silently for any error (unreadable, corrupted, etc.).
pub fn extract_pdf_text(path: &Path) -> Option<String> {
    let doc = pdf_oxide::PdfDocument::open(path).ok()?;
    doc.extract_all_text().ok().filter(|s| !s.is_empty())
}

/// Extract text from a plain-text file for FTS indexing.
pub fn extract_text_file(path: &Path) -> Option<String> {
    let data = std::fs::read(path).ok()?;
    String::from_utf8(data).ok().filter(|s| !s.is_empty())
}
