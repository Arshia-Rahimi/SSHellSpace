package com.sshellspace.core.ui.designsystem.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun StringResource.asString(): String = stringResource(this)
