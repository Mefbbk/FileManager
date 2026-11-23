package com.anatoliy.filemanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow as TO
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import com.anatoliy.filemanager.shareFiles
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.FileCategory
import com.anatoliy.filemanager.FileItem
import com.anatoliy.filemanager.FileRepository
import com.anatoliy.filemanager.SortBy
import com.anatoliy.filemanager.SortOrder
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import com.anatoliy.filemanager.formatDate
import com.anatoliy.filemanager.formatSize
import com.anatoliy.filemanager.openWithExternalApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun visibleItems(list: List<FileItem>, filter: FileCategory) =
    list.filter { it.isDirectory || filter == FileCategory.ALL || it.category == filter }

@Composable
internal fun FilterBar(
    selected: FileCategory,
    counts: Map<FileCategory, Int>,
    onSelect: (FileCategory) -> Unit,
) {
    val visible = FileCategory.entries.filter {
        it == FileCategory.ALL || it == selected || (counts[it] ?: 0) > 0
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        items(visible, key = { it.name }) { category ->
            val isSelected = category == selected
            val count = if (category == FileCategory.ALL) counts.values.sum() else counts[category] ?: 0
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) FileCategory.ALL else category) },
                label = { Text(if (count > 0) "${category.label} $count" else category.label) },
                leadingIcon = {
                    Icon(category.icon, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaCell(
    item: FileItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, label = "inset")
    Box(
        Modifier
            .aspectRatio(1f)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        FileThumbnail(
            item,
            Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(if (selected) 8.dp else 4.dp)),
            iconSize = 40.dp,
        )
        if (item.category == FileCategory.VIDEO) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Відео",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp),
            )
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (selected) "Вибрано" else null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Black.copy(alpha = 0.25f),
                        CircleShape,
                    ),
            )
        }
    }
}

data class PendingTransfer(val files: List<File>, val move: Boolean)

internal fun selectedFiles(items: List<FileItem>?, selected: Set<String>): List<File> =
    items.orEmpty().filter { it.file.absolutePath in selected }.map { it.file }

@Composable
internal fun PasteBar(pending: PendingTransfer, onCancel: () -> Unit, onPaste: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (pending.move) "Перемістити: " else "Копіювати: ") + pending.files.size +
                    "\nВідкрий потрібну папку",
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TO.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel) { Text("Скасувати") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = onPaste) { Text("Вставити сюди") }
        }
    }
}

@Composable
internal fun BusyDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(text) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text("Зачекай, не закривай додаток")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchTopBar(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Закрити пошук")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистити")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
    )
}

@Composable
internal fun SortMenu(sort: SortOrder, onChange: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Сортування")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortBy.entries.forEach { by ->
                DropdownMenuItem(
                    text = { Text(by.label) },
                    leadingIcon = { RadioButton(selected = sort.by == by, onClick = null) },
                    onClick = {
                        onChange(
                            if (sort.by == by) sort.copy(descending = !sort.descending)
                            else SortOrder(by, by.defaultDescending)
                        )
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(directionLabel(sort)) },
                trailingIcon = {
                    Switch(checked = sort.descending, onCheckedChange = null)
                },
                onClick = { onChange(sort.copy(descending = !sort.descending)) },
            )
        }
    }
}

internal fun directionLabel(sort: SortOrder): String = when (sort.by) {
    SortBy.NAME, SortBy.TYPE -> if (sort.descending) "Я → А" else "А → Я"
    SortBy.DATE -> if (sort.descending) "Спочатку нові" else "Спочатку старі"
    SortBy.SIZE -> if (sort.descending) "Спочатку великі" else "Спочатку малі"
}

@Composable
internal fun RenameDialog(file: File, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val nameEnd = if (file.isDirectory || file.extension.isEmpty()) file.name.length
    else file.name.length - file.extension.length - 1
    var value by remember {
        mutableStateOf(TextFieldValue(file.name, selection = TextRange(0, nameEnd)))
    }
    val problem = FileRepository.nameProblem(file, value.text)
    val newExt = File(value.text.trim()).extension
    val extChanged = !file.isDirectory && problem == null && !newExt.equals(file.extension, ignoreCase = true)
    val canSave = problem == null && value.text.trim() != file.name
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (file.isDirectory) "Перейменувати папку" else "Перейменувати файл") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = problem != null,
                supportingText = {
                    when {
                        problem != null -> Text(problem)
                        extChanged -> Text("Увага: змінюється розширення файлу")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(value.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text.trim()) }, enabled = canSave) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

@Composable
internal fun NewFolderDialog(dir: File, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember {
        val suggested = FileRepository.suggestFolderName(dir)
        mutableStateOf(TextFieldValue(suggested, selection = TextRange(0, suggested.length)))
    }
    val problem = FileRepository.newFolderProblem(dir, value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
        title = { Text("Нова папка") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = problem != null,
                supportingText = { problem?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (problem == null) onConfirm(value.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text.trim()) }, enabled = problem == null) { Text("Створити") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
