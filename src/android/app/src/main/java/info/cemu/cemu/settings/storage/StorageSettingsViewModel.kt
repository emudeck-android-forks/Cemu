package info.cemu.cemu.settings.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.cemu.cemu.common.android.context.internalFolder
import info.cemu.cemu.common.android.storage.StoragePathResolver
import info.cemu.cemu.common.settings.AppSettings
import info.cemu.cemu.common.settings.AppSettingsStore
import info.cemu.cemu.common.settings.StorageSettings
import info.cemu.cemu.common.settings.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageSettingsUiState(
    val showMigrationDialog: Boolean = false,
    val pendingSettings: StorageSettings? = null,
    val isMigrating: Boolean = false,
    val error: String? = null,
)

class StorageSettingsViewModel(
    private val dataStore: DataStore<AppSettings> = AppSettingsStore.dataStore,
) : ViewModel() {
    val storageSettings = dataStore.data.map { it.storageSettings }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            StorageSettings(),
        )

    private val _uiState = MutableStateFlow(StorageSettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun requestChangeToDefault(context: Context) {
        val current = storageSettings.value
        if (current.storageType == StorageType.DEFAULT) return

        val newSettings = StorageSettings(storageType = StorageType.DEFAULT)
        _uiState.value = _uiState.value.copy(
            showMigrationDialog = true,
            pendingSettings = newSettings,
        )
    }

    fun requestChangeToCustom(context: Context, treeUri: Uri) {
        val path = StoragePathResolver.resolveTreeUriToPath(context, treeUri)
        if (path == null || !StoragePathResolver.validatePath(path)) {
            _uiState.value = _uiState.value.copy(
                error = "Cannot use selected folder. Please choose a writable location.",
            )
            return
        }

        val newSettings = StorageSettings(
            storageType = StorageType.CUSTOM,
            customFolderPath = path,
        )
        _uiState.value = _uiState.value.copy(
            showMigrationDialog = true,
            pendingSettings = newSettings,
        )
    }

    fun dismissMigrationDialog() {
        _uiState.value = _uiState.value.copy(
            showMigrationDialog = false,
            pendingSettings = null,
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun migrateAndRestart(context: Context, migrate: Boolean) {
        val pendingSettings = _uiState.value.pendingSettings ?: return
        _uiState.value = _uiState.value.copy(
            showMigrationDialog = false,
            isMigrating = true,
        )

        viewModelScope.launch {
            val currentSettings = storageSettings.value
            val source = resolveFolder(context, currentSettings)
            val destination = resolveFolder(context, pendingSettings)

            if (migrate && source.absolutePath != destination.absolutePath) {
                withContext(Dispatchers.IO) {
                    destination.mkdirs()
                    source.copyRecursively(destination, overwrite = true)
                }
            }

            dataStore.updateData { it.copy(storageSettings = pendingSettings) }

            restartApp(context)
        }
    }

    private fun resolveFolder(context: Context, settings: StorageSettings): File {
        return when (settings.storageType) {
            StorageType.CUSTOM -> File(settings.customFolderPath!!)
            else -> context.applicationContext.internalFolder()
        }
    }

    private fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
