package com.sshellspace.core.ui.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledTextField
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.textSecondary

private const val SELECTION_ALPHA = 0.3f

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    focusRequester: FocusRequester? = null,
    /** Called with the current text when Enter is pressed in a single-line field. */
    onSubmit: ((String) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val state = rememberTextFieldState(initialText = value)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(value) {
        if (state.text.toString() != value) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(state.text) {
        val current = state.text.toString()
        if (current != value) {
            onValueChange(current)
        }
    }

    val passwordTransformation = remember(isPassword) {
        if (isPassword) PasswordOutputTransformation else null
    }
    val primaryColor = Theme[colors][primary]

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (label != null) {
            Text(
                text = label,
                color = Theme[colors][textSecondary],
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        UnstyledTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onPreviewKeyEvent { event ->
                    val submit = onSubmit?.takeIf { singleLine } ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        submit(state.text.toString())
                        true
                    } else {
                        false
                    }
                },
            enabled = enabled,
            cursorBrush = SolidColor(primaryColor),
            selectionColors = TextSelectionColors(
                handleColor = primaryColor,
                backgroundColor = primaryColor.copy(alpha = SELECTION_ALPHA),
            ),
            textColor = Theme[colors][textPrimary],
            lineLimits = if (singleLine) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine(minHeightInLines = minLines)
            },
            outputTransformation = passwordTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interactionSource,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fieldContainer(focused = focused, enabled = enabled, isError = isError)
                    .padding(contentPadding),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextInput(
                    modifier = Modifier.weight(1f),
                    placeholder = placeholder?.let {
                        { Text(it, color = Theme[colors][textSecondary], maxLines = 1) }
                    },
                )
                trailing?.invoke()
            }
        }
    }
}

private object PasswordOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length > 0) {
            replace(0, length, "•".repeat(length))
        }
    }
}
