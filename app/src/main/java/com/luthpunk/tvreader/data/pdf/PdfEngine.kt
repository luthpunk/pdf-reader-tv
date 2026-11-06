package com.luthpunk.tvreader.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfEngine(private val context: Context) : AutoCloseable {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var pageCount: Int = 0
        private set

    // Memory cache for rendered pages (around 12-16 high res pages)
    private val memoryCache: LruCache<Int, Bitmap> = object : LruCache<Int, Bitmap>(16) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Bitmap recycling can be handled here if needed
        }
    }

    suspend fun open(filePath: String, uri: Uri? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            close()
            pfd = if (uri != null && uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")
            } else {
                val file = File(filePath)
                if (!file.exists()) return@withContext false
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            pfd?.let { descriptor ->
                renderer = PdfRenderer(descriptor)
                pageCount = renderer?.pageCount ?: 0
                return@withContext (pageCount > 0)
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1920, targetHeight: Int = 1080): Bitmap? =
        withContext(Dispatchers.IO) {
            if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

            // Check cache first
            memoryCache.get(pageIndex)?.let { return@withContext it }

            val rend = renderer ?: return@withContext null
            try {
                // PdfRenderer is NOT thread-safe: synchronize page rendering
                synchronized(rend) {
                    val page = rend.openPage(pageIndex)

                    val pageWidth = page.width
                    val pageHeight = page.height

                    // Scale to target TV screen dimension preserving aspect ratio
                    val scaleX = targetWidth.toFloat() / pageWidth.toFloat()
                    val scaleY = targetHeight.toFloat() / pageHeight.toFloat()
                    val scale = minOf(scaleX, scaleY).coerceAtLeast(1.0f)

                    val bitmapWidth = (pageWidth * scale).toInt()
                    val bitmapHeight = (pageHeight * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    // PDF pages often have transparent background, fill with white
                    bitmap.eraseColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    memoryCache.put(pageIndex, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun generateCoverThumbnail(outputFile: File): Boolean = withContext(Dispatchers.IO) {
        if (outputFile.exists() && outputFile.length() > 0) return@withContext true
        try {
            val thumbBitmap = renderPage(0, targetWidth = 480, targetHeight = 640) ?: return@withContext false
            FileOutputStream(outputFile).use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun close() {
        memoryCache.evictAll()
        try {
            renderer?.close()
        } catch (ignored: Exception) {}
        renderer = null

        try {
            pfd?.close()
        } catch (ignored: Exception) {}
        pfd = null
        pageCount = 0
    }
}
