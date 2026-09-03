package com.sshellspace.feature.vault.sshkeys

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.sshellspace.core.ui.designsystem.components.Button
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SshKeysPage() {
    val viewModel = koinViewModel<SshKeysPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = uiState.keys,
                key = { it.id },
            ) {
                Button(
                    onClick = {},
                ) {
                    Row {
                        Text(it.name)
                        Text(it.algorithm.name)
                    }
                }
            }
        }
    }
}
