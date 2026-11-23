package com.anatoliy.filemanager

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

class PdfDocument(file: File) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()

    private val renderer = PdfRenderer(
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    )

    @Volatile
    private var closed = false

    val pageCount: Int = renderer.pageCount

    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap? = withContext(dispatcher) {
        if (closed) return@withContext null
        renderer.openPage(index).use { page ->
            val scale = targetWidth.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(
                targetWidth,
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        executor.execute {
            closed = true
            renderer.close()
        }
        executor.shutdown()
    }
}
