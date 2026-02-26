package info.cemu.cemu.onboarding

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import info.cemu.cemu.common.ui.localization.tr

@Composable
fun StorageOnboardingScreen(
    onComplete: () -> Unit,
    viewModel: StorageOnboardingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.selectCustom(context, uri)
        }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = tr("Welcome to Cemu"),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tr("Choose where to store your data"),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            StorageOptionCard(
                selected = uiState.selectedType == StorageType.DEFAULT,
                title = tr("Default location"),
                description = tr("Data is stored in the app's private folder. It will be deleted if you uninstall the app."),
                onClick = { viewModel.selectDefault() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            StorageOptionCard(
                selected = uiState.selectedType == StorageType.CUSTOM,
                title = tr("Custom folder"),
                description = uiState.customPath
                    ?: tr("Choose a folder to keep your data safe even if you uninstall the app."),
                onClick = { launcher.launch(null) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.confirm(context, onComplete) },
                enabled = uiState.selectedType != StorageType.NOT_SET && !uiState.isConfirming,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text(text = tr("Continue"))
            }
        }
    }
}

@Composable
private fun StorageOptionCard(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
