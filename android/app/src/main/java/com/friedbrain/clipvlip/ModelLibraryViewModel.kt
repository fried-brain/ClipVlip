package com.friedbrain.clipvlip

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ModelLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ModelDownloadRepository(application.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    val installed = mutableStateMapOf<String, Boolean>()
    val progress = mutableStateMapOf<String, Float>()
    val busy = mutableStateMapOf<String, Boolean>()
    val errors = mutableStateMapOf<String, String>()

    init {
        viewModelScope.launch {
            ModelCatalog.models.forEach { model ->
                installed[model.id] = repository.isInstalled(model)
            }
        }
    }

    fun download(model: ModelArtifact) {
        if (busy[model.id] == true || installed[model.id] == true) return
        errors.remove(model.id)
        busy[model.id] = true
        progress[model.id] = 0f
        viewModelScope.launch {
            try {
                repository.download(model) { downloaded, total ->
                    mainHandler.post {
                        progress[model.id] = (downloaded.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    }
                }
                installed[model.id] = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                errors[model.id] = error.message ?: "Download failed. Please try again."
            } finally {
                busy[model.id] = false
            }
        }
    }

    fun remove(model: ModelArtifact) {
        if (busy[model.id] == true) return
        repository.delete(model)
        if (repository.installedFile(model).exists()) {
            installed[model.id] = true
            errors[model.id] = "Could not remove this model. Check storage permissions and try again."
            return
        }
        installed[model.id] = false
        progress.remove(model.id)
        errors.remove(model.id)
    }
}
