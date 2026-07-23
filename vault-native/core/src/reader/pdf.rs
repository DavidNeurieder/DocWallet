use super::{DocumentReader, ReaderError, ReaderMeta, RenderedPage};
use std::path::Path;

#[derive(Debug)]
pub struct PdfReader {
    inner: pdf_oxide::PdfDocument,
    page_count: u32,
}

impl PdfReader {
    pub fn open(path: &Path) -> Result<Self, ReaderError> {
        let doc = pdf_oxide::PdfDocument::open(path)
            .map_err(|e| ReaderError::OpenFailed(e.to_string()))?;
        let page_count = doc
            .page_count()
            .map_err(|e| ReaderError::ParseFailed(e.to_string()))?;
        Ok(Self {
            inner: doc,
            page_count: page_count as u32,
        })
    }
}

impl DocumentReader for PdfReader {
    fn page_count(&self) -> Result<u32, ReaderError> {
        Ok(self.page_count)
    }

    fn extract_text(&self, page_index: u32) -> Result<String, ReaderError> {
        self.inner
            .extract_text(page_index as usize)
            .map_err(|e| ReaderError::ExtractFailed(e.to_string()))
    }

    fn extract_all_text(&self) -> Result<String, ReaderError> {
        self.inner
            .extract_all_text()
            .map_err(|e| ReaderError::ExtractFailed(e.to_string()))
    }

    fn page_size(&self, page_index: u32) -> Result<(f32, f32), ReaderError> {
        let info = self.inner.get_page_info(page_index as usize)
            .map_err(|e| ReaderError::ExtractFailed(e.to_string()))?;
        Ok((info.media_box.width, info.media_box.height))
    }

    fn render_page(&self, page_index: u32, scale: f32) -> Result<RenderedPage, ReaderError> {
        let dpi = (150.0 * scale) as u32;
        let options = pdf_oxide::rendering::RenderOptions::with_dpi(dpi).as_raw();
        let img = pdf_oxide::rendering::render_page(&self.inner, page_index as usize, &options)
            .map_err(|e| ReaderError::RenderFailed(e.to_string()))?;
        Ok(RenderedPage {
            data: img.data,
            width: img.width,
            height: img.height,
        })
    }

    fn render_thumbnail(&self) -> Result<Vec<u8>, ReaderError> {
        let options = pdf_oxide::rendering::RenderOptions::with_dpi(72);
        let img = pdf_oxide::rendering::render_page(&self.inner, 0, &options)
            .map_err(|e| ReaderError::RenderFailed(e.to_string()))?;
        Ok(img.data)
    }

    fn metadata(&self) -> Result<ReaderMeta, ReaderError> {
        Ok(ReaderMeta {
            title: None,
            author: None,
            page_count: self.page_count,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::Path;

    fn fixture(name: &str) -> std::path::PathBuf {
        let dir = env!("CARGO_MANIFEST_DIR");
        Path::new(dir).join("tests").join("fixtures").join(name)
    }

    #[test]
    fn test_pdf_reader_open_single() {
        let r = PdfReader::open(&fixture("test_1page.pdf")).unwrap();
        assert_eq!(r.page_count, 1);
    }

    #[test]
    fn test_pdf_reader_open_multi() {
        let r = PdfReader::open(&fixture("test_2page.pdf")).unwrap();
        assert_eq!(r.page_count, 2);
    }

    #[test]
    fn test_pdf_reader_trait_dispatch() {
        let r: Box<dyn DocumentReader> = Box::new(
            PdfReader::open(&fixture("test_1page.pdf")).unwrap(),
        );
        assert_eq!(r.page_count().unwrap(), 1);
        assert_eq!(r.extract_text(0).unwrap().trim(), "Test PDF content");
        let (w, h) = r.page_size(0).unwrap();
        assert!((w - 612.0).abs() < 0.1);
        assert!((h - 792.0).abs() < 0.1);
    }

    #[test]
    fn test_open_nonexistent() {
        let err = PdfReader::open(Path::new("/nonexistent/test.pdf")).unwrap_err();
        assert!(matches!(err, ReaderError::OpenFailed(_)));
    }
}
