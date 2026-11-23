package com.anatoliy.filemanager

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

fun formatDate(millis: Long): String = dateFormatter.format(Instant.ofEpochMilli(millis))

fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun isImageFile(file: File): Boolean = file.extension.lowercase() in imageExtensions

fun mimeTypeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

private fun contentUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

fun openWithExternalApp(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri(context, file), mimeTypeOf(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Відкрити за допомогою"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Немає додатка для цього типу файлів", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    val uris = files.map { contentUri(context, it) }
    val types = files.map(::mimeTypeOf).distinct()
    val mime = when {
        types.size == 1 -> types.first()
        types.map { it.substringBefore('/') }.distinct().size == 1 ->
            types.first().substringBefore('/') + "/*"
        else -> "*/*"
    }

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.apply {
        type = mime
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Поділитися"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Немає додатків, щоб поділитися", Toast.LENGTH_SHORT).show()
    }
}

private val textExtensions = setOf(
    "txt", "log", "json", "xml", "md", "csv", "tsv", "ini", "cfg", "conf", "yml", "yaml",
    "properties", "sh", "bat", "py", "kt", "kts", "java", "js", "ts", "css", "sql", "srt", "nfo", "gradle",
)

fun isTextFile(file: File): Boolean = file.extension.lowercase() in textExtensions

fun installApk(context: Context, file: File) {
    val pm = context.packageManager
    if (!pm.canRequestPackageInstalls()) {
        Toast.makeText(context, "Дозволь встановлення додатків з File Manager і натисни ще раз", Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
        )
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri(context, file), "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Не вдалося запустити встановлення", Toast.LENGTH_SHORT).show()
    }
}
