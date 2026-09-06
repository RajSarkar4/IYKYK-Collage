package com.iykyk.collage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.iykyk.collage.domain.model.ProcessingState
import com.iykyk.collage.ui.MainViewModel
import com.iykyk.collage.ui.screens.CollageScreen
import com.iykyk.collage.ui.screens.HomeScreen
import com.iykyk.collage.ui.screens.ProcessingScreen
import com.iykyk.collage.ui.screens.ResultsScreen
import com.iykyk.collage.ui.theme.IYKYKCollageTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "Media permissions are required to process videos.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            IYKYKCollageTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val processingState by viewModel.processingState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showingCollageView by remember { mutableStateOf(true) }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = processingState) {
            is ProcessingState.Idle -> {
                showingCollageView = true
                HomeScreen(
                    onVideoSelected = { uri ->
                        viewModel.selectVideo(uri)
                    }
                )
            }

            is ProcessingState.Processing -> {
                showingCollageView = true
                ProcessingScreen(
                    state = state,
                    onCancel = {
                        viewModel.cancelProcessing()
                    }
                )
            }

            is ProcessingState.Success -> {
                if (showingCollageView) {
                    CollageScreen(
                        collageBitmap = state.result.collageBitmap,
                        onSaveToGallery = { viewModel.saveCollageToGallery() },
                        onShare = { viewModel.shareCollage() },
                        onBackToHome = {
                            showingCollageView = false
                            viewModel.resetToIdle()
                        }
                    )
                } else {
                    ResultsScreen(
                        result = state.result,
                        onViewCollage = {
                            showingCollageView = true
                        }
                    )
                }
            }

            is ProcessingState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetToIdle() },
                    title = { Text("Processing Error") },
                    text = { Text(state.message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.resetToIdle() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
