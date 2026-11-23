package com.anatoliy.filemanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.StorageRoot
import com.anatoliy.filemanager.Storages
import com.anatoliy.filemanager.formatSize
import java.io.File

enum class Tool { ANALYSIS, DUPLICATES, UNUSED }

@Composable
fun AppDrawerContent(
    roots: List<StorageRoot>,
    currentRoot: StorageRoot?,
    currentDir: File,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    onOpenDir: (File) -> Unit,
    onOpenTool: (Tool) -> Unit,
) {
    val context = LocalContext.current
    val quick = remember { Storages.quickFolders() }
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text(
                "File Manager",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp),
            )

            SectionTitle("Сховища")
            roots.forEach { root ->
                val total = root.totalBytes
                val free = root.freeBytes
                NavigationDrawerItem(
                    selected = root == currentRoot,
                    onClick = { onOpenDir(root.dir) },
                    icon = {
                        Icon(
                            when {
                                !root.removable -> Icons.Filled.PhoneAndroid
                                root.label.contains("USB", ignoreCase = true) -> Icons.Filled.Usb
                                else -> Icons.Filled.SdCard
                            },
                            contentDescription = null,
                        )
                    },
                    label = {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(root.label)
                            if (total > 0) {
                                LinearProgressIndicator(
                                    progress = { (total - free).toFloat() / total },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                )
                                Text(
                                    "Вільно ${formatSize(context, free)} з ${formatSize(context, total)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                )
            }

            if (quick.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Швидкий доступ")
                quick.forEach { (label, dir) ->
                    NavigationDrawerItem(
                        selected = dir.path == currentDir.path,
                        onClick = { onOpenDir(dir) },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Інструменти")
            NavigationDrawerItem(
                selected = false,
                onClick = { onOpenTool(Tool.ANALYSIS) },
                icon = { Icon(Icons.Filled.DataUsage, contentDescription = null) },
                label = { Text("Аналіз пам'яті") },
            )
            NavigationDrawerItem(
                selected = false,
                onClick = { onOpenTool(Tool.DUPLICATES) },
                icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                label = { Text("Пошук дублікатів") },
            )
            NavigationDrawerItem(
                selected = false,
                onClick = { onOpenTool(Tool.UNUSED) },
                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                label = { Text("Давно не використовувані") },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                selected = false,
                onClick = { onShowHiddenChange(!showHidden) },
                icon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                label = { Text("Приховані файли") },
                badge = { Switch(checked = showHidden, onCheckedChange = null) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
    )
}
