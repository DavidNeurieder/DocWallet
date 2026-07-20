package com.librecrate.app.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Persists error reports to a LibreCrate folder under the app's Download
 * directory (Download/LibreCrate/errors.log) so failures can be analyzed
 * without adb/logcat. The app-specific Download directory is always writable
 * without storage permissions (scoped storage), and is accessible via a file
 * manager or `adb pull`. All writes happen off the main thread and are
 * best-effort: logging must never crash the app.
 */
object ErrorLogger {
    private const val TAG = "ErrorLogger"
    private const val LOG_FILE_NAME = "errors.log"
    private const val FOLDER_NAME = "LibreCrate"
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "error-logger").apply { isDaemon = false }
    }

    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val entry = buildEntry(tag, message, throwable)
        executor.execute {
            runCatching { writeEntry(context.applicationContext, entry) }
                .onFailure { Log.e(TAG, "Failed to write error log", it) }
        }
    }

    /** Synchronous variant: blocks until the entry is written. Use when the
     *  caller's process lifetime is uncertain (e.g. tests, crash handlers). */
    fun logNow(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        runCatching { writeEntry(context.applicationContext, buildEntry(tag, message, throwable)) }
            .onFailure { Log.e(TAG, "Failed to write error log", it) }
    }

    fun installGlobalHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(context, "Uncaught", "Crash on thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun targetFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(dir, "$FOLDER_NAME/$LOG_FILE_NAME").also { it.parentFile?.mkdirs() }
    }

    private fun writeEntry(context: Context, entry: String) {
        targetFile(context).appendText(entry)
    }

    private fun buildEntry(tag: String, message: String, throwable: Throwable?): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("========== $time ==========\n")
        sb.append("Tag: $tag\n")
        sb.append("Message: $message\n")
        throwable?.let {
            sb.append("Exception: ${it.javaClass.name}: ${it.message}\n")
            sb.append("Stacktrace:\n")
            sb.append(it.stackTraceToString())
            var cause = it.cause
            var depth = 0
            while (cause != null && depth < 5) {
                sb.append("\nCaused by: ${cause.javaClass.name}: ${cause.message}\n")
                sb.append(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }
        sb.append("\n")
        return sb.toString()
    }
}
