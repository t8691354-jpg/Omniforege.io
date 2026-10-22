package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ApiConnection
import com.example.data.model.ChatMessage
import com.example.data.model.Checkpoint
import com.example.data.model.DevelopmentTask
import com.example.data.model.Project
import com.example.data.model.ProjectFile
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

class FirestoreRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Profile ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { it.toObject(UserProfile::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                    throw e
                }
        )
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to profile.displayName,
                "email" to profile.email,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            profile.photoUrl?.let { payload["photoUrl"] = it }
            if (profile.createdAt == null) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Projects ---
    fun observeProjects(): Flow<List<Project>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Project::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    fun observeProject(projectId: String): Flow<Project?> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects/$projectId"
        emitAll(
            db.collection("users").document(uid).collection("projects").document(projectId)
                .snapshots()
                .map { it.toObject(Project::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                    throw e
                }
        )
    }

    suspend fun createProject(project: Project): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("projects")
        val docRef = if (project.id.isNotBlank()) colRef.document(project.id) else colRef.document()
        val assignedId = docRef.id
        try {
            val payload = mapOf(
                "id" to assignedId,
                "userId" to uid,
                "name" to project.name,
                "description" to project.description,
                "targetPlatform" to project.targetPlatform,
                "language" to project.language,
                "framework" to project.framework,
                "status" to project.status,
                "activeProvider" to project.activeProvider,
                "activeModel" to project.activeModel,
                "totalTasks" to project.totalTasks,
                "completedTasks" to project.completedTasks,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            return assignedId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateProject(project: Project) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document(project.id)
        try {
            val payload = mutableMapOf<String, Any>(
                "name" to project.name,
                "description" to project.description,
                "targetPlatform" to project.targetPlatform,
                "language" to project.language,
                "framework" to project.framework,
                "status" to project.status,
                "activeProvider" to project.activeProvider,
                "activeModel" to project.activeModel,
                "totalTasks" to project.totalTasks,
                "completedTasks" to project.completedTasks,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            project.currentTaskId?.let { payload["currentTaskId"] = it }
            project.lastCheckpointId?.let { payload["lastCheckpointId"] = it }
            docRef.update(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProject(projectId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- API Connections ---
    fun observeApiConnections(): Flow<List<ApiConnection>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/api_connections"
        emitAll(
            db.collection("users").document(uid).collection("api_connections")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ApiConnection::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun saveApiConnection(conn: ApiConnection): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("api_connections")
        val docRef = if (conn.id.isNotBlank()) colRef.document(conn.id) else colRef.document()
        val assignedId = docRef.id
        try {
            val payload = mutableMapOf<String, Any>(
                "id" to assignedId,
                "userId" to uid,
                "name" to conn.name,
                "provider" to conn.provider,
                "maskedKey" to conn.maskedKey,
                "selectedModel" to conn.selectedModel,
                "status" to conn.status,
                "priority" to conn.priority,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            conn.customBaseUrl?.let { payload["customBaseUrl"] = it }
            conn.lastError?.let { payload["lastError"] = it }
            if (conn.createdAt == null) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
            return assignedId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteApiConnection(connId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("api_connections").document(connId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Tasks ---
    fun observeTasks(projectId: String): Flow<List<DevelopmentTask>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects/$projectId/tasks"
        emitAll(
            db.collection("users").document(uid).collection("projects").document(projectId).collection("tasks")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(DevelopmentTask::class.java).sortedBy { it.orderIndex } }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun saveTask(task: DevelopmentTask): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("projects").document(task.projectId).collection("tasks")
        val docRef = if (task.id.isNotBlank()) colRef.document(task.id) else colRef.document()
        val assignedId = docRef.id
        try {
            val payload = mutableMapOf<String, Any>(
                "id" to assignedId,
                "userId" to uid,
                "projectId" to task.projectId,
                "title" to task.title,
                "description" to task.description,
                "orderIndex" to task.orderIndex,
                "status" to task.status,
                "dependencies" to task.dependencies,
                "targetFiles" to task.targetFiles,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            task.providerUsed?.let { payload["providerUsed"] = it }
            task.modelUsed?.let { payload["modelUsed"] = it }
            task.outputSummary?.let { payload["outputSummary"] = it }
            task.errorMessage?.let { payload["errorMessage"] = it }
            if (task.createdAt == null) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
            return assignedId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Checkpoints ---
    fun observeCheckpoints(projectId: String): Flow<List<Checkpoint>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects/$projectId/checkpoints"
        emitAll(
            db.collection("users").document(uid).collection("projects").document(projectId).collection("checkpoints")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Checkpoint::class.java).sortedByDescending { it.version } }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun saveCheckpoint(checkpoint: Checkpoint): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("projects").document(checkpoint.projectId).collection("checkpoints")
        val docRef = if (checkpoint.id.isNotBlank()) colRef.document(checkpoint.id) else colRef.document()
        val assignedId = docRef.id
        try {
            val payload = mutableMapOf<String, Any>(
                "id" to assignedId,
                "userId" to uid,
                "projectId" to checkpoint.projectId,
                "version" to checkpoint.version,
                "title" to checkpoint.title,
                "completedTaskCount" to checkpoint.completedTaskCount,
                "completedTaskIds" to checkpoint.completedTaskIds,
                "pendingTaskIds" to checkpoint.pendingTaskIds,
                "fileCount" to checkpoint.fileCount,
                "filesManifestJson" to checkpoint.filesManifestJson,
                "contextSummary" to checkpoint.contextSummary,
                "lastProvider" to checkpoint.lastProvider,
                "lastModel" to checkpoint.lastModel,
                "status" to checkpoint.status,
                "createdAt" to FieldValue.serverTimestamp()
            )
            checkpoint.interruptedTaskId?.let { payload["interruptedTaskId"] = it }
            docRef.set(payload).await()
            return assignedId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- Source Files ---
    fun observeFiles(projectId: String): Flow<List<ProjectFile>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects/$projectId/files"
        emitAll(
            db.collection("users").document(uid).collection("projects").document(projectId).collection("files")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ProjectFile::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun saveFile(file: ProjectFile): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("projects").document(file.projectId).collection("files")
        val docRef = if (file.id.isNotBlank()) colRef.document(file.id) else colRef.document()
        val assignedId = docRef.id
        try {
            val payload = mapOf(
                "id" to assignedId,
                "userId" to uid,
                "projectId" to file.projectId,
                "path" to file.path,
                "content" to file.content,
                "language" to file.language,
                "sizeBytes" to file.content.toByteArray().size,
                "version" to file.version,
                "createdAt" to (file.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
            return assignedId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFile(projectId: String, fileId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId).collection("files").document(fileId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Chat Messages ---
    fun observeMessages(projectId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects/$projectId/messages"
        emitAll(
            db.collection("users").document(uid).collection("projects").document(projectId).collection("messages")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ChatMessage::class.java).sortedBy { it.createdAt?.seconds ?: 0L } }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun saveMessage(message: ChatMessage): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("projects").document(message.projectId).collection("messages")
        val docRef = if (message.id.isNotBlank()) colRef.document(message.id) else colRef.document()
        val assignedId = docRef.id
        try {
            val payload = mutableMapOf<String, Any>(
                "id" to assignedId,
                "userId" to uid,
                "projectId" to message.projectId,
                "role" to message.role,
                "content" to message.content,
                "isCheckpointEvent" to message.isCheckpointEvent,
                "createdAt" to FieldValue.serverTimestamp()
            )
            message.provider?.let { payload["provider"] = it }
            message.model?.let { payload["model"] = it }
            docRef.set(payload).await()
            return assignedId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
