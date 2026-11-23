package com.anatoliy.filemanager

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val lastUsed: Long,
) {
    val name: String get() = file.name
    val isPdf: Boolean get() = !isDirectory && name.endsWith(".pdf", ignoreCase = true)
    val isImage: Boolean get() = !isDirectory && isImageFile(file)
    val category: FileCategory by lazy { if (isDirectory) FileCategory.OTHER else FileCategory.of(file) }
}

object FileRepository {
    val rootDir: File get() = Environment.getExternalStorageDirectory()

    fun list(dir: File, showHidden: Boolean = false): List<FileItem> =
        dir.listFiles().orEmpty()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { it.toItem() }
            .sortedWith(
                compareByDescending<FileItem> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )

    fun lastUsedTime(file: File): Long {
        val modified = file.lastModified()
        return try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            maxOf(modified, attrs.lastAccessTime().toMillis())
        } catch (e: Exception) {
            modified
        }
    }

    suspend fun findUnused(
        root: File,
        olderThanDays: Int,
        onProgress: (scanned: Int) -> Unit = {},
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(olderThanDays.toLong())
        val androidDir = File(root, "Android").absolutePath
        val result = mutableListOf<FileItem>()
        var scanned = 0

        root.walkTopDown()
            .onEnter { dir ->
                dir == root || (!dir.name.startsWith(".") && dir.absolutePath != androidDir)
            }
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file ->
                ensureActive()
                scanned++
                if (scanned % 200 == 0) onProgress(scanned)
                val used = lastUsedTime(file)
                if (used < threshold) result += file.toItem(used)
            }

        onProgress(scanned)
        result.sortedBy { it.lastUsed }
    }

    suspend fun delete(files: Collection<File>): Int = withContext(Dispatchers.IO) {
        files.count { !(if (it.isDirectory) it.deleteRecursively() else it.delete()) }
    }

    fun search(root: File, query: String, showHidden: Boolean = false, limit: Int = 1000): Flow<List<FileItem>> = flow {
        val androidDir = File(rootDir, "Android").absolutePath
        val found = mutableListOf<FileItem>()
        var emitted = 0
        var lastEmit = 0L
        val sorted = { found.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() }) }
        for (f in root.walkTopDown().onEnter { it == root || ((showHidden || !it.name.startsWith(".")) && it.absolutePath != androidDir) }) {
            currentCoroutineContext().ensureActive()
            if (f == root || (!showHidden && f.name.startsWith("."))) continue
            if (f.name.contains(query, ignoreCase = true)) {
                found += f.toItem()
                if (found.size >= limit) break
            }
            val now = System.currentTimeMillis()
            if (found.size != emitted && now - lastEmit > 250) {
                emit(sorted())
                emitted = found.size
                lastEmit = now
            }
        }
        emit(sorted())
    }.flowOn(Dispatchers.IO)

    private val badNameChars = Regex("""[\\/:*?"<>|]""")

    fun nameProblem(file: File, newName: String): String? {
        val name = newName.trim()
        return when {
            name.isEmpty() -> "Введи назву"
            name == "." || name == ".." -> "Недопустима назва"
            badNameChars.containsMatchIn(name) -> """Не можна використовувати символи \ / : * ? " < > |"""
            name == file.name -> null
            !name.equals(file.name, ignoreCase = true) && File(file.parentFile, name).exists() ->
                "Файл з такою назвою вже є"
            else -> null
        }
    }

    fun newFolderProblem(dir: File, name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Введи назву"
            n == "." || n == ".." -> "Недопустима назва"
            badNameChars.containsMatchIn(n) -> """Не можна використовувати символи \ / : * ? " < > |"""
            File(dir, n).exists() -> "Папка або файл з такою назвою вже є"
            else -> null
        }
    }

    fun suggestFolderName(dir: File, base: String = "Нова папка"): String {
        if (!File(dir, base).exists()) return base
        var i = 1
        while (File(dir, "$base ($i)").exists()) i++
        return "$base ($i)"
    }

    suspend fun createFolder(dir: File, name: String): File? = withContext(Dispatchers.IO) {
        File(dir, name.trim()).takeIf { it.mkdir() }
    }

    suspend fun rename(file: File, newName: String): File? = withContext(Dispatchers.IO) {
        val name = newName.trim()
        val target = File(file.parentFile, name)
        if (name == file.name) return@withContext file
        if (name.equals(file.name, ignoreCase = true)) {
            val temp = File(file.parentFile, ".rename_${System.nanoTime()}")
            if (!file.renameTo(temp)) return@withContext null
            return@withContext if (temp.renameTo(target)) target else { temp.renameTo(file); null }
        }
        if (file.renameTo(target)) target else null
    }

    data class TransferResult(val done: Int, val failed: Int, val skipped: Int)

    suspend fun transfer(files: List<File>, destDir: File, move: Boolean): TransferResult =
        withContext(Dispatchers.IO) {
            var done = 0
            var failed = 0
            var skipped = 0
            val dest = destDir.canonicalPath
            for (src in files) {
                ensureActive()
                if (!src.exists()) { failed++; continue }
                val srcPath = src.canonicalPath
                val intoItself = src.isDirectory &&
                    (dest == srcPath || dest.startsWith(srcPath + File.separator))
                val sameFolder = move && src.parentFile?.canonicalPath == dest
                if (intoItself || sameFolder) { skipped++; continue }

                val target = freeName(destDir, src)
                val ok = runCatching {
                    if (move) {
                        src.renameTo(target) || (src.copyRecursively(target) && src.deleteRecursively())
                    } else {
                        src.copyRecursively(target)
                    }
                }.getOrDefault(false)
                if (ok) done++ else failed++
            }
            TransferResult(done, failed, skipped)
        }

    fun freeName(dir: File, src: File): File {
        var target = File(dir, src.name)
        if (!target.exists()) return target
        val base = if (src.isDirectory) src.name else src.nameWithoutExtension
        val ext = if (src.isDirectory || src.extension.isEmpty()) "" else ".${src.extension}"
        var i = 1
        while (target.exists()) target = File(dir, "$base (${i++})$ext")
        return target
    }

    private fun File.toItem(lastUsed: Long = lastUsedTime(this)) = FileItem(
        file = this,
        isDirectory = isDirectory,
        size = if (isFile) length() else 0L,
        lastModified = lastModified(),
        lastUsed = lastUsed,
    )
}
