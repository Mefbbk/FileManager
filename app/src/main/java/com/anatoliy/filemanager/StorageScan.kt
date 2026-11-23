package com.anatoliy.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.PriorityQueue

data class SpaceReport(
    val totalBytes: Long,
    val freeBytes: Long,
    val scannedBytes: Long,
    val fileCount: Int,
    val byCategory: List<Triple<FileCategory, Long, Int>>,
    val topFolders: List<Pair<File, Long>>,
    val largestFiles: List<FileItem>,
)

data class DuplicateGroup(val size: Long, val files: List<FileItem>) {
    val wasted: Long get() = size * (files.size - 1)
}

object StorageScan {
    private fun walk(root: File): Sequence<File> {
        val androidDir = File(root, "Android").path
        return root.walkTopDown().onEnter { it == root || it.path != androidDir }.filter { it.isFile }
    }

    suspend fun analyze(root: File, onProgress: (Int) -> Unit): SpaceReport = withContext(Dispatchers.IO) {
        val byCat = HashMap<FileCategory, LongArray>()
        val topLevel = HashMap<String, Long>()
        val largest = PriorityQueue<File>(compareBy { it.length() })
        var total = 0L
        var count = 0
        val rootPath = root.path + File.separator
        for (f in walk(root)) {
            ensureActive()
            val len = f.length()
            total += len
            count++
            if (count % 500 == 0) onProgress(count)
            val cat = FileCategory.of(f)
            byCat.getOrPut(cat) { LongArray(2) }.let { it[0] += len; it[1]++ }
            val first = f.path.removePrefix(rootPath).substringBefore(File.separator, "")
            if (first.isNotEmpty() && first != f.name) topLevel[first] = (topLevel[first] ?: 0L) + len
            largest.add(f)
            if (largest.size > 100) largest.poll()
        }
        onProgress(count)
        val stat = StorageRoot(root, "", false)
        SpaceReport(
            totalBytes = stat.totalBytes,
            freeBytes = stat.freeBytes,
            scannedBytes = total,
            fileCount = count,
            byCategory = byCat.map { (c, v) -> Triple(c, v[0], v[1].toInt()) }.sortedByDescending { it.second },
            topFolders = topLevel.map { (name, size) -> File(root, name) to size }.sortedByDescending { it.second }.take(12),
            largestFiles = largest.toList().sortedByDescending { it.length() }.map { it.toItem() },
        )
    }

    suspend fun duplicates(
        root: File,
        onProgress: (phase: String, done: Int, total: Int) -> Unit,
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val bySize = HashMap<Long, MutableList<File>>()
        var scanned = 0
        for (f in walk(root)) {
            ensureActive()
            if (f.name.startsWith(".")) continue
            val len = f.length()
            if (len < 1024) continue
            bySize.getOrPut(len) { mutableListOf() }.add(f)
            if (++scanned % 500 == 0) onProgress("Сканую", scanned, 0)
        }
        val candidates = bySize.values.filter { it.size > 1 }
        val toCheck = candidates.sumOf { it.size }
        var checked = 0
        val groups = mutableListOf<DuplicateGroup>()
        for (sameSize in candidates) {
            ensureActive()
            val byQuick = sameSize.groupBy { f ->
                checked++
                if (checked % 20 == 0) onProgress("Порівнюю", checked, toCheck)
                runCatching { quickHash(f) }.getOrNull()
            }.filterKeys { it != null }.values.filter { it.size > 1 }
            for (quickGroup in byQuick) {
                val byFull = quickGroup.groupBy { f -> runCatching { fullHash(f) }.getOrNull() }
                    .filterKeys { it != null }.values.filter { it.size > 1 }
                for (same in byFull) {
                    groups += DuplicateGroup(same.first().length(), same.map { it.toItem() }.sortedBy { it.lastModified })
                }
            }
        }
        onProgress("Готово", toCheck, toCheck)
        groups.sortedByDescending { it.wasted }
    }

    private const val CHUNK = 64 * 1024

    private fun quickHash(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { input ->
            val buf = ByteArray(CHUNK)
            md.update(buf, 0, input.read(buf).coerceAtLeast(0))
            val len = f.length()
            if (len > 2L * CHUNK) {
                input.skip(len - 2L * CHUNK)
                md.update(buf, 0, input.read(buf).coerceAtLeast(0))
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fullHash(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.toItem() = FileItem(this, false, length(), lastModified(), lastModified())
}
