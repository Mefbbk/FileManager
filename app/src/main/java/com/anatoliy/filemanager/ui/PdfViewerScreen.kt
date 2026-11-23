package com.anatoliy.filemanager.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.PdfDocument
import com.anatoliy.filemanager.openWithExternalApp
import com.anatoliy.filemanager.shareFiles
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(file: File, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val document = remember(file) { runCatching { PdfDocument(file) } }
    DisposableEffect(document) {
        onDispose { document.getOrNull()?.close() }
    }

    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val pageCount = document.getOrNull()?.pageCount ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (pageCount > 0) {
                        Text("$currentPage / $pageCount", Modifier.padding(end = 4.dp))
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
        val doc = document.getOrNull()
        if (doc == null) {
            Box(Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
                Text(
                    "Не вдалося відкрити PDF: ${document.exceptionOrNull()?.message ?: "невідома помилка"}",
                    Modifier.align(Alignment.Center),
                )
            }
        } else {
            BoxWithConstraints(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val pagePadding = 8.dp
                val widthPx = with(LocalDensity.current) { (maxWidth - pagePadding * 2).roundToPx() }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(pagePadding),
                    verticalArrangement = Arrangement.spacedBy(pagePadding),
                ) {
                    items(count = doc.pageCount) { index ->
                        PdfPage(doc, index, widthPx)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDocument, index: Int, widthPx: Int) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, widthPx) {
        bitmap = doc.renderPage(index, widthPx)
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Сторінка ${index + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
