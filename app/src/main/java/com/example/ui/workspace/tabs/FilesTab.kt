package com.example.ui.workspace.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProjectFile

enum class FileViewMode {
    CODE,
    DIFF,
    EDIT
}

@Composable
fun FilesTab(
    files: List<ProjectFile>,
    onSaveFileEdit: (ProjectFile, String) -> Unit,
    onCreateFile: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteFile: (ProjectFile) -> Unit = {}
) {
    var selectedFile by remember(files) { mutableStateOf(files.firstOrNull()) }
    var viewMode by remember { mutableStateOf(FileViewMode.CODE) }
    var editedContent by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // When file selection changes, synchronize editor
    val activeFile = selectedFile ?: files.firstOrNull()

    if (files.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No project files in workspace.\nExecute an autonomous build plan or create a new file manually.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showNewFileDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create New File")
                }
            }
        }

        if (showNewFileDialog) {
            NewFileDialog(
                onDismiss = { showNewFileDialog = false },
                onCreate = { path, content, lang ->
                    onCreateFile(path, content, lang)
                    showNewFileDialog = false
                }
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar: New File & File Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // New File Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A))
                    .clickable { showNewFileDialog = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("create_new_file_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New File",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Horizontal File Tabs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                files.forEach { file ->
                    val isSelected = file.id == activeFile?.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                            .clickable {
                                selectedFile = file
                                editedContent = file.content
                                viewMode = FileViewMode.CODE
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("file_tab_${file.path.replace('/', '_')}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = file.path.substringAfterLast("/"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }

        // Active File Viewer / Editor / Diff
        activeFile?.let { currentFile ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header with file path, mode selector, and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentFile.path,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        )
                        Text(
                            text = "${currentFile.sizeBytes} bytes • version ${currentFile.version} • ${currentFile.language.uppercase()}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        )
                    }

                    // View Mode Switcher
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FileModeButton(
                            title = "Code",
                            isSelected = viewMode == FileViewMode.CODE,
                            onClick = { viewMode = FileViewMode.CODE }
                        )
                        FileModeButton(
                            title = "Diff",
                            isSelected = viewMode == FileViewMode.DIFF,
                            onClick = {
                                if (editedContent.isBlank()) editedContent = currentFile.content
                                viewMode = FileViewMode.DIFF
                            }
                        )
                        FileModeButton(
                            title = "Edit",
                            isSelected = viewMode == FileViewMode.EDIT,
                            onClick = {
                                editedContent = currentFile.content
                                viewMode = FileViewMode.EDIT
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (viewMode == FileViewMode.EDIT) {
                        Button(
                            onClick = {
                                onSaveFileEdit(currentFile, editedContent)
                                viewMode = FileViewMode.CODE
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp)
                        }
                    } else {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete File",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Content Panel based on Mode
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    when (viewMode) {
                        FileViewMode.CODE -> {
                            val lines = currentFile.content.lines()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                items(lines.withIndex().toList()) { (idx, line) ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "${idx + 1}".padStart(3, ' '),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF475569),
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        FileViewMode.DIFF -> {
                            // Line by line diff comparison
                            val originalLines = currentFile.content.lines()
                            val workingLines = if (editedContent.isNotBlank() && editedContent != currentFile.content) {
                                editedContent.lines()
                            } else {
                                originalLines
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "Diff Viewer • Comparing Workspace Version ${currentFile.version} (${currentFile.path})",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }

                                items(workingLines.withIndex().toList()) { (idx, line) ->
                                    val isOriginal = idx < originalLines.size && originalLines[idx] == line
                                    val prefix = if (isOriginal) "  " else "+ "
                                    val textColor = if (isOriginal) Color(0xFFE2E8F0) else Color(0xFF4ADE80)
                                    val bgColor = if (isOriginal) Color.Transparent else Color(0x1F22C55E)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(bgColor)
                                            .padding(vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}".padStart(3, ' '),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF475569),
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Text(
                                            text = prefix + line,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = textColor,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        FileViewMode.EDIT -> {
                            OutlinedTextField(
                                value = editedContent,
                                onValueChange = { editedContent = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A),
                                    focusedBorderColor = Color(0xFF0284C7),
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { path, content, lang ->
                onCreateFile(path, content, lang)
                showNewFileDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && activeFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete File", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${activeFile.path}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(activeFile)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun FileModeButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFF94A3B8)
            )
        )
    }
}

@Composable
private fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var filePath by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("kotlin") }
    var initialContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Project File", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    label = { Text("File Path (e.g., src/main/App.kt)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Language (kotlin, ts, swift, dart, py)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = initialContent,
                    onValueChange = { initialContent = it },
                    label = { Text("Initial Content (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (filePath.isNotBlank()) {
                        onCreate(filePath.trim(), initialContent, language.trim().lowercase())
                    }
                },
                enabled = filePath.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}
