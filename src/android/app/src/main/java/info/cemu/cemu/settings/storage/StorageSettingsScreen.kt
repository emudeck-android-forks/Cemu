package info.cemu.cemu.settings.storage

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.cemu.cemu.common.settings.StorageType
import info.cemu.cemu.common.ui.components.Button
import info.cemu.cemu.common.ui.components.ScreenContent
import info.cemu.cemu.common.ui.localization.tr

@Composable
fun StorageSettingsScreen(
    navigateBack: () -> Unit,
    viewModel: StorageSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val storageSettings by viewModel.storageSettings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.requestChangeToCustom(context, uri)
        }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    if (uiState.showMigrationDialog) {
        MigrationDialog(
            onDismiss = { viewModel.dismissMigrationDialog() },
            onMigrateAndRestart = { viewModel.migrateAndRestart(context, migrate = true) },
            onSwitchWithoutCopying = { viewModel.migrateAndRestart(context, migrate = false) },
        )
    }

    ScreenContent(
        appBarText = tr("Storage location"),
        navigateBack = navigateBack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) {
        if (uiState.isMigrating) {
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = tr("Migrating data..."),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = tr("Current location"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
                Text(
                    text = when (storageSettings.storageType) {
                        StorageType.CUSTOM -> storageSettings.customFolderPath
                            ?: tr("Custom folder")

                        else -> tr("Default (app private storage)")
                    },
                    fontSize = 14.sp,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                )
            }

            if (storageSettings.storageType != StorageType.DEFAULT) {
                Button(
                    label = tr("Use default location"),
                    description = tr("Move data to app private storage"),
                    onClick = { viewModel.requestChangeToDefault(context) },
                )
            }

            Button(
                label = if (storageSettings.storageType == StorageType.CUSTOM) {
                    tr("Change custom folder")
                } else {
                    tr("Choose custom folder")
                },
                description = tr("Select a folder to keep data safe from uninstall"),
                onClick = { launcher.launch(null) },
            )
        }
    }
}

@Composable
private fun MigrationDialog(
    onDismiss: () -> Unit,
    onMigrateAndRestart: () -> Unit,
    onSwitchWithoutCopying: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = tr("Change storage location")) },
        text = {
            Text(text = tr("Do you want to copy your existing data to the new location? The app will restart after this change."))
        },
        confirmButton = {
            TextButton(onClick = onMigrateAndRestart) {
                Text(text = tr("Copy and restart"))
            }
        },
        dismissButton = {
            TextButton(onClick = onSwitchWithoutCopying) {
                Text(text = tr("Switch without copying"))
            }
        },
    )
}
