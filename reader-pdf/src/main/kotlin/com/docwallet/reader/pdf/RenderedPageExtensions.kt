package com.docwallet.reader.pdf

import android.graphics.Bitmap
import com.docwallet.vault.reader.RenderedPage
import java.nio.ByteBuffer

fun RenderedPage.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixelData))
    return bitmap
}
