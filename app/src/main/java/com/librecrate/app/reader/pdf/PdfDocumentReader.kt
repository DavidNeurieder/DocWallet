package com.librecrate.app.reader.pdf

import android.graphics.Bitmap
import com.librecrate.app.vault.reader.DocumentReader
import com.librecrate.app.vault.reader.RenderConfig
import com.librecrate.app.vault.reader.RenderedPage
import com.librecrate.app.vault.reader.models.DocumentMetadata
import com.librecrate.app.vault.reader.models.ReaderLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.vault_native.Document as NativeDocument
import java.nio.ByteBuffer

class PdfDocumentReader(filePath: String) : DocumentReader {

    private val document = NativeDocument.open(filePath, "application/pdf")

    override val pageCount: Int by lazy { document.pageCount().toInt() }

    override val metadata: DocumentMetadata by lazy {
        DocumentMetadata(
            title = "",
            author = "",
            pageCount = pageCount,
        )
    }

    override fun currentLocation(): ReaderLocation {
        return ReaderLocation(pageIndex = 0)
    }

    fun renderPageBitmap(pageIndex: Int, targetWidthPx: Int? = null): Bitmap {
        val pageW = document.pageWidth(pageIndex.toUInt())
        val scale = if (targetWidthPx != null && targetWidthPx > 0 && pageW > 0f) {
            targetWidthPx * 72f / (pageW * 150f)
        } else {
            1.0f
        }
        val rawData = document.renderPage(pageIndex.toUInt(), scale)
        val width = document.renderPageWidth(pageIndex.toUInt(), scale)
        val height = document.renderPageHeight(pageIndex.toUInt(), scale)

        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rawData))
        compositeOverWhite(bitmap)
        return bitmap
    }

    override suspend fun renderPage(pageIndex: Int, config: RenderConfig): RenderedPage {
        return withContext(Dispatchers.IO) {
            val bitmap = renderPageBitmap(pageIndex, targetWidthPx = config.width)
            try {
                val buffer = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buffer)
                RenderedPage(
                    width = bitmap.width,
                    height = bitmap.height,
                    pixelData = buffer.array(),
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    override fun extractText(): String? {
        return document.extractAllText().takeIf { it.isNotBlank() }
    }

    override fun close() {
        document.destroy()
    }

    private fun compositeOverWhite(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val rowPixels = IntArray(w)
        var changed = false
        for (y in 0 until h) {
            bitmap.getPixels(rowPixels, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = rowPixels[x]
                val a = p ushr 24
                if (a != 0xFF) {
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val af = a / 255f
                    val nr = (r * af + 255f * (1f - af)).toInt().coerceIn(0, 255)
                    val ng = (g * af + 255f * (1f - af)).toInt().coerceIn(0, 255)
                    val nb = (b * af + 255f * (1f - af)).toInt().coerceIn(0, 255)
                    rowPixels[x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                    changed = true
                }
            }
            if (changed) {
                bitmap.setPixels(rowPixels, 0, w, 0, y, w, 1)
            }
        }
    }
}
