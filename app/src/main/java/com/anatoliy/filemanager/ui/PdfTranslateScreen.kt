package com.anatoliy.filemanager.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.FileRepository
import com.anatoliy.filemanager.PageLayout
import com.anatoliy.filemanager.PageTranslator
import com.anatoliy.filemanager.PdfDocument
import com.anatoliy.filemanager.TranslateLanguages
import com.anatoliy.filemanager.TranslationOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private const val PREF_TARGET = "translate_target"
private const val PREF_ON_PAGE = "translate_on_page"

private sealed interface PageState {
    data class Working(val message: String) : PageState
    data class Failed(val message: String) : PageState
    data object Empty : PageState
    data class Ready(val source: String, val layout: PageLayout, val sameLanguage: Boolean) : PageState
}

private fun translatedDestination(context: Context, file: File, target: String): File {
    val inCache = file.canonicalPath.startsWith(context.cacheDir.canonicalPath)
    val parent = file.parentFile
    val dir = if (!inCache && parent != null && parent.canWrite()) {
        parent
    } else {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
    }
    return FileRepository.freeName(dir, File("${file.nameWithoutExtension} ($target).pdf"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfTranslateScreen(file: File, doc: PdfDocument, startPage: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var target by remember { mutableStateOf(prefs.getString(PREF_TARGET, null) ?: TranslateLanguages.all.first().first) }
    var sourceOverride by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var onPage by remember { mutableStateOf(prefs.getBoolean(PREF_ON_PAGE, true)) }

    var exportJob by remember { mutableStateOf<Job?>(null) }
    var exportPage by remember { mutableIntStateOf(0) }
    var savedFile by remember { mutableStateOf<File?>(null) }

    val engine = remember(file) { PageTranslator(context, file, doc) }
    DisposableEffect(engine) {
        onDispose {
            exportJob?.cancel()
            engine.close()
        }
    }

    val opened = savedFile
    if (opened != null) {
        PdfViewerScreen(opened, onBack = { savedFile = null })
        return
    }

    BackHandler(onBack = onClose)

    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))) {
        doc.pageCount
    }

    fun startExport() {
        val dest = translatedDestination(context, file, target)
        exportPage = 0
        exportJob = scope.launch {
            try {
                TranslationOverlay.exportPdf(engine, dest, sourceOverride, target) { exportPage = it }
                Toast.makeText(context, "Збережено: ${dest.absolutePath}", Toast.LENGTH_LONG).show()
                savedFile = dest
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toast.makeText(context, "Не вдалося зберегти: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            } finally {
                exportJob = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Переклад")
                        Text(
                            "Сторінка ${pagerState.currentPage + 1} / ${doc.pageCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onPage = !onPage
                        prefs.edit().putBoolean(PREF_ON_PAGE, onPage).apply()
                    }) {
                        if (onPage) {
                            Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Показати лише текст")
                        } else {
                            Icon(Icons.Filled.Description, contentDescription = "Показати на сторінці")
                        }
                    }
                    IconButton(onClick = { startExport() }, enabled = exportJob == null) {
                        Icon(Icons.Filled.Save, contentDescription = "Зберегти перекладений PDF")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguagePicker(
                    label = sourceOverride?.let { TranslateLanguages.name(it) } ?: "Авто",
                    includeAuto = true,
                    onPick = { sourceOverride = it },
                )
                Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                LanguagePicker(
                    label = TranslateLanguages.name(target),
                    includeAuto = false,
                    onPick = { code ->
                        if (code != null) {
                            target = code
                            prefs.edit().putString(PREF_TARGET, code).apply()
                        }
                    },
                )
                Box(Modifier.weight(1f))
                FilterChip(
                    selected = showOriginal,
                    onClick = { showOriginal = !showOriginal },
                    label = { Text("Оригінал") },
                )
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                TranslatedPage(
                    page = page,
                    engine = engine,
                    sourceOverride = sourceOverride,
                    target = target,
                    showOriginal = showOriginal,
                    onPage = onPage,
                )
            }
        }
    }

    if (exportJob != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Збереження перекладу") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Сторінка ${(exportPage + 1).coerceAtMost(doc.pageCount)} з ${doc.pageCount}")
                    LinearProgressIndicator(
                        progress = { exportPage.toFloat() / doc.pageCount.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { exportJob?.cancel() }) { Text("Скасувати") }
            },
        )
    }
}

@Composable
private fun LanguagePicker(label: String, includeAuto: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (includeAuto) {
                DropdownMenuItem(text = { Text("Авто") }, onClick = { open = false; onPick(null) })
            }
            TranslateLanguages.all.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(code) })
            }
        }
    }
}

@Composable
private fun TranslatedPage(
    page: Int,
    engine: PageTranslator,
    sourceOverride: String?,
    target: String,
    showOriginal: Boolean,
    onPage: Boolean,
) {
    var state by remember(page) { mutableStateOf<PageState>(PageState.Working("Читання тексту…")) }
    val translated = remember(page) { mutableStateListOf<String>() }
    var attempt by remember(page) { mutableIntStateOf(0) }

    LaunchedEffect(page, sourceOverride, target, attempt) {
        translated.clear()
        try {
            val layout = engine.layout(page) { state = PageState.Working(it) }
            if (layout.blocks.isEmpty()) {
                state = PageState.Empty
                return@LaunchedEffect
            }
            val source = engine.source(page, sourceOverride) { state = PageState.Working(it) }
            if (source == null) {
                state = PageState.Failed("Не вдалося визначити мову сторінки. Виберіть мову оригіналу вгорі.")
                return@LaunchedEffect
            }
            val ready = PageState.Ready(source, layout, sameLanguage = source == target)
            state = ready
            if (source == target) return@LaunchedEffect
            engine.translate(
                page, source, target,
                onStatus = { state = PageState.Working(it) },
                onBlock = {
                    if (state != ready) state = ready
                    translated += it
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            state = PageState.Failed("Помилка: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is PageState.Working -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
            }

            is PageState.Failed -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(s.message)
                OutlinedButton(onClick = { attempt++ }) { Text("Спробувати ще") }
            }

            PageState.Empty -> Text(
                "На цій сторінці немає тексту.",
                Modifier.align(Alignment.Center).padding(32.dp),
            )

            is PageState.Ready -> if (onPage) {
                OverlayPage(page, engine, s, translated, showOriginal)
            } else {
                TextPage(s, translated, showOriginal)
            }
        }
    }
}

@Composable
private fun StatusLine(state: PageState.Ready, translated: List<String>, showOriginal: Boolean) {
    val total = state.layout.blocks.size
    val translating = !state.sameLanguage && !showOriginal && translated.size < total
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (translating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            buildString {
                append("Мова оригіналу: ")
                append(TranslateLanguages.name(state.source))
                if (state.layout.ocr) append(" · розпізнано з зображення")
                if (state.sameLanguage) append(" · перекладати не потрібно")
                if (translating) append(" · переклад ${translated.size} / $total")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverlayPage(
    page: Int,
    engine: PageTranslator,
    state: PageState.Ready,
    translated: List<String>,
    showOriginal: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val renderWidth = (constraints.maxWidth * 2).coerceIn(800, 2400)
        var base by remember(page, renderWidth) { mutableStateOf<Bitmap?>(null) }
        var image by remember(page) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(page, renderWidth) {
            base = engine.renderPage(page, renderWidth)
        }
        val count = translated.size
        LaunchedEffect(base, count, showOriginal, state) {
            val b = base ?: return@LaunchedEffect
            image = if (showOriginal || state.sameLanguage || count == 0) {
                b
            } else {
                TranslationOverlay.render(b, state.layout.blocks, translated.toList())
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            StatusLine(state, translated, showOriginal)
            val img = image
            if (img != null) {
                Image(
                    bitmap = img.asImageBitmap(),
                    contentDescription = "Сторінка ${page + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / 1.414f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            Text(
                "Щоб збільшити, збережіть переклад (іконка дискети) — він відкриється як звичайний PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun TextPage(state: PageState.Ready, translated: List<String>, showOriginal: Boolean) {
    val clipboard = LocalClipboardManager.current
    val original = state.layout.blocks.map { it.text }
    val paragraphs = if (!state.sameLanguage && !showOriginal) translated else original

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { StatusLine(state, translated, showOriginal) }
                IconButton(onClick = { clipboard.setText(AnnotatedString(paragraphs.joinToString("\n\n"))) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копіювати текст", Modifier.size(20.dp))
                }
            }
        }
        items(paragraphs) { paragraph ->
            SelectionContainer {
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
            }
        }
    }
}
