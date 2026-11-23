package com.anatoliy.filemanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anatoliy.filemanager.FileRepository
import com.anatoliy.filemanager.formatDate
import com.anatoliy.filemanager.formatSize
import com.anatoliy.filemanager.openWithExternalApp
import com.anatoliy.filemanager.shareFiles
import java.io.File

private val periods = listOf(
    30 to "1 міс.",
    90 to "3 міс.",
    180 to "6 міс.",
    365 to "1 рік",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnusedFilesScreen(
    onBack: () -> Unit,
    onOpenPdf: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    vm: UnusedFilesViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val root = FileRepository.rootDir
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.scanIfNeeded() }

    val results = vm.results
    val selected = vm.selected
    val selectedItems = vm.selectedItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Давно не використовувані") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { shareFiles(context, selectedItems.map { it.file }) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Поділитися вибраними")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Видалити вибрані")
                        }
                    }
                    if (results.isNotEmpty()) {
                        IconButton(onClick = vm::toggleAll) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Вибрати все")
                        }
                    }
                    IconButton(onClick = vm::scan, enabled = !vm.scanning) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Сканувати знову")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                periods.forEach { (value, label) ->
                    FilterChip(
                        selected = vm.days == value,
                        onClick = { vm.setPeriod(value) },
                        label = { Text("> $label") },
                    )
                }
            }

            Text(
                text = when {
                    vm.scanning -> "Сканування… перевірено файлів: ${vm.scannedCount}"
                    selected.isNotEmpty() ->
                        "Вибрано: ${selectedItems.size} · ${formatSize(context, selectedItems.sumOf { it.size })}"
                    else -> "Знайдено: ${results.size} · ${formatSize(context, results.sumOf { it.size })}"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            Box(Modifier.fillMaxSize()) {
                when {
                    vm.scanning -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    results.isEmpty() -> Text("Таких файлів немає", Modifier.align(Alignment.Center))
                    else -> LazyColumn {
                        items(results, key = { it.file.absolutePath }) { item ->
                            val path = item.file.absolutePath
                            val folder = item.file.parentFile
                                ?.toRelativeString(root)?.ifEmpty { "/" } ?: ""
                            FileRow(
                                item = item,
                                subtitle = "${formatDate(item.lastUsed)} · " +
                                    "${formatSize(context, item.size)} · $folder",
                                onClick = {
                                    when {
                                        item.isPdf -> onOpenPdf(item.file)
                                        item.isImage -> onOpenImage(item.file)
                                        else -> openWithExternalApp(context, item.file)
                                    }
                                },
                                trailing = {
                                    Checkbox(
                                        checked = path in selected,
                                        onCheckedChange = { vm.toggle(path, it) },
                                    )
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Видалити файли?") },
            text = {
                Text(
                    "Буде видалено файлів: ${selectedItems.size} " +
                        "(${formatSize(context, selectedItems.sumOf { it.size })}). " +
                        "Цю дію не можна скасувати."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSelected { deleted, failed ->
                        val message = if (failed == 0) "Видалено файлів: $deleted"
                        else "Видалено: $deleted, не вдалося: $failed"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Видалити") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") }
            },
        )
    }
}
