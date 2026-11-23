package com.anatoliy.filemanager.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.formatSize
import com.anatoliy.filemanager.isImageFile
import com.anatoliy.filemanager.mimeTypeOf
import com.anatoliy.filemanager.videoExtensions
import com.anatoliy.filemanager.audioExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun PropertiesDialog(files: List<File>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rows by produceState<List<Pair<String, String>>?>(null, files) {
        value = withContext(Dispatchers.IO) { describe(context, files) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text(if (files.size == 1) files.first().name else "Вибрано: ${files.size}") },
        text = {
            val list = rows
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (list == null) {
                        Text("Рахую…")
                    } else {
                        list.forEach { (label, value) ->
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрити") } },
    )
}

private fun describe(context: Context, files: List<File>): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    var bytes = 0L
    var fileCount = 0
    var folderCount = 0
    for (f in files) {
        if (f.isDirectory) {
            f.walkTopDown().forEach {
                if (it == f) return@forEach
                if (it.isFile) { bytes += it.length(); fileCount++ } else folderCount++
            }
        } else {
            bytes += f.length()
        }
    }

    if (files.size == 1) {
        val f = files.first()
        rows += "Розташування" to (f.parentFile?.path ?: "/")
        rows += "Тип" to if (f.isDirectory) "Папка" else "${mimeTypeOf(f)} (.${f.extension.lowercase()})"
        rows += "Розмір" to sizeText(context, bytes)
        if (f.isDirectory) rows += "Вміст" to "Файлів: $fileCount, папок: $folderCount"
        rows += "Змінено" to dateTime.format(Instant.ofEpochMilli(f.lastModified()))
        if (f.isFile) rows += mediaDetails(f)
        rows += "Доступ" to buildString {
            append(if (f.canRead()) "читання" else "немає читання")
            append(if (f.canWrite()) ", запис" else ", лише читання")
            if (f.isHidden) append(", прихований")
        }
    } else {
        val parents = files.mapNotNull { it.parentFile?.path }.distinct()
        if (parents.size == 1) rows += "Розташування" to parents.first()
        rows += "Елементів" to "Файлів: ${files.count { it.isFile }}, папок: ${files.count { it.isDirectory }}"
        rows += "Загальний розмір" to sizeText(context, bytes)
        if (folderCount + fileCount > 0) rows += "Усередині папок" to "Файлів: $fileCount, папок: $folderCount"
    }
    return rows
}

private fun sizeText(context: Context, bytes: Long) =
    "${formatSize(context, bytes)} (${"%,d".format(bytes).replace(',', ' ')} байт)"

private fun mediaDetails(f: File): List<Pair<String, String>> {
    val ext = f.extension.lowercase()
    val rows = mutableListOf<Pair<String, String>>()
    if (isImageFile(f)) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth > 0) {
            val mp = o.outWidth.toLong() * o.outHeight / 1_000_000.0
            rows += "Зображення" to "${o.outWidth} × ${o.outHeight} (%.1f Мп)".format(mp)
        }
    } else if (ext in videoExtensions || ext in audioExtensions) {
        val r = MediaMetadataRetriever()
        runCatching {
            r.setDataSource(f.path)
            fun meta(key: Int) = r.extractMetadata(key)?.takeIf { it.isNotBlank() }
            meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                rows += "Тривалість" to duration(it)
            }
            val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (w != null && h != null) rows += "Відео" to "$w × $h"
            meta(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { rows += "Назва" to it }
            meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { rows += "Виконавець" to it }
            meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { rows += "Альбом" to it }
            meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                rows += "Бітрейт" to "${it / 1000} кбіт/с"
            }
        }
        r.release()
    }
    return rows
}

private fun duration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
