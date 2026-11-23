package com.anatoliy.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.FileCategory
import com.anatoliy.filemanager.FileItem
import com.anatoliy.filemanager.openWithExternalApp
import com.anatoliy.filemanager.shareFiles
import java.io.File

@Suppress("DEPRECATION")
fun iconFor(item: FileItem): ImageVector = when {
    item.isDirectory -> Icons.Filled.Folder
    item.isPdf -> Icons.Filled.PictureAsPdf
    item.isImage -> Icons.Filled.Image
    item.category == FileCategory.VIDEO -> Icons.Filled.VideoFile
    item.category == FileCategory.AUDIO -> Icons.Filled.AudioFile
    item.category == FileCategory.FB2 -> Icons.Filled.MenuBook
    item.category == FileCategory.ARCHIVES -> Icons.Filled.FolderZip
    item.category == FileCategory.APK -> Icons.Filled.Android
    else -> Icons.Filled.Description
}

data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else ListItemDefaults.colors(),
        leadingContent = {
            if (hasPreview(item)) {
                FileThumbnail(
                    item,
                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    iconSize = 24.dp,
                )
            } else {
                Icon(
                    iconFor(item),
                    contentDescription = null,
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing,
    )
}

@Composable
fun FileActionsMenu(file: File, extra: List<MenuAction> = emptyList()) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Дії")
        }
        FileActionsDropdown(file, expanded, onDismiss = { expanded = false }, extra = extra)
    }
}

@Composable
fun FileActionsDropdown(
    file: File,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extra: List<MenuAction> = emptyList(),
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (file.isFile) {
            DropdownMenuItem(
                text = { Text("Відкрити в іншому додатку") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                onClick = {
                    onDismiss()
                    openWithExternalApp(context, file)
                },
            )
            DropdownMenuItem(
                text = { Text("Поділитися") },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    onDismiss()
                    shareFiles(context, listOf(file))
                },
            )
        }
        extra.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, contentDescription = null) },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}
