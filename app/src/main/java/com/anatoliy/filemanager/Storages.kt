package com.anatoliy.filemanager

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class StorageRoot(val dir: File, val label: String, val removable: Boolean) {
    val totalBytes: Long get() = runCatching { StatFs(dir.path).totalBytes }.getOrDefault(0L)
    val freeBytes: Long get() = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)
}

object Storages {
    fun list(context: Context): List<StorageRoot> {
        val sm = context.getSystemService(StorageManager::class.java)
        val result = mutableListOf<StorageRoot>()
        runCatching {
            for (volume in sm.storageVolumes) {
                val state = volume.state
                if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                val dir = directoryOf(volume) ?: continue
                if (!dir.canRead()) continue
                val label = when {
                    volume.isPrimary -> "Пам'ять пристрою"
                    else -> volume.getDescription(context)?.takeIf { it.isNotBlank() } ?: dir.name
                }
                result += StorageRoot(dir, label, volume.isRemovable)
            }
        }
        if (result.none { it.dir.path == FileRepository.rootDir.path }) {
            result.add(0, StorageRoot(FileRepository.rootDir, "Пам'ять пристрою", removable = false))
        }
        return result.sortedBy { it.removable }
    }

    private fun directoryOf(volume: StorageVolume): File? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory
        } else {
            runCatching { volume.javaClass.getMethod("getPathFile").invoke(volume) as File }.getOrNull()
        }

    fun rootOf(dir: File, roots: List<StorageRoot>): StorageRoot? =
        roots.filter { dir.path == it.dir.path || dir.path.startsWith(it.dir.path + File.separator) }
            .maxByOrNull { it.dir.path.length }

    fun quickFolders(): List<Pair<String, File>> {
        val root = FileRepository.rootDir
        return listOf(
            "Завантаження" to File(root, Environment.DIRECTORY_DOWNLOADS),
            "Камера" to File(root, "DCIM/Camera"),
            "Знімки екрана" to File(root, "Pictures/Screenshots"),
            "Зображення" to File(root, Environment.DIRECTORY_PICTURES),
            "Документи" to File(root, Environment.DIRECTORY_DOCUMENTS),
            "Музика" to File(root, Environment.DIRECTORY_MUSIC),
            "Фільми" to File(root, Environment.DIRECTORY_MOVIES),
            "Telegram" to File(root, "Download/Telegram"),
        ).filter { it.second.isDirectory }
    }
}
