package com.anatoliy.filemanager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

enum class FileCategory(val label: String, val icon: ImageVector) {
    ALL("Усі", Icons.Filled.SelectAll),
    IMAGES("Фото", Icons.Filled.Image),
    VIDEO("Відео", Icons.Filled.VideoFile),
    AUDIO("Музика", Icons.Filled.AudioFile),
    DOCUMENTS("Документи", Icons.Filled.Description),
    FB2("FB2", @Suppress("DEPRECATION") Icons.Filled.MenuBook),
    ARCHIVES("Архіви", Icons.Filled.FolderZip),
    APK("APK", Icons.Filled.Android),
    OTHER("Інше", @Suppress("DEPRECATION") Icons.Filled.InsertDriveFile);

    val isVisual: Boolean get() = this == IMAGES || this == VIDEO

    companion object {
        fun of(file: File): FileCategory {
            val ext = file.extension.lowercase()
            return when {
                isImageFile(file) -> IMAGES
                ext == "fb2" || file.name.endsWith(".fb2.zip", ignoreCase = true) -> FB2
                ext in videoExtensions -> VIDEO
                ext in audioExtensions -> AUDIO
                ext in documentExtensions -> DOCUMENTS
                ext in archiveExtensions -> ARCHIVES
                ext == "apk" || ext == "apks" || ext == "xapk" -> APK
                else -> OTHER
            }
        }
    }
}

val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts", "wmv", "flv")
val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "amr", "wma", "mid")
private val documentExtensions = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
    "txt", "rtf", "csv", "md", "epub", "djvu", "html", "htm", "json", "xml",
)
private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
