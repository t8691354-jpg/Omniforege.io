package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TargetPlatform(val displayName: String, val extensionHint: String) {
    ANDROID("Android (Kotlin/Compose)", "kt"),
    IOS("iOS (Swift/SwiftUI)", "swift"),
    WEB("Full-Stack Web (React/TS)", "tsx"),
    FLUTTER("Cross-Platform (Flutter/Dart)", "dart"),
    REST_API("Backend / REST API (Node/Go)", "ts"),
    CLI("CLI & Automation Utility", "py")
}

enum class ProjectStatus(val label: String) {
    PLANNING("Planning"),
    READY("Ready"),
    RUNNING("Building"),
    TESTING("Verifying & Testing"),
    RECOVERING("Auto-Recovering"),
    PAUSED_QUOTA("Paused (Quota Exceeded)"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

enum class TaskStatus(val label: String) {
    QUEUED("Queued"),
    PLANNING("Planning"),
    RUNNING("Running"),
    WAITING_FOR_TOOL("Waiting for Tool"),
    TESTING("Testing"),
    RECOVERING("Recovering"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    // Backwards-compatible aliases
    PENDING("Queued"),
    IN_PROGRESS("Running"),
    INTERRUPTED("Quota Interrupted")
}

enum class ApiProviderType(val displayName: String, val defaultBaseUrl: String, val suggestedModels: List<String>) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/", listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-3.1-pro-preview", "gemini-flash-latest")),
    OPENAI("OpenAI", "https://api.openai.com/v1/", listOf("gpt-4o", "gpt-4o-mini", "o3-mini")),
    CLAUDE("Anthropic Claude", "https://api.anthropic.com/v1/", listOf("claude-3-5-sonnet", "claude-3-5-haiku")),
    OPENAI_COMPATIBLE("OpenAI-Compatible / Custom", "https://api.groq.com/openai/v1/", listOf("llama-3.3-70b", "deepseek-r1")),
    CUSTOM("Custom REST Adapter", "", listOf("custom-agent-v1"))
}

enum class ConnectionStatus(val label: String) {
    ACTIVE("Active"),
    QUOTA_EXCEEDED("Quota Exceeded (429)"),
    RATE_LIMITED("Rate Limited"),
    INVALID_KEY("Invalid Key (401)"),
    ERROR("Error"),
    CIRCUIT_TRIPPED("Circuit Breaker Tripped"),
    DISABLED("Disabled")
}

@IgnoreExtraProperties
data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Project(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val targetPlatform: String = TargetPlatform.ANDROID.name,
    val language: String = "Kotlin",
    val framework: String = "Jetpack Compose",
    val status: String = ProjectStatus.PLANNING.name,
    val activeProvider: String = ApiProviderType.GEMINI.name,
    val activeModel: String = "gemini-3.8-flash",
    val currentTaskId: String? = null,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val lastCheckpointId: String? = null,
    val inputTokensUsed: Long = 0L,
    val outputTokensUsed: Long = 0L,
    val totalLatencyMs: Long = 0L,
    val estimatedCostUsd: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ApiConnection(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val provider: String = ApiProviderType.GEMINI.name,
    val maskedKey: String = "",
    val customBaseUrl: String? = null,
    val selectedModel: String = "gemini-3.8-flash",
    val status: String = ConnectionStatus.ACTIVE.name,
    val lastError: String? = null,
    val priority: Int = 1,
    val failureCount: Int = 0,
    val latencyMs: Long = 0L,
    val totalRequests: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class AgentRole(val title: String, val badgeColorHex: Long) {
    CHIEF_ARCHITECT("Chief AI Architect", 0xFF8B5CF6),
    FULLSTACK_ENGINEER("Full-Stack Engineer", 0xFF0284C7),
    SECURITY_AUDITOR("OWASP Security Auditor", 0xFF10B981),
    DEVOPS_SPECIALIST("DevOps CI/CD Specialist", 0xFFF59E0B)
}

@IgnoreExtraProperties
data class DevelopmentTask(
    val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val title: String = "",
    val description: String = "",
    val orderIndex: Int = 0,
    val status: String = TaskStatus.QUEUED.name,
    val assignedAgentRole: String = AgentRole.FULLSTACK_ENGINEER.name,
    val securityAuditPassed: Boolean = true,
    val architectureScore: Int = 98,
    val providerUsed: String? = null,
    val modelUsed: String? = null,
    val dependencies: List<String> = emptyList(),
    val outputSummary: String? = null,
    val targetFiles: List<String> = emptyList(),
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val executionLogs: List<String> = emptyList(),
    val fileHashesJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Checkpoint(
    val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val version: Int = 1,
    val title: String = "",
    val completedTaskCount: Int = 0,
    val completedTaskIds: List<String> = emptyList(),
    val pendingTaskIds: List<String> = emptyList(),
    val interruptedTaskId: String? = null,
    val fileCount: Int = 0,
    val filesManifestJson: String = "{}",
    val contextSummary: String = "",
    val lastProvider: String = "",
    val lastModel: String = "",
    val status: String = "saved",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ProjectFile(
    val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val path: String = "",
    val content: String = "",
    val language: String = "kotlin",
    val sizeBytes: Int = 0,
    val version: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val role: String = "user",
    val content: String = "",
    val provider: String? = null,
    val model: String? = null,
    val isCheckpointEvent: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ProjectSecret(
    val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val key: String = "",
    val value: String = "",
    val description: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TelemetryUsageRecord(
    val provider: String = "",
    val model: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val latencyMs: Long = 0L,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProviderCapability(
    val provider: ApiProviderType,
    val supportsGrounding: Boolean,
    val supportsCodeGeneration: Boolean,
    val supportsStreaming: Boolean,
    val contextWindowTokens: Int,
    val maxOutputTokens: Int,
    val rateLimitRpm: Int,
    val costPer1MInputTokensUsd: Double,
    val costPer1MOutputTokensUsd: Double
)

