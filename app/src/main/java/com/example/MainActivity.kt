package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.credentials.CredentialManager
import kotlinx.coroutines.launch
import com.example.data.repository.FirestoreRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.projects.ProjectListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.workspace.WorkspaceScreen
import com.example.ui.workspace.WorkspaceViewModel
import com.example.ui.workspace.WorkspaceViewModelFactory
import com.example.data.secrets.UserSecretsManager
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserSecretsManager.init(this)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val auth = FirebaseAuth.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fbAuth ->
            currentUser = fbAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = auth.currentUser
            }
        )
    } else {
        AuthenticatedWorkspaceRoot(userId = currentUser!!.uid)
    }
}

@Composable
fun AuthenticatedWorkspaceRoot(userId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val repository = remember(context) { FirestoreRepository(context) }
    val viewModel: WorkspaceViewModel = viewModel(
        factory = WorkspaceViewModelFactory(repository)
    )

    val activeProject by viewModel.activeProject.collectAsState()
    val projects by viewModel.projects.collectAsState()

    if (activeProject != null) {
        BackHandler {
            // Return from workspace to project list
            viewModel.selectProject(null as com.example.data.model.Project?)
        }
        WorkspaceScreen(
            viewModel = viewModel,
            onBackToProjects = {
                viewModel.selectProject(null as com.example.data.model.Project?)
            }
        )
    } else {
        ProjectListScreen(
            projects = projects,
            onSelectProject = { proj ->
                viewModel.selectProject(proj)
            },
            onCreateProject = { name, desc, platform, provider, model ->
                viewModel.createProject(name, desc, platform, provider, model)
            },
            onDeleteProject = { id ->
                scope.launch { viewModel.repository.deleteProject(id) }
            },
            onSignOut = {
                signOutUser(
                    context = context,
                    credentialManager = credentialManager,
                    onSignOutComplete = {},
                    scope = scope
                )
            }
        )
    }
}
