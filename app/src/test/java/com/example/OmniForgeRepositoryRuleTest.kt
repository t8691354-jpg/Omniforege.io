package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Checkpoint
import com.example.data.model.DevelopmentTask
import com.example.data.model.Project
import com.example.data.model.ProjectFile
import com.example.data.model.ProjectStatus
import com.example.data.model.TaskStatus
import com.example.data.repository.FirestoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniForgeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createProject_authenticatedUser_succeedsAndReturnsId() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreRepository(firestore, auth)

        val project = Project(
            userId = aliceUid,
            name = "Robotics Fleet Manager",
            description = "Real-time robotics coordination system",
            targetPlatform = "ANDROID",
            language = "Kotlin",
            framework = "Compose",
            status = ProjectStatus.READY.name
        )

        val projectId = repository.createProject(project)
        assertNotNull(projectId)
        assertTrue(projectId.isNotBlank())

        val fetched = repository.observeProject(projectId).first()
        assertNotNull(fetched)
        assertEquals("Robotics Fleet Manager", fetched?.name)
    }

    @Test
    fun checkpoint_preservesProjectStateAndContinuity() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreRepository(firestore, auth)

        val project = Project(
            userId = aliceUid,
            name = "Fintech Dashboard",
            targetPlatform = "WEB",
            status = ProjectStatus.RUNNING.name
        )
        val projectId = repository.createProject(project)

        val task1 = DevelopmentTask(
            userId = aliceUid,
            projectId = projectId,
            title = "Scaffold Config",
            orderIndex = 0,
            status = TaskStatus.COMPLETED.name
        )
        val taskId = repository.saveTask(task1)

        val checkpoint = Checkpoint(
            userId = aliceUid,
            projectId = projectId,
            version = 1,
            title = "Milestone 1: Scaffold Config Done",
            completedTaskCount = 1,
            completedTaskIds = listOf(taskId),
            contextSummary = "Task 1 completed. Ready for Task 2.",
            status = "saved"
        )
        val checkpointId = repository.saveCheckpoint(checkpoint)
        assertNotNull(checkpointId)

        val checkpoints = repository.observeCheckpoints(projectId).first()
        assertTrue(checkpoints.isNotEmpty())
        assertEquals("Milestone 1: Scaffold Config Done", checkpoints.first().title)
    }

    @Test
    fun unauthenticatedAccess_failsSafely() = runBlocking {
        auth.signOut()
        val repository = FirestoreRepository(firestore, auth)

        var caughtException = false
        try {
            repository.createProject(Project(name = "Unauthenticated Project"))
        } catch (e: Exception) {
            caughtException = true
        }
        assertTrue("Unauthenticated call must fail safely", caughtException)
    }
}
