package com.sshellspace.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composeunstyled.Text
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsTabScreen() {
    val viewModel = koinViewModel<SettingsTabViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("settings tab")
    }
}
