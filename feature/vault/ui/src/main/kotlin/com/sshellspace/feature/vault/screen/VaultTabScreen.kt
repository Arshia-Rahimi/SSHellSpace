package com.sshellspace.feature.vault.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composeunstyled.Text
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VaultTabScreen() {
    val viewModel = koinViewModel<VaultTabViewModel>()
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("vault tab")
    }
}
