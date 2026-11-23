package com.anatoliy.filemanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatoliy.filemanager.openWithExternalApp
import com.anatoliy.filemanager.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val MAX_BYTES = 2 * 1024 * 1024

private class TextContent(val lines: List<String>, val truncated: Boolean, val charset: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(file: File, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var fontSize by rememberSaveable { mutableFloatStateOf(14f) }
    val content by produceState<TextContent?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { load(file) }.getOrNull() ?: TextContent(emptyList(), false, "") }
    }
    val mono = file.extension.lowercase() in setOf("json", "xml", "log", "csv", "tsv", "yml", "yaml", "ini", "cfg", "conf",
        "sh", "bat", "py", "kt", "kts", "java", "js", "ts", "css", "sql", "gradle", "properties")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { fontSize = (fontSize - 2f).coerceAtLeast(10f) }) {
                        Icon(Icons.Filled.TextDecrease, contentDescription = "Менший шрифт")
                    }
                    IconButton(onClick = { fontSize = (fontSize + 2f).coerceAtMost(28f) }) {
                        Icon(Icons.Filled.TextIncrease, contentDescription = "Більший шрифт")
                    }
                    IconButton(onClick = { shareFiles(context, listOf(file)) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поділитися")
                    }
                    IconButton(onClick = { openWithExternalApp(context, file) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Відкрити в іншому додатку")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val c = content
            when {
                c == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                c.lines.isEmpty() -> Text("Файл порожній або не читається", Modifier.align(Alignment.Center))
                else -> SelectionContainer {
                    LazyColumn(contentPadding = PaddingValues(12.dp)) {
                        itemsIndexed(c.lines) { _, line ->
                            Text(
                                line.ifEmpty { " " },
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.35f).sp,
                                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (c.truncated) {
                            item {
                                Text(
                                    "… файл завеликий, показано перші 2 МБ. Повністю — «Відкрити в іншому додатку».",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun load(file: File): TextContent {
    val bytes = file.inputStream().use { input ->
        val buf = ByteArray(minOf(file.length(), MAX_BYTES.toLong()).toInt())
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) break
            read += n
        }
        buf.copyOf(read)
    }
    val truncated = file.length() > MAX_BYTES
    val (text, charset) = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString() to "UTF-8"
    } catch (e: CharacterCodingException) {
        if (truncated) String(bytes, Charsets.UTF_8) to "UTF-8"
        else String(bytes, Charset.forName("windows-1251")) to "windows-1251"
    }
    return TextContent(text.removePrefix("﻿").lines(), truncated, charset)
}
