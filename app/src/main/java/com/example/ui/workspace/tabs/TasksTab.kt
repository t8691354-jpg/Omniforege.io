package com.example.ui.workspace.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DevelopmentTask
import com.example.data.model.TaskStatus

@Composable
fun TasksTab(
    tasks: List<DevelopmentTask>,
    isGenerating: Boolean,
    onRunAutonomousPlan: () -> Unit,
    onPauseExecution: () -> Unit = {},
    onCancelExecution: () -> Unit = {}
) {
    var selectedTaskForDetails by remember { mutableStateOf<DevelopmentTask?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Autonomous Agent Tasks",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9)
                    )
                )
                Text(
                    text = "${tasks.count { it.status == TaskStatus.COMPLETED.name }} of ${tasks.size} steps completed",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isGenerating) {
                    OutlinedButton(
                        onClick = onPauseExecution,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("pause_tasks_action_button")
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onCancelExecution,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("cancel_tasks_action_button")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onRunAutonomousPlan,
                        enabled = tasks.any { it.status != TaskStatus.COMPLETED.name },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("run_tasks_action_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Execute Plan")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tasks List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tasks) { task ->
                TaskItemCard(
                    task = task,
                    onClick = { selectedTaskForDetails = task }
                )
            }
        }

        // Task Details Modal
        selectedTaskForDetails?.let { task ->
            TaskDetailDialog(
                task = task,
                onDismiss = { selectedTaskForDetails = null }
            )
        }
    }
}

@Composable
private fun TaskItemCard(
    task: DevelopmentTask,
    onClick: () -> Unit
) {
    val (statusColor, statusIcon, statusLabel) = when (task.status) {
        TaskStatus.COMPLETED.name -> Triple(Color(0xFF10B981), Icons.Default.CheckCircle, "Completed")
        TaskStatus.RUNNING.name, TaskStatus.IN_PROGRESS.name -> Triple(Color(0xFF38BDF8), Icons.Default.HourglassBottom, "Running")
        TaskStatus.PLANNING.name -> Triple(Color(0xFFA855F7), Icons.Default.AutoAwesome, "Planning")
        TaskStatus.WAITING_FOR_TOOL.name -> Triple(Color(0xFFF59E0B), Icons.Default.Build, "Waiting for Tool")
        TaskStatus.TESTING.name -> Triple(Color(0xFF06B6D4), Icons.Default.Science, "Testing & Verifying")
        TaskStatus.RECOVERING.name -> Triple(Color(0xFFF97316), Icons.Default.Refresh, "Auto-Recovering (${task.retryCount})")
        TaskStatus.INTERRUPTED.name -> Triple(Color(0xFFEF4444), Icons.Default.Warning, "Quota Paused")
        TaskStatus.FAILED.name -> Triple(Color(0xFFF87171), Icons.Default.Close, "Failed")
        TaskStatus.CANCELLED.name -> Triple(Color(0xFF6B7280), Icons.Default.Close, "Cancelled")
        else -> Triple(Color(0xFF64748B), Icons.Default.Schedule, "Queued")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("task_card_${task.orderIndex}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(statusColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${task.orderIndex + 1} • ${task.title}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )
                }

                // Multi-Agent Specialist Badge Row (×200 Architecture)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val roleLabel = when (task.orderIndex % 4) {
                        0 -> "Chief AI Architect"
                        1 -> "Senior Full-Stack Engineer"
                        2 -> "OWASP Security Auditor"
                        else -> "DevOps CI/CD Specialist"
                    }
                    val roleColor = when (task.orderIndex % 4) {
                        0 -> Color(0xFF8B5CF6)
                        1 -> Color(0xFF0284C7)
                        2 -> Color(0xFF10B981)
                        else -> Color(0xFFF59E0B)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(roleColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = roleColor
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x1F10B981))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Score: ${task.architectureScore}/100",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        )
                    }
                }

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                        maxLines = 2
                    )
                }

                if (task.targetFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        task.targetFiles.take(3).forEach { file ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF0F172A))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = file.substringAfterLast("/"),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskDetailDialog(
    task: DevelopmentTask,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Task #${task.orderIndex + 1}: ${task.title}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Specification:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                )
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0))
                )

                if (task.targetFiles.isNotEmpty()) {
                    Text(
                        text = "Target Files:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    )
                    task.targetFiles.forEach { f ->
                        Text("• $f", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    }
                }

                if (!task.outputSummary.isNullOrBlank()) {
                    Text(
                        text = "Output Summary:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    )
                    Text(task.outputSummary ?: "", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1))
                }

                if (!task.errorMessage.isNullOrBlank()) {
                    Text(
                        text = "Error Information:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    )
                    Text(task.errorMessage ?: "", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFCA5A5))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}
