package com.docwallet.ui.common

import android.graphics.Bitmap
import android.util.LruCache

object ThumbnailCache {
    private const val MAX_SIZE = 5 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
