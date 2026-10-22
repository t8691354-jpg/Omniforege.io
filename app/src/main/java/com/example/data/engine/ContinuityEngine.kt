package com.example.data.engine

import com.example.data.ai.GeminiController
import com.example.data.model.ApiConnection
import com.example.data.model.Checkpoint
import com.example.data.model.DevelopmentTask
import com.example.data.model.Project
import com.example.data.model.ProjectFile
import com.example.data.model.ProjectStatus
import com.example.data.model.TaskStatus
import com.example.data.repository.FirestoreRepository
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ResumeResult(
    val resumedTask: DevelopmentTask?,
    val message: String,
    val isReadyToRun: Boolean
)

class ContinuityEngine(
    private val repository: FirestoreRepository
) {

    private fun hashFileContent(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun createCheckpoint(
        project: Project,
        tasks: List<DevelopmentTask>,
        files: List<ProjectFile>,
        interruptedTaskId: String? = null,
        checkpointTitle: String? = null,
        statusLabel: String = "saved"
    ): Checkpoint {
        val completed = tasks.filter { it.status == TaskStatus.COMPLETED.name }
        val pending = tasks.filter { it.status == TaskStatus.PENDING.name }
        val interrupted = tasks.firstOrNull { it.id == interruptedTaskId }

        // Compute File Manifest with real SHA256 hashes
        val manifestJson = JSONObject()
        val filesArray = JSONArray()
        for (f in files) {
            val item = JSONObject().apply {
                put("path", f.path)
                put("sizeBytes", f.sizeBytes)
                put("hash", hashFileContent(f.content))
                put("version", f.version)
            }
            filesArray.put(item)
        }
        manifestJson.put("files", filesArray)
        manifestJson.put("totalFiles", files.size)

        val contextSummary = GeminiController.synthesizeContinuityContext(
            project = project,
            completedTasks = completed,
            interruptedTask = interrupted,
            files = files
        )

        val nextVersion = (project.completedTasks) + 1
        val title = checkpointTitle ?: if (interrupted != null) {
            "Failover Checkpoint: ${interrupted.title} (Paused for Quota)"
        } else {
            "Checkpoint #$nextVersion: ${completed.lastOrNull()?.title ?: "Milestone Saved"}"
        }

        val checkpoint = Checkpoint(
            userId = project.userId,
            projectId = project.id,
            version = nextVersion,
            title = title,
            completedTaskCount = completed.size,
            completedTaskIds = completed.map { it.id },
            pendingTaskIds = pending.map { it.id },
            interruptedTaskId = interruptedTaskId,
            fileCount = files.size,
            filesManifestJson = manifestJson.toString(),
            contextSummary = contextSummary,
            lastProvider = project.activeProvider,
            lastModel = project.activeModel,
            status = statusLabel
        )

        val checkpointId = repository.saveCheckpoint(checkpoint)
        val savedCheckpoint = checkpoint.copy(id = checkpointId)

        // Update project with last checkpoint ID
        repository.updateProject(
            project.copy(
                lastCheckpointId = checkpointId,
                completedTasks = completed.size
            )
        )

        return savedCheckpoint
    }

    suspend fun prepareResumeFromCheckpoint(
        project: Project,
        checkpoint: Checkpoint,
        tasks: List<DevelopmentTask>,
        newConnection: ApiConnection
    ): ResumeResult {
        // Step 1: Find earliest incomplete task
        val eligibleTask = if (!checkpoint.interruptedTaskId.isNullOrBlank()) {
            tasks.firstOrNull { it.id == checkpoint.interruptedTaskId }
        } else {
            // Find earliest pending task whose dependencies are satisfied
            val completedSet = checkpoint.completedTaskIds.toSet()
            tasks.firstOrNull { task ->
                task.status != TaskStatus.COMPLETED.name &&
                        task.dependencies.all { completedSet.contains(it) || it.isBlank() }
            }
        }

        if (eligibleTask == null) {
            return ResumeResult(
                resumedTask = null,
                message = "All tasks have already been completed for project ${project.name}.",
                isReadyToRun = false
            )
        }

        // Step 2: Bind new provider and model to the project
        val updatedProject = project.copy(
            activeProvider = newConnection.provider,
            activeModel = newConnection.selectedModel,
            status = ProjectStatus.RUNNING.name,
            currentTaskId = eligibleTask.id
        )
        repository.updateProject(updatedProject)

        // Reset interrupted task to IN_PROGRESS
        repository.saveTask(
            eligibleTask.copy(
                status = TaskStatus.IN_PROGRESS.name,
                providerUsed = newConnection.provider,
                modelUsed = newConnection.selectedModel,
                errorMessage = null
            )
        )

        return ResumeResult(
            resumedTask = eligibleTask,
            message = "Project successfully resumed at task #${eligibleTask.orderIndex}: '${eligibleTask.title}' using ${newConnection.name} (${newConnection.selectedModel}). Completed files were preserved.",
            isReadyToRun = true
        )
    }
}
