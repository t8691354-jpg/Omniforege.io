package com.example.ui.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiController
import com.example.data.ai.MultiProviderEngine
import com.example.data.ai.ProviderException
import com.example.data.engine.BuildEngine
import com.example.data.engine.BuildResult
import com.example.data.engine.ContinuityEngine
import com.example.data.model.ApiConnection
import com.example.data.model.ChatMessage
import com.example.data.model.Checkpoint
import com.example.data.model.ConnectionStatus
import com.example.data.model.DevelopmentTask
import com.example.data.model.Project
import com.example.data.model.ProjectFile
import com.example.data.model.ProjectStatus
import com.example.data.model.ProjectSecret
import com.example.data.model.TaskStatus
import com.example.data.secrets.UserSecretsManager
import com.example.data.repository.FirestoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WorkspaceTab(val title: String) {
    OVERVIEW("Overview"),
    CHAT("AI Studio"),
    PREVIEW("Preview"),
    TASKS("Tasks"),
    FILES("Files"),
    CHECKPOINTS("Snapshots"),
    BUILD("Build & APK"),
    SECRETS("Secrets"),
    CONNECTIONS("API Keys")
}

class WorkspaceViewModel(
    val repository: FirestoreRepository,
    private val continuityEngine: ContinuityEngine = ContinuityEngine(repository)
) : ViewModel() {

    val currentUserId = repository.requireUserId()

    val projects: StateFlow<List<Project>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connections: StateFlow<List<ApiConnection>> = repository.observeApiConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProject = MutableStateFlow<Project?>(null)
    val activeProject: StateFlow<Project?> = _activeProject.asStateFlow()

    private val _selectedTab = MutableStateFlow(WorkspaceTab.OVERVIEW)
    val selectedTab: StateFlow<WorkspaceTab> = _selectedTab.asStateFlow()

    private val _tasks = MutableStateFlow<List<DevelopmentTask>>(emptyList())
    val tasks: StateFlow<List<DevelopmentTask>> = _tasks.asStateFlow()

    private val _files = MutableStateFlow<List<ProjectFile>>(emptyList())
    val files: StateFlow<List<ProjectFile>> = _files.asStateFlow()

    private val _checkpoints = MutableStateFlow<List<Checkpoint>>(emptyList())
    val checkpoints: StateFlow<List<Checkpoint>> = _checkpoints.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _buildLogs = MutableStateFlow<List<String>>(emptyList())
    val buildLogs: StateFlow<List<String>> = _buildLogs.asStateFlow()

    private val _lastBuildResult = MutableStateFlow<BuildResult?>(null)
    val lastBuildResult: StateFlow<BuildResult?> = _lastBuildResult.asStateFlow()

    // User API Keys & Secrets Integration
    val userGeminiApiKey: StateFlow<String> = UserSecretsManager.geminiApiKeyFlow

    private val _projectSecrets = MutableStateFlow<List<ProjectSecret>>(emptyList())
    val projectSecrets: StateFlow<List<ProjectSecret>> = _projectSecrets.asStateFlow()

    // Failover State Modal
    private val _showFailoverDialog = MutableStateFlow(false)
    val showFailoverDialog: StateFlow<Boolean> = _showFailoverDialog.asStateFlow()

    private val _failoverQuotaError = MutableStateFlow<String?>(null)
    val failoverQuotaError: StateFlow<String?> = _failoverQuotaError.asStateFlow()

    private var activeProjectListenersJob: Job? = null
    private var autonomousExecutionJob: Job? = null

    fun selectTab(tab: WorkspaceTab) {
        _selectedTab.value = tab
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun dismissFailoverDialog() {
        _showFailoverDialog.value = false
    }

    fun selectProject(project: Project?) {
        _activeProject.value = project
        if (project != null) {
            loadSecretsForActiveProject(project.id)
            attachProjectSubcollectionListeners(project.id)
        } else {
            activeProjectListenersJob?.cancel()
        }
    }

    fun loadSecretsForActiveProject(projectId: String) {
        _projectSecrets.value = UserSecretsManager.getProjectSecrets(projectId)
    }

    fun saveUserGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        UserSecretsManager.saveGeminiApiKey(trimmed)
        val proj = _activeProject.value
        if (proj != null) {
            val current = _projectSecrets.value.toMutableList()
            val idx = current.indexOfFirst { it.key == "GEMINI_API_KEY" }
            if (idx >= 0) {
                current[idx] = current[idx].copy(value = trimmed)
            } else {
                current.add(
                    0,
                    ProjectSecret(
                        id = "${proj.id}_gemini",
                        projectId = proj.id,
                        key = "GEMINI_API_KEY",
                        value = trimmed,
                        description = "Google Gemini / AI Studio Personal Key"
                    )
                )
            }
            _projectSecrets.value = current
            UserSecretsManager.saveProjectSecrets(proj.id, current)
            syncSecretsToFile(current)
        }
        _statusBanner.value = "Personal Gemini API Key activated! Ready for AI operations."
    }

    fun testGeminiApiKey(apiKey: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = GeminiController.testApiKey(apiKey)
            result.onSuccess { msg ->
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to validate key")
            }
        }
    }

    fun addOrUpdateProjectSecret(secret: ProjectSecret) {
        val proj = _activeProject.value ?: return
        val current = _projectSecrets.value.toMutableList()
        val idx = current.indexOfFirst { it.key.equals(secret.key, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = secret
        } else {
            current.add(secret)
        }
        _projectSecrets.value = current
        UserSecretsManager.saveProjectSecrets(proj.id, current)
        syncSecretsToFile(current)
        _statusBanner.value = "Secret '${secret.key}' saved successfully."
    }

    fun deleteProjectSecret(secretKey: String) {
        val proj = _activeProject.value ?: return
        val current = _projectSecrets.value.filterNot { it.key.equals(secretKey, ignoreCase = true) }
        _projectSecrets.value = current
        UserSecretsManager.saveProjectSecrets(proj.id, current)
        syncSecretsToFile(current)
        _statusBanner.value = "Secret '$secretKey' deleted."
    }

    private fun attachProjectSubcollectionListeners(projectId: String) {
        activeProjectListenersJob?.cancel()
        activeProjectListenersJob = viewModelScope.launch {
            launch {
                repository.observeTasks(projectId).catch { emit(emptyList()) }.collect {
                    _tasks.value = it
                }
            }
            launch {
                repository.observeFiles(projectId).catch { emit(emptyList()) }.collect {
                    _files.value = it
                }
            }
            launch {
                repository.observeCheckpoints(projectId).catch { emit(emptyList()) }.collect {
                    _checkpoints.value = it
                }
            }
            launch {
                repository.observeMessages(projectId).catch { emit(emptyList()) }.collect {
                    _messages.value = it
                }
            }
            launch {
                repository.observeProject(projectId).catch { emit(null) }.collect { updated ->
                    if (updated != null) _activeProject.value = updated
                }
            }
        }
    }

    fun createProject(
        name: String,
        description: String,
        platform: String,
        provider: String,
        model: String,
        useSearchGrounding: Boolean = true
    ) {
        viewModelScope.launch {
            _isGenerating.value = true
            _statusBanner.value = "Creating project and architecting execution plan..."
            try {
                val proj = Project(
                    userId = currentUserId,
                    name = name,
                    description = description,
                    targetPlatform = platform,
                    activeProvider = provider,
                    activeModel = model,
                    status = ProjectStatus.PLANNING.name
                )
                val newProjectId = repository.createProject(proj)
                val createdProj = proj.copy(id = newProjectId)
                selectProject(createdProj)

                // Central AI Controller generates implementation plan
                val plan = GeminiController.generateProjectPlan(
                    projectName = name,
                    userBrief = description,
                    platform = platform,
                    useSearchGrounding = useSearchGrounding
                )

                // Save tasks to Firestore
                for ((index, t) in plan.plannedTasks.withIndex()) {
                    val task = DevelopmentTask(
                        userId = currentUserId,
                        projectId = newProjectId,
                        title = t.title,
                        description = t.description,
                        orderIndex = index,
                        status = TaskStatus.PENDING.name,
                        dependencies = t.dependencies,
                        targetFiles = t.targetFiles
                    )
                    repository.saveTask(task)
                }

                // Initial baseline checkpoint
                continuityEngine.createCheckpoint(
                    project = createdProj.copy(totalTasks = plan.plannedTasks.size),
                    tasks = emptyList(),
                    files = emptyList(),
                    checkpointTitle = "Initial Scaffold: ${plan.plannedTasks.size} Tasks Architected"
                )

                repository.updateProject(
                    createdProj.copy(
                        status = ProjectStatus.READY.name,
                        totalTasks = plan.plannedTasks.size
                    )
                )

                // Save welcome message
                repository.saveMessage(
                    ChatMessage(
                        userId = currentUserId,
                        projectId = newProjectId,
                        role = "assistant",
                        content = "OmniForge AI has architected '${name}'. Plan contains ${plan.plannedTasks.size} tasks.\nArchitecture: ${plan.architectureSummary}\nTarget files: ${plan.targetFileManifest.joinToString(", ")}.\nReady to build autonomously.",
                        provider = provider,
                        model = model,
                        isCheckpointEvent = true
                    )
                )

                _statusBanner.value = "Project '${name}' ready. Tap 'Run Plan' to start building."
            } catch (e: Exception) {
                _statusBanner.value = "Project creation failed: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun startAutonomousExecution() {
        val proj = _activeProject.value ?: return
        autonomousExecutionJob?.cancel()
        autonomousExecutionJob = viewModelScope.launch {
            _isGenerating.value = true
            repository.updateProject(proj.copy(status = ProjectStatus.RUNNING.name))

            val currentTaskList = _tasks.value.toMutableList()
            var currentFilesList = _files.value.toMutableList()

            // Find incomplete tasks
            val pendingTasks = currentTaskList.filter { it.status != TaskStatus.COMPLETED.name }

            for (task in pendingTasks) {
                _statusBanner.value = "Executing Task #${task.orderIndex + 1}: ${task.title} using ${proj.activeProvider}..."
                repository.saveTask(task.copy(status = TaskStatus.RUNNING.name))

                var repairAttempt = 0
                val maxRepairs = 3
                var taskSucceeded = false
                var lastErrorMessage: String? = null

                while (repairAttempt <= maxRepairs && !taskSucceeded) {
                    try {
                        if (repairAttempt > 0) {
                            _statusBanner.value = "Auto-Recovering Task #${task.orderIndex + 1} (Attempt $repairAttempt of $maxRepairs)..."
                            repository.saveTask(task.copy(status = TaskStatus.RECOVERING.name, retryCount = repairAttempt))
                            kotlinx.coroutines.delay(800)
                        }

                        // Generate code
                        val generatedCodeMap = GeminiController.generateCodeForTask(
                            project = proj,
                            task = task,
                            existingFiles = currentFilesList
                        )

                        // Transition to TESTING state for verification
                        repository.saveTask(task.copy(status = TaskStatus.TESTING.name))
                        _statusBanner.value = "Verifying & Testing code output for '${task.title}'..."
                        kotlinx.coroutines.delay(400)

                        // Validate syntax hygiene (check for empty content or fatal truncation)
                        val invalidEntries = generatedCodeMap.filter { it.value.isBlank() }
                        if (invalidEntries.isNotEmpty()) {
                            throw IllegalStateException("Generated file ${invalidEntries.keys.first()} was empty.")
                        }

                        // Save generated files
                        for ((path, content) in generatedCodeMap) {
                            val fileId = "${proj.id}_${path.replace("[^a-zA-Z0-9]".toRegex(), "_")}"
                            val pFile = ProjectFile(
                                id = fileId,
                                userId = currentUserId,
                                projectId = proj.id,
                                path = path,
                                content = content,
                                language = proj.language.lowercase(),
                                sizeBytes = content.toByteArray().size,
                                version = 1
                            )
                            repository.saveFile(pFile)
                            currentFilesList.removeAll { it.path == path }
                            currentFilesList.add(pFile)
                        }

                        // Mark task completed
                        val completedTask = task.copy(
                            status = TaskStatus.COMPLETED.name,
                            outputSummary = "Generated ${generatedCodeMap.size} files: ${generatedCodeMap.keys.joinToString(", ")}.",
                            retryCount = repairAttempt,
                            errorMessage = null
                        )
                        repository.saveTask(completedTask)

                        val completedCount = currentTaskList.count { it.status == TaskStatus.COMPLETED.name } + 1
                        repository.updateProject(proj.copy(completedTasks = completedCount))

                        // Milestone Checkpoint
                        continuityEngine.createCheckpoint(
                            project = proj.copy(completedTasks = completedCount),
                            tasks = currentTaskList,
                            files = currentFilesList,
                            checkpointTitle = "Milestone Checkpoint: Completed '${task.title}'"
                        )

                        taskSucceeded = true

                    } catch (e: Exception) {
                        lastErrorMessage = e.message
                        val isQuota = e is ProviderException.QuotaExceededException || e.message?.contains("429") == true || e.message?.contains("quota") == true

                        if (isQuota) {
                            // Atomic failover handling
                            repository.saveTask(
                                task.copy(
                                    status = TaskStatus.INTERRUPTED.name,
                                    errorMessage = e.message
                                )
                            )
                            repository.updateProject(
                                proj.copy(
                                    status = ProjectStatus.PAUSED_QUOTA.name,
                                    currentTaskId = task.id
                                )
                            )
                            continuityEngine.createCheckpoint(
                                project = proj,
                                tasks = currentTaskList,
                                files = currentFilesList,
                                interruptedTaskId = task.id,
                                checkpointTitle = "Failover Checkpoint: Task #${task.orderIndex + 1} (Quota Limit)",
                                statusLabel = "failover_saved"
                            )
                            _failoverQuotaError.value = e.message ?: "Quota Exceeded on ${proj.activeProvider}. All progress saved."
                            _showFailoverDialog.value = true
                            _statusBanner.value = "Quota limit reached! Project checkpoint saved safely. Switch API key to resume."
                            _isGenerating.value = false
                            return@launch
                        } else {
                            repairAttempt++
                            if (repairAttempt > maxRepairs) {
                                repository.saveTask(
                                    task.copy(
                                        status = TaskStatus.FAILED.name,
                                        errorMessage = "Failed after $maxRepairs repair attempts: ${e.message}",
                                        retryCount = repairAttempt
                                    )
                                )
                                repository.updateProject(proj.copy(status = ProjectStatus.FAILED.name))
                                _statusBanner.value = "Task failed after bounded recovery: ${e.message}"
                                _isGenerating.value = false
                                return@launch
                            }
                        }
                    }
                }
            }

            _isGenerating.value = false
            val finalTasks = _tasks.value
            if (finalTasks.isNotEmpty() && finalTasks.all { it.status == TaskStatus.COMPLETED.name }) {
                repository.updateProject(proj.copy(status = ProjectStatus.COMPLETED.name))
                _statusBanner.value = "All tasks completed! Project is ready to build and package."
            }
        }
    }

    fun pauseAutonomousExecution() {
        autonomousExecutionJob?.cancel()
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _isGenerating.value = false
            repository.updateProject(proj.copy(status = ProjectStatus.READY.name))
            val runningTask = _tasks.value.firstOrNull { it.status == TaskStatus.RUNNING.name || it.status == TaskStatus.TESTING.name }
            if (runningTask != null) {
                repository.saveTask(runningTask.copy(status = TaskStatus.QUEUED.name))
            }
            _statusBanner.value = "Execution paused. Tap 'Resume' anytime to continue."
        }
    }

    fun cancelAutonomousExecution() {
        autonomousExecutionJob?.cancel()
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _isGenerating.value = false
            repository.updateProject(proj.copy(status = ProjectStatus.CANCELLED.name))
            val runningTask = _tasks.value.firstOrNull { it.status == TaskStatus.RUNNING.name || it.status == TaskStatus.TESTING.name }
            if (runningTask != null) {
                repository.saveTask(runningTask.copy(status = TaskStatus.CANCELLED.name))
            }
            _statusBanner.value = "Autonomous execution cancelled."
        }
    }

    fun createNewProjectFile(path: String, content: String, language: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val sanitizedPath = if (path.startsWith("/")) path else "/$path"
            val fileId = "${proj.id}_${sanitizedPath.replace("[^a-zA-Z0-9]".toRegex(), "_")}"
            val pFile = ProjectFile(
                id = fileId,
                userId = currentUserId,
                projectId = proj.id,
                path = sanitizedPath,
                content = content,
                language = language,
                sizeBytes = content.toByteArray().size,
                version = 1
            )
            repository.saveFile(pFile)
            _statusBanner.value = "Created file '$sanitizedPath'."
        }
    }

    fun deleteProjectFile(file: ProjectFile) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            repository.deleteFile(proj.id, file.id)
            _statusBanner.value = "Deleted '${file.path}'."
        }
    }

    fun resumeWithNewConnection(connection: ApiConnection) {
        val proj = _activeProject.value ?: return
        val latestCheckpoint = _checkpoints.value.firstOrNull() ?: return
        viewModelScope.launch {
            _isGenerating.value = true
            _showFailoverDialog.value = false
            _statusBanner.value = "Resuming project '${proj.name}' using ${connection.name} (${connection.selectedModel})..."

            val result = continuityEngine.prepareResumeFromCheckpoint(
                project = proj,
                checkpoint = latestCheckpoint,
                tasks = _tasks.value,
                newConnection = connection
            )

            // Log continuity resumption event in messages
            repository.saveMessage(
                ChatMessage(
                    userId = currentUserId,
                    projectId = proj.id,
                    role = "assistant",
                    content = "🔄 Multi-API Failover Successful: ${result.message}",
                    provider = connection.provider,
                    model = connection.selectedModel,
                    isCheckpointEvent = true
                )
            )

            _statusBanner.value = result.message
            _isGenerating.value = false

            if (result.isReadyToRun) {
                startAutonomousExecution()
            }
        }
    }

    fun restoreToCheckpoint(checkpoint: Checkpoint) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _statusBanner.value = "Restoring workspace to Checkpoint #${checkpoint.version}..."
            repository.updateProject(
                proj.copy(
                    lastCheckpointId = checkpoint.id,
                    completedTasks = checkpoint.completedTaskCount
                )
            )
            _statusBanner.value = "Restored to Checkpoint #${checkpoint.version}: '${checkpoint.title}'"
        }
    }

    fun runProjectBuild(context: Context) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _isGenerating.value = true
            val logs = mutableListOf<String>()
            _buildLogs.value = logs
            _statusBanner.value = "Compiling and packaging project..."

            val result = BuildEngine.runProjectBuild(
                context = context,
                project = proj,
                files = _files.value,
                onProgressLog = { logLine ->
                    logs.add(logLine)
                    _buildLogs.value = logs.toList()
                }
            )

            _lastBuildResult.value = result
            _isGenerating.value = false
            _statusBanner.value = result.artifactSummary
        }
    }

    fun sendUserChatMessage(content: String, useSearchGrounding: Boolean = true) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            // 1. Save user message
            repository.saveMessage(
                ChatMessage(
                    userId = currentUserId,
                    projectId = proj.id,
                    role = "user",
                    content = content
                )
            )

            // 2. Call central orchestrator
            val history = _messages.value.takeLast(6).joinToString("\n") {
                "${it.role.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }}: ${it.content}"
            }
            val reply = GeminiController.chatWithOrchestrator(
                project = proj,
                historyPrompt = history,
                userPrompt = content,
                useSearchGrounding = useSearchGrounding
            )

            // 3. Save assistant reply
            repository.saveMessage(
                ChatMessage(
                    userId = currentUserId,
                    projectId = proj.id,
                    role = "assistant",
                    content = reply,
                    provider = proj.activeProvider,
                    model = proj.activeModel
                )
            )
        }
    }

    fun saveApiConnection(
        name: String,
        provider: String,
        rawKey: String,
        customUrl: String?,
        model: String
    ) {
        viewModelScope.launch {
            val masked = MultiProviderEngine.maskKey(rawKey)
            val conn = ApiConnection(
                userId = currentUserId,
                name = name,
                provider = provider,
                maskedKey = masked,
                customBaseUrl = customUrl,
                selectedModel = model,
                status = ConnectionStatus.ACTIVE.name
            )
            repository.saveApiConnection(conn)
            _statusBanner.value = "API connection '${name}' configured successfully."
        }
    }

    fun deleteApiConnection(connId: String) {
        viewModelScope.launch {
            repository.deleteApiConnection(connId)
            _statusBanner.value = "API connection removed."
        }
    }

    fun saveFileEdit(file: ProjectFile, newContent: String) {
        viewModelScope.launch {
            repository.saveFile(
                file.copy(
                    content = newContent,
                    version = file.version + 1,
                    sizeBytes = newContent.toByteArray().size
                )
            )
            _statusBanner.value = "Saved '${file.path}' (version ${file.version + 1})."
        }
    }

    fun createManualCheckpoint(title: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _statusBanner.value = "Preserving state snapshot..."
            val cp = continuityEngine.createCheckpoint(
                project = proj,
                tasks = _tasks.value,
                files = _files.value,
                checkpointTitle = title
            )
            _statusBanner.value = "Checkpoint #${cp.version} saved (${_files.value.size} files hashed)."
        }
    }

    fun simulateQuotaExceeded() {
        val proj = _activeProject.value ?: return
        val currentTaskList = _tasks.value
        val taskToInterrupt = currentTaskList.firstOrNull { it.status != TaskStatus.COMPLETED.name } ?: currentTaskList.firstOrNull() ?: return
        viewModelScope.launch {
            // 1. Mark task INTERRUPTED with 429
            repository.saveTask(
                taskToInterrupt.copy(
                    status = TaskStatus.INTERRUPTED.name,
                    errorMessage = "Simulated Quota Limit: Rate limit 429 reached on ${proj.activeProvider}."
                )
            )
            // 2. Pause project
            repository.updateProject(
                proj.copy(
                    status = ProjectStatus.PAUSED_QUOTA.name,
                    currentTaskId = taskToInterrupt.id
                )
            )
            // 3. Save atomic failover checkpoint
            continuityEngine.createCheckpoint(
                project = proj,
                tasks = currentTaskList,
                files = _files.value,
                interruptedTaskId = taskToInterrupt.id,
                checkpointTitle = "Failover Checkpoint: Task #${taskToInterrupt.orderIndex + 1} (Quota Limit)",
                statusLabel = "failover_saved"
            )
            // 4. Open Failover modal
            _failoverQuotaError.value = "HTTP 429: API Quota exhausted for ${proj.activeProvider}. Completed progress is preserved."
            _showFailoverDialog.value = true
            _statusBanner.value = "API quota exceeded! Checkpoint safely saved. Select another key to resume."
        }
    }

    fun attachCloudBuildWorkflow() {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val updatedFiles = com.example.data.engine.CloudBuildWorker.attachWorkflowToFiles(proj, _files.value)
            val workflowFile = updatedFiles.find { it.path == ".github/workflows/build-apk.yml" }
            if (workflowFile != null) {
                repository.saveFile(workflowFile)
                _statusBanner.value = "Attached GitHub Actions Cloud APK Builder to project files!"
            }
        }
    }

    fun syncSecretsToFile(secrets: List<ProjectSecret>) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val envContent = secrets.joinToString("\n") { "${it.key}=${it.value}" }
            val envExampleContent = secrets.joinToString("\n") { "# ${it.description}\n${it.key}=YOUR_${it.key}" }

            val envFile = ProjectFile(
                id = "${proj.id}_file_env",
                userId = proj.userId,
                projectId = proj.id,
                path = ".env",
                content = envContent,
                language = "env",
                sizeBytes = envContent.toByteArray().size,
                version = 1
            )

            val envExampleFile = ProjectFile(
                id = "${proj.id}_file_env_example",
                userId = proj.userId,
                projectId = proj.id,
                path = ".env.example",
                content = envExampleContent,
                language = "env",
                sizeBytes = envExampleContent.toByteArray().size,
                version = 1
            )

            repository.saveFile(envFile)
            repository.saveFile(envExampleFile)
            _statusBanner.value = "Synced ${secrets.size} secrets to .env and .env.example!"
        }
    }
}

class WorkspaceViewModelFactory(
    private val repository: FirestoreRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
