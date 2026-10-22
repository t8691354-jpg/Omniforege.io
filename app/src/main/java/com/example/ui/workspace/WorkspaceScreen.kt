package com.example.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.workspace.tabs.ApiKeysTab
import com.example.ui.workspace.tabs.BuildTab
import com.example.ui.workspace.tabs.ChatTab
import com.example.ui.workspace.tabs.CheckpointsTab
import com.example.ui.workspace.tabs.FilesTab
import com.example.ui.workspace.tabs.OverviewTab
import com.example.ui.workspace.tabs.PreviewTab
import com.example.ui.workspace.tabs.SecretsTab
import com.example.ui.workspace.tabs.TasksTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onBackToProjects: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val files by viewModel.files.collectAsState()
    val checkpoints by viewModel.checkpoints.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val connections by viewModel.connections.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val buildLogs by viewModel.buildLogs.collectAsState()
    val lastBuildResult by viewModel.lastBuildResult.collectAsState()
    val showFailoverDialog by viewModel.showFailoverDialog.collectAsState()
    val userGeminiApiKey by viewModel.userGeminiApiKey.collectAsState()
    val projectSecrets by viewModel.projectSecrets.collectAsState()
    val failoverError by viewModel.failoverQuotaError.collectAsState()

    if (project == null) {
        onBackToProjects()
        return
    }

    val tabs = listOf(
        WorkspaceTabItemData(WorkspaceTab.OVERVIEW, "Overview", Icons.Default.Dashboard, "nav_tab_overview"),
        WorkspaceTabItemData(WorkspaceTab.CHAT, "AI Studio", Icons.AutoMirrored.Filled.Chat, "nav_tab_studio"),
        WorkspaceTabItemData(WorkspaceTab.PREVIEW, "Preview", Icons.Default.Devices, "nav_tab_preview"),
        WorkspaceTabItemData(WorkspaceTab.TASKS, "Tasks", Icons.Default.FormatListNumbered, "nav_tab_tasks"),
        WorkspaceTabItemData(WorkspaceTab.FILES, "Files", Icons.Default.Description, "nav_tab_files"),
        WorkspaceTabItemData(WorkspaceTab.CHECKPOINTS, "Snapshots", Icons.Default.Bookmark, "nav_tab_checkpoints"),
        WorkspaceTabItemData(WorkspaceTab.BUILD, "Build & APK", Icons.Default.Build, "nav_tab_build"),
        WorkspaceTabItemData(WorkspaceTab.SECRETS, "Secrets", Icons.Default.Security, "nav_tab_secrets"),
        WorkspaceTabItemData(WorkspaceTab.CONNECTIONS, "API Keys", Icons.Default.Key, "nav_tab_keys")
    )

    val currentTabIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = project!!.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9)
                                )
                            )
                            Text(
                                text = "${project!!.targetPlatform} • ${project!!.activeProvider} (${project!!.activeModel})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackToProjects,
                            modifier = Modifier.testTag("workspace_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Projects",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )

                // Scrollable Tabs Strip
                ScrollableTabRow(
                    selectedTabIndex = currentTabIndex,
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color(0xFF38BDF8),
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[currentTabIndex]),
                            color = Color(0xFF38BDF8)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, item ->
                        val isSelected = index == currentTabIndex
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(item.tab) },
                            text = {
                                Text(
                                    text = item.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF0A0E17)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Status Banner (if active)
            statusBanner?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.contains("Quota") || msg.contains("failed")) Color(0xFF7F1D1D) else Color(0xFF0369A1)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Tab Views
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    WorkspaceTab.OVERVIEW -> OverviewTab(
                        project = project!!,
                        tasks = tasks,
                        files = files,
                        checkpoints = checkpoints,
                        isGenerating = isGenerating,
                        onRunPlan = { viewModel.startAutonomousExecution() },
                        onManualCheckpoint = { viewModel.createManualCheckpoint("Manual User Snapshot") },
                        onNavigateToBuild = { viewModel.selectTab(WorkspaceTab.BUILD) },
                        onNavigateToKeys = { viewModel.selectTab(WorkspaceTab.CONNECTIONS) }
                    )
                    WorkspaceTab.CHAT -> ChatTab(
                        project = project!!,
                        messages = messages,
                        onSendMessage = { prompt, search -> viewModel.sendUserChatMessage(prompt, search) }
                    )
                    WorkspaceTab.PREVIEW -> PreviewTab(
                        project = project!!,
                        files = files
                    )
                    WorkspaceTab.TASKS -> TasksTab(
                        tasks = tasks,
                        isGenerating = isGenerating,
                        onRunAutonomousPlan = { viewModel.startAutonomousExecution() },
                        onPauseExecution = { viewModel.pauseAutonomousExecution() },
                        onCancelExecution = { viewModel.cancelAutonomousExecution() }
                    )
                    WorkspaceTab.FILES -> FilesTab(
                        files = files,
                        onSaveFileEdit = { file, newContent -> viewModel.saveFileEdit(file, newContent) },
                        onCreateFile = { path, content, lang -> viewModel.createNewProjectFile(path, content, lang) },
                        onDeleteFile = { file -> viewModel.deleteProjectFile(file) }
                    )
                    WorkspaceTab.CHECKPOINTS -> CheckpointsTab(
                        checkpoints = checkpoints,
                        onRestoreCheckpoint = { cp -> viewModel.restoreToCheckpoint(cp) }
                    )
                    WorkspaceTab.BUILD -> BuildTab(
                        project = project!!,
                        files = files,
                        isGenerating = isGenerating,
                        buildLogs = buildLogs,
                        lastBuildResult = lastBuildResult,
                        onRunBuild = { viewModel.runProjectBuild(context) },
                        onAttachWorkflow = { viewModel.attachCloudBuildWorkflow() }
                    )
                    WorkspaceTab.SECRETS -> SecretsTab(
                        project = project!!,
                        files = files,
                        currentGeminiApiKey = userGeminiApiKey,
                        projectSecrets = projectSecrets,
                        onSaveGeminiApiKey = { key -> viewModel.saveUserGeminiApiKey(key) },
                        onTestGeminiApiKey = { key, callback -> viewModel.testGeminiApiKey(key, callback) },
                        onAddOrUpdateSecret = { secret -> viewModel.addOrUpdateProjectSecret(secret) },
                        onDeleteSecret = { key -> viewModel.deleteProjectSecret(key) },
                        onSyncSecretsToFile = { secrets -> viewModel.syncSecretsToFile(secrets) }
                    )
                    WorkspaceTab.CONNECTIONS -> ApiKeysTab(
                        connections = connections,
                        onAddConnection = { name, prov, key, url, model ->
                            viewModel.saveApiConnection(name, prov, key, url, model)
                        },
                        onDeleteConnection = { connId -> viewModel.deleteApiConnection(connId) },
                        onSimulateQuotaExceeded = {
                            viewModel.simulateQuotaExceeded()
                        }
                    )
                }
            }
        }
    }

    // Failover Dialog
    if (showFailoverDialog) {
        FailoverDialog(
            project = project!!,
            latestCheckpoint = checkpoints.firstOrNull(),
            availableConnections = connections.filter { it.provider != project!!.activeProvider || it.selectedModel != project!!.activeModel },
            errorMessage = failoverError,
            onDismiss = { viewModel.dismissFailoverDialog() },
            onResumeWithConnection = { conn -> viewModel.resumeWithNewConnection(conn) },
            onNavigateToKeys = {
                viewModel.dismissFailoverDialog()
                viewModel.selectTab(WorkspaceTab.CONNECTIONS)
            }
        )
    }
}

private data class WorkspaceTabItemData(
    val tab: WorkspaceTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)
