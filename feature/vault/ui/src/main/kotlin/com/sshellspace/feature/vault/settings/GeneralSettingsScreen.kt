package com.sshellspace.feature.vault.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import org.koin.compose.viewmodel.koinViewModel

/** The general settings on their own, opened from the unlock screen while no vault is unlocked. */
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Its own owner, so leaving the screen also ends a shortcut recording and the paused shortcuts with it.
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val viewModel = koinViewModel<GeneralSettingsViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsColumn(modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    imageVector = Icons.Back,
                    contentDescription = "Back to unlocking",
                    modifier = Modifier.size(28.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("General settings", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Shared by every vault on this computer. Each vault's own settings are in its Settings page.",
                        color = Theme[colors][textSecondary],
                    )
                }
            }
            GeneralSettingsSections(uiState, viewModel::execute)
        }
    }
}
