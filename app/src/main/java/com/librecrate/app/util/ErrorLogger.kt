package com.librecrate.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors

object ErrorLogger {
    private const val TAG = "ErrorLogger"
    private const val FOLDER_NAME = "LibreCrate"
    private const val MAX_FILES = 500
    private val counter = AtomicInteger(0)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "error-logger").apply { isDaemon = false }
    }

    fun logException(context: Context?, tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val ctx = context ?: resolveContext() ?: return
        executor.execute {
            runCatching { writeEntry(ctx, buildEntry(tag, message, throwable)) }
                .onFailure { Log.e(TAG, "Failed to write error log", it) }
        }
    }

    fun logWarning(context: Context?, tag: String, message: String, throwable: Throwable? = null) {
        val ctx = context ?: resolveContext() ?: return
        executor.execute {
            runCatching { writeEntry(ctx, buildEntry(tag, message, throwable)) }
                .onFailure { Log.e(TAG, "Failed to write warning log", it) }
        }
    }

    fun logExceptionNow(context: Context?, tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val ctx = context ?: resolveContext() ?: return
        runCatching { writeEntry(ctx, buildEntry(tag, message, throwable)) }
            .onFailure { Log.e(TAG, "Failed to write error log", it) }
    }

    fun installGlobalHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logExceptionNow(context, "Uncaught", "Crash on thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun resolveContext(): Context? {
        return try {
            val cls = Class.forName("com.librecrate.app.LibreCrateApplication")
            cls.getField("instance").get(null) as? Context
        } catch (_: Exception) { null }
    }

    private fun targetDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val result = File(dir, FOLDER_NAME)
        result.mkdirs()
        return result
    }

    private fun writeEntry(context: Context, entry: String) {
        val dir = targetDir(context)
        val time = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val seq = counter.getAndIncrement() % 10000
        val file = File(dir, "error-$time-$seq.log")
        file.writeText(entry)
        trimOldFiles(dir)
    }

    private fun trimOldFiles(dir: File) {
        val files = dir.listFiles()?.filter { it.name.startsWith("error-") && it.name.endsWith(".log") }
            ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.dropLast(MAX_FILES).forEach { it.delete() }
    }

    private fun deviceInfo(context: Context): String {
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) { "?" }
        return "App: LibreCrate $versionName (API ${Build.VERSION.SDK_INT}, ${Build.MODEL})"
    }

    private fun buildEntry(tag: String, message: String, throwable: Throwable?): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("Event: $message\n")
        sb.append("Tag: $tag\n")
        sb.append("Time: $time\n")
        resolveContext()?.let { sb.append("${deviceInfo(it)}\n") }
        throwable?.let {
            sb.append("Exception: ${it.javaClass.name}: ${it.message}\n")
            sb.append("Stacktrace:\n${it.stackTraceToString()}\n")
            var cause = it.cause
            var depth = 0
            while (cause != null && depth < 5) {
                sb.append("Caused by: ${cause.javaClass.name}: ${cause.message}\n${cause.stackTraceToString()}\n")
                cause = cause.cause
                depth++
            }
        }
        return sb.toString()
    }
}
