package com.github.arshia_rahimi.core.ui.common_compose

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun StringResource.asString(): String = stringResource(this)
