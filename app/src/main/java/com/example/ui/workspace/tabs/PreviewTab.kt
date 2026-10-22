package com.example.ui.workspace.tabs

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.engine.CloudBuildWorker
import com.example.data.model.Project
import com.example.data.model.ProjectFile

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewTab(
    project: Project,
    files: List<ProjectFile>
) {
    val context = LocalContext.current
    var isPhoneFrame by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val consoleLogs = remember { mutableStateListOf<String>() }

    val previewHtml = remember(project, files) {
        CloudBuildWorker.generateInteractivePreviewHtml(project, files)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .padding(12.dp)
    ) {
        // Toolbar: Controls & Device toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Interactive Preview",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9)
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isPhoneFrame = !isPhoneFrame },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isPhoneFrame) Icons.Default.Tablet else Icons.Default.Smartphone,
                        contentDescription = "Toggle Frame Size",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        webViewRef?.loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
                        consoleLogs.add("[Preview] Reloaded at ${System.currentTimeMillis() % 100000}ms")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Preview",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Simulated Mobile Device Frame
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .then(
                        if (isPhoneFrame) {
                            Modifier
                                .fillMaxWidth(0.92f)
                                .fillMaxSize()
                        } else {
                            Modifier.fillMaxSize()
                        }
                    )
                    .border(3.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                    .testTag("interactive_preview_container"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF000000)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mobile Notch Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                        )
                    }

                    // Android WebView rendering interactive preview
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        consoleMessage?.message()?.let { msg ->
                                            consoleLogs.add("[Console] $msg")
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }
                                webViewClient = WebViewClient()
                                loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
                                webViewRef = this
                            }
                        },
                        update = { wv ->
                            wv.loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Console Log Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Preview Console (${consoleLogs.size} events)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = consoleLogs.takeLast(2).joinToString("\n").ifBlank { "Live interactive sandbox running. Tap buttons inside phone frame to test interactions." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    maxLines = 2
                )
            }
        }
    }
}
