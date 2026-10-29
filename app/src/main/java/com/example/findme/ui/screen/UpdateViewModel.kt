package com.example.findme.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.BuildConfig
import com.example.findme.data.repository.AppRelease
import com.example.findme.data.repository.AppUpdateRepository
import com.example.findme.data.repository.ApkDownloader
import com.example.findme.util.ApkUpdateInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateState(
    val checking: Boolean = false,
    val release: AppRelease? = null,
    val message: String = "Se comprueban las actualizaciones al abrir la app.",
    val showDialog: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedApk: File? = null,
    val requestInstall: Boolean = false
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppUpdateRepository()
    private val downloader = ApkDownloader()
    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()
    private var downloadJob: Job? = null
    init { check() }

    fun check() {
        if (_state.value.checking || _state.value.downloading || _state.value.requestInstall) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            try {
                val release = repository.latest(BuildConfig.VERSION_NAME)
                _state.value = UpdateState(release = release,
                    message = if (release == null) "Tienes la versión más reciente." else "Hay una actualización de FindMe disponible.",
                    showDialog = release != null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(checking = false, message = "No se pudo buscar la actualización. Intenta otra vez.") }
            }
        }
    }

    fun update() {
        if (_state.value.downloading || _state.value.checking) return
        val release = _state.value.release ?: return
        _state.update { it.copy(downloading = true, progress = 0f, showDialog = true, message = "Descargando actualización…") }
        downloadJob = viewModelScope.launch {
            try {
                val file = _state.value.downloadedApk?.takeIf { it.isFile } ?: downloader.download(
                    release.apk, File(getApplication<Application>().cacheDir, "updates")
                ) { progress -> _state.update { it.copy(progress = progress) } }
                withContext(Dispatchers.IO) { ApkUpdateInstaller.validate(getApplication(), file, release.version) }
                _state.update { it.copy(downloading = false, downloadedApk = file, requestInstall = true,
                    message = "Actualización lista para instalar.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(downloading = false, downloadedApk = null, requestInstall = false,
                    message = "No se pudo preparar la actualización. Intenta otra vez.", showDialog = true) }
            }
        }
    }

    fun consumeInstallRequest() { _state.update { it.copy(requestInstall = false) } }
    fun requestInstallation() {
        if (_state.value.downloadedApk?.isFile == true) _state.update { it.copy(requestInstall = true) }
    }
    fun permissionRequested() { _state.update { it.copy(showDialog = false, message = "Autoriza a FindMe para instalar la actualización.") } }
    fun installerOpened() { _state.update { it.copy(showDialog = false, message = "Confirma la actualización en Android.") } }
    fun installationUnavailable() { _state.update { it.copy(showDialog = true, requestInstall = false,
        message = "Permite que FindMe instale actualizaciones e intenta otra vez.") } }
    fun installationCancelled() { _state.update { it.copy(showDialog = false, requestInstall = false,
        message = "La instalación no se completó. Puedes intentarlo otra vez.") } }
    fun cancelDownload() {
        downloadJob?.cancel()
        _state.update { it.copy(downloading = false, progress = 0f, showDialog = false, message = "Actualización pospuesta.") }
    }
    fun dismiss() { if (!_state.value.downloading) _state.update { it.copy(showDialog = false) } }
}
