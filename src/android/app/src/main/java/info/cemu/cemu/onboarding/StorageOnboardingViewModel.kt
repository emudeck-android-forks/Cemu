package info.cemu.cemu.onboarding

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.cemu.cemu.CemuApplication
import info.cemu.cemu.common.android.context.internalFolder
import info.cemu.cemu.common.android.storage.StoragePathResolver
import info.cemu.cemu.common.settings.AppSettings
import info.cemu.cemu.common.settings.AppSettingsStore
import info.cemu.cemu.common.settings.StorageSettings
import info.cemu.cemu.common.settings.StorageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class OnboardingUiState(
    val selectedType: StorageType = StorageType.NOT_SET,
    val customPath: String? = null,
    val error: String? = null,
    val isConfirming: Boolean = false,
)

class StorageOnboardingViewModel(
    private val dataStore: DataStore<AppSettings> = AppSettingsStore.dataStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    fun selectDefault() {
        _uiState.value = _uiState.value.copy(
            selectedType = StorageType.DEFAULT,
            customPath = null,
            error = null,
        )
    }

    fun selectCustom(context: Context, treeUri: Uri) {
        val path = StoragePathResolver.resolveTreeUriToPath(context, treeUri)
        if (path == null || !StoragePathResolver.validatePath(path)) {
            _uiState.value = _uiState.value.copy(
                error = "Cannot use selected folder. Please choose a writable location.",
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedType = StorageType.CUSTOM,
            customPath = path,
            error = null,
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun confirm(context: Context, onComplete: () -> Unit) {
        val state = _uiState.value
        if (state.selectedType == StorageType.NOT_SET) return
        if (state.isConfirming) return

        _uiState.value = state.copy(isConfirming = true)

        viewModelScope.launch {
            val storageSettings = StorageSettings(
                storageType = state.selectedType,
                customFolderPath = state.customPath,
            )
            dataStore.updateData { it.copy(storageSettings = storageSettings) }

            val folder = when (state.selectedType) {
                StorageType.CUSTOM -> File(state.customPath!!)
                else -> context.applicationContext.internalFolder()
            }

            (context.applicationContext as CemuApplication).initializeCemuWithFolder(folder)

            onComplete()
        }
    }
}
