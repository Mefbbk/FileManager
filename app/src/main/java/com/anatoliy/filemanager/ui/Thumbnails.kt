package com.anatoliy.filemanager.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.anatoliy.filemanager.Fb2
import com.anatoliy.filemanager.FileCategory
import com.anatoliy.filemanager.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

private const val THUMB_PX = 320

private enum class PreviewKind { IMAGE, VIDEO, AUDIO, APK, PDF, FB2 }

private fun previewKind(item: FileItem): PreviewKind? = when {
    item.isDirectory -> null
    item.isImage -> PreviewKind.IMAGE
    item.isPdf -> PreviewKind.PDF
    item.category == FileCategory.VIDEO -> PreviewKind.VIDEO
    item.category == FileCategory.AUDIO -> PreviewKind.AUDIO
    item.category == FileCategory.FB2 -> PreviewKind.FB2
    item.file.extension.equals("apk", ignoreCase = true) -> PreviewKind.APK
    else -> null
}

fun hasPreview(item: FileItem): Boolean = previewKind(item) != null

private val cache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private val noPreview = Collections.synchronizedSet(HashSet<String>())

private val decodeLimit = Semaphore(4)

private fun keyOf(file: File) = "${file.absolutePath}:${file.lastModified()}"

fun cachedThumbnail(file: File): Bitmap? = cache.get(keyOf(file))

private suspend fun loadThumbnail(context: Context, file: File, kind: PreviewKind): Bitmap? {
    val key = keyOf(file)
    cache.get(key)?.let { return it }
    if (key in noPreview) return null
    return decodeLimit.withPermit {
        withContext(Dispatchers.IO) {
            cache.get(key) ?: runCatching { decode(context, file, kind) }.getOrNull()
                .also { if (it != null) cache.put(key, it) else noPreview.add(key) }
        }
    }
}

private suspend fun decode(context: Context, file: File, kind: PreviewKind): Bitmap? {
    val size = Size(THUMB_PX, THUMB_PX)
    val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    return when (kind) {
        PreviewKind.IMAGE -> if (q) ThumbnailUtils.createImageThumbnail(file, size, null) else loadBitmap(file, THUMB_PX)
        PreviewKind.VIDEO -> if (q) ThumbnailUtils.createVideoThumbnail(file, size, null) else {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
        }
        PreviewKind.AUDIO -> audioCover(file)
        PreviewKind.APK -> apkIcon(context, file)
        PreviewKind.PDF -> pdfFirstPage(file)
        PreviewKind.FB2 -> Fb2.coverBytes(file)?.let(::decodeBytes)
    }
}

private fun audioCover(file: File): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        r.embeddedPicture?.let(::decodeBytes)
    } finally {
        r.release()
    }
}

private fun apkIcon(context: Context, file: File): Bitmap? {
    val pm = context.packageManager
    val info = pm.getPackageArchiveInfo(file.absolutePath, 0)?.applicationInfo ?: return null
    info.sourceDir = file.absolutePath
    info.publicSourceDir = file.absolutePath
    return info.loadIcon(pm).toBitmap(THUMB_PX / 2, THUMB_PX / 2)
}

private fun pdfFirstPage(file: File): Bitmap? =
    PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
        if (r.pageCount == 0) return null
        r.openPage(0).use { page ->
            val scale = THUMB_PX.toFloat() / maxOf(page.width, page.height)
            val bmp = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            bmp.eraseColor(AColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

private fun decodeBytes(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
fun FileThumbnail(item: FileItem, modifier: Modifier = Modifier, iconSize: Dp) {
    val context = LocalContext.current.applicationContext
    val kind = previewKind(item)
    val bitmap by produceState(
        initialValue = if (kind != null) cachedThumbnail(item.file) else null,
        item.file, item.lastModified,
    ) {
        if (kind != null && value == null) value = loadThumbnail(context, item.file, kind)
    }
    val fit = kind == PreviewKind.APK || kind == PreviewKind.PDF

    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                iconFor(item),
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
