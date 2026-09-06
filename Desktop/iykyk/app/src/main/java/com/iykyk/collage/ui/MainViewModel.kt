package com.iykyk.collage.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iykyk.collage.data.storage.MediaExporter
import com.iykyk.collage.domain.model.ProcessingResult
import com.iykyk.collage.domain.model.ProcessingState
import com.iykyk.collage.processing.VideoProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val videoProcessor = VideoProcessor(application.applicationContext)
    private val mediaExporter = MediaExporter(application.applicationContext)

    val processingState: StateFlow<ProcessingState> = videoProcessor.processingState

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var processingJob: Job? = null

    fun selectVideo(uri: Uri) {
        _selectedVideoUri.value = uri
        startProcessing(uri)
    }

    fun startProcessing(uri: Uri) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            try {
                videoProcessor.processVideo(uri)
            } catch (e: Exception) {
                // Handled in VideoProcessor state updates
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        resetToIdle()
    }

    fun saveCollageToGallery() {
        val currentState = processingState.value
        if (currentState is ProcessingState.Success) {
            val bitmap = currentState.result.collageBitmap
            if (bitmap != null) {
                viewModelScope.launch {
                    val savedUri = mediaExporter.saveCollageToGallery(bitmap)
                    if (savedUri != null) {
                        _userMessage.value = "Collage successfully saved to Gallery!"
                    } else {
                        _userMessage.value = "Failed to save collage to Gallery."
                    }
                }
            }
        }
    }

    fun shareCollage() {
        val currentState = processingState.value
        if (currentState is ProcessingState.Success) {
            val bitmap = currentState.result.collageBitmap
            if (bitmap != null) {
                viewModelScope.launch {
                    mediaExporter.shareCollage(bitmap)
                }
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun resetToIdle() {
        _selectedVideoUri.value = null
        processingJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        videoProcessor.close()
    }
}
