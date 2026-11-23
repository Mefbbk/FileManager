package com.anatoliy.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object Zip {
    fun isZip(file: File) = file.isFile && file.extension.equals("zip", ignoreCase = true)

    suspend fun extract(zip: File): File? = withContext(Dispatchers.IO) {
        val parent = zip.parentFile ?: return@withContext null
        val target = FileRepository.freeName(parent, File(zip.name.removeSuffix(".zip").removeSuffix(".ZIP")))
        for (charset in listOf(Charsets.UTF_8, Charset.forName("CP866"))) {
            val ok = runCatching { unpack(zip, target, charset) }.isSuccess
            if (ok) return@withContext target
            target.deleteRecursively()
        }
        null
    }

    private suspend fun unpack(zip: File, target: File, charset: Charset) {
        val root = target.canonicalPath + File.separator
        ZipFile(zip, charset).use { zf ->
            for (entry in zf.entries()) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val out = File(target, entry.name)
                require(out.canonicalPath.startsWith(root) || out.canonicalPath == target.canonicalPath)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    if (entry.time > 0) out.setLastModified(entry.time)
                }
            }
        }
        target.mkdirs()
    }

    suspend fun compress(files: List<File>, destDir: File): File? = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext null
        val baseName = if (files.size == 1) {
            val f = files.first()
            if (f.isDirectory) f.name else f.nameWithoutExtension
        } else "Архів"
        val dest = FileRepository.freeName(destDir, File("$baseName.zip"))
        val ok = runCatching {
            ZipOutputStream(dest.outputStream().buffered()).use { zos ->
                for (file in files) {
                    val base = file.parentFile ?: continue
                    file.walkTopDown().forEach { f ->
                        ensureActive()
                        if (f == dest) return@forEach
                        val name = f.relativeTo(base).invariantSeparatorsPath
                        if (f.isDirectory) {
                            zos.putNextEntry(ZipEntry("$name/").apply { time = f.lastModified() })
                            zos.closeEntry()
                        } else {
                            zos.putNextEntry(ZipEntry(name).apply { time = f.lastModified() })
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
        }.isSuccess
        if (ok) dest else { dest.delete(); null }
    }
}
