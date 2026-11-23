package com.anatoliy.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.FileCategory
import com.anatoliy.filemanager.FileItem
import com.anatoliy.filemanager.StorageRoot
import com.anatoliy.filemanager.formatDate
import com.anatoliy.filemanager.formatSize
import java.io.File

enum class ViewMode { LIST, GRID }

class ScrollMemory {
    private val positions = HashMap<String, Pair<Int, Int>>()
    fun get(key: String): Pair<Int, Int> = positions[key] ?: (0 to 0)
    fun put(key: String, index: Int, offset: Int) {
        positions[key] = index to offset
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onCompress: () -> Unit,
    onProperties: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Скасувати вибір")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = "Вибрати всі")
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Копіювати")
            }
            IconButton(onClick = onMove) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Перемістити")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Видалити")
            }
            Box {
                IconButton(onClick = { more = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ще")
                }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    if (count == 1) {
                        DropdownMenuItem(
                            text = { Text("Перейменувати") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { more = false; onRename() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Поділитися") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { more = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Стиснути в ZIP") },
                        leadingIcon = { Icon(Icons.Filled.FolderZip, contentDescription = null) },
                        onClick = { more = false; onCompress() },
                    )
                    DropdownMenuItem(
                        text = { Text("Властивості") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = { more = false; onProperties() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

@Composable
internal fun Breadcrumbs(dir: File, root: StorageRoot?, onNavigate: (File) -> Unit) {
    val crumbs = remember(dir, root) {
        val list = mutableListOf<Pair<String, File>>()
        if (root == null) {
            list += dir.path to dir
        } else {
            list += root.label to root.dir
            var acc = root.dir
            dir.relativeToOrNull(root.dir)?.path?.split(File.separator)?.filter { it.isNotEmpty() }?.forEach {
                acc = File(acc, it)
                list += it to acc
            }
        }
        list
    }
    val state = rememberLazyListState()
    LaunchedEffect(crumbs) { state.scrollToItem(crumbs.lastIndex) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(crumbs.size) { i ->
            val (label, file) = crumbs[i]
            val last = i == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(onClick = { if (!last) onNavigate(file) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(
                        label,
                        maxLines = 1,
                        fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                        color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FileListRow(
    item: FileItem,
    selecting: Boolean,
    selected: Boolean,
    onTap: (FileItem) -> Unit,
    onLongPress: (FileItem) -> Unit,
    showPath: Boolean = false,
    actions: (FileItem) -> List<MenuAction> = { emptyList() },
) {
    val context = LocalContext.current
    val subtitle = if (showPath) {
        val where = item.file.parentFile?.path.orEmpty()
        if (item.isDirectory) where else "$where · ${formatSize(context, item.size)}"
    } else if (item.isDirectory) {
        formatDate(item.lastModified)
    } else {
        "${formatSize(context, item.size)} · ${formatDate(item.lastModified)}"
    }
    FileRow(
        item = item,
        subtitle = subtitle,
        onClick = { onTap(item) },
        onLongClick = { onLongPress(item) },
        selected = selected,
        trailing = if (selecting) {
            { Checkbox(checked = selected, onCheckedChange = { onLongPress(item) }) }
        } else {
            { FileActionsMenu(item.file, actions(item)) }
        },
    )
}

@Composable
internal fun MediaGrid(
    folders: List<FileItem>,
    files: List<FileItem>,
    state: LazyGridState,
    selecting: Boolean,
    isSelected: (FileItem) -> Boolean,
    onTap: (FileItem) -> Unit,
    onLongPress: (FileItem) -> Unit,
    showPath: Boolean,
    actions: (FileItem) -> List<MenuAction>,
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(folders, key = { it.file.absolutePath }, span = { GridItemSpan(maxLineSpan) }) { item ->
            Column {
                FileListRow(item, selecting, isSelected(item), onTap, onLongPress, showPath, actions)
                HorizontalDivider()
            }
        }
        gridItems(files, key = { it.file.absolutePath }) { item ->
            MediaCell(item, selecting, isSelected(item), onClick = { onTap(item) }, onLongClick = { onLongPress(item) })
        }
    }
}

@Composable
internal fun FileGrid(
    items: List<FileItem>,
    state: LazyGridState,
    selecting: Boolean,
    isSelected: (FileItem) -> Boolean,
    onTap: (FileItem) -> Unit,
    onLongPress: (FileItem) -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 88.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(items, key = { it.file.absolutePath }) { item ->
            FileGridCell(item, selecting, isSelected(item), onClick = { onTap(item) }, onLongClick = { onLongPress(item) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCell(
    item: FileItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (item.isDirectory) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            } else {
                FileThumbnail(item, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), iconSize = 40.dp)
            }
            if (item.category == FileCategory.VIDEO && !item.isDirectory) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Відео",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(3.dp),
                )
            }
            if (selecting) SelectionCheck(selected, Modifier.align(Alignment.TopEnd))
        }
        Text(
            item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp).heightIn(min = 32.dp),
        )
    }
}

@Composable
internal fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
        contentDescription = if (selected) "Вибрано" else null,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        modifier = modifier
            .padding(4.dp)
            .size(24.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Black.copy(alpha = 0.25f),
                CircleShape,
            ),
    )
}

@Composable
internal fun EmptyState(text: String) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 120.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
