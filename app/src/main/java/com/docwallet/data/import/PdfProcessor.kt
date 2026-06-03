package com.docwallet.data.import

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfProcessor : DocumentProcessor {

    override suspend fun process(input: File, mimeType: String): ProcessorResult = withContext(Dispatchers.IO) {
        PDDocument.load(input).use { doc ->
            val info = doc.documentInformation
            val title = info.title?.takeIf { it.isNotBlank() } ?: input.nameWithoutExtension
            val author = info.author ?: ""
            val pageCount = doc.numberOfPages

            val textStripper = PDFTextStripper()
            val textContent = textStripper.getText(doc)

            val thumbnailBitmap = if (pageCount > 0) {
                val renderer = PDFRenderer(doc)
                val pageBitmap = renderer.renderImage(0)
                scaleToWidth(pageBitmap, 200)
            } else null

            ProcessorResult(
                title = title,
                author = author,
                pageCount = pageCount,
                textContent = textContent,
                thumbnailBitmap = thumbnailBitmap,
            )
        }
    }

    private fun scaleToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        val targetHeight = (targetWidth * source.height) / source.width
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight.coerceAtLeast(1), true)
    }
}
