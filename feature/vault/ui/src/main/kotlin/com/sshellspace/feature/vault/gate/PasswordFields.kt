package com.sshellspace.feature.vault.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.PasswordStrength
import com.sshellspace.core.domain.models.RECOMMENDED_VAULT_PASSWORD_LENGTH
import com.sshellspace.core.domain.models.vaultPasswordStrength
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.success
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.warning

/**
 * Choosing a password, including choosing none. Weak ones get a warning and are still allowed. Only a mismatch stops the
 * form, since that's a typo that would lock the user out rather than a choice they made.
 */
@Composable
internal fun NewPasswordFields(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
    label: String = "Password",
) {
    val mismatch = confirmation.isNotEmpty() && confirmation != password

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PasswordField(
            value = password,
            onValueChange = onPasswordChange,
            label = "$label (optional)",
            enabled = enabled,
            onSubmit = { if (password.isEmpty()) onSubmit() },
        )
        PasswordStrengthMeter(password)
    }
    if (password.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PasswordField(
                value = confirmation,
                onValueChange = onConfirmationChange,
                label = "Confirm ${label.lowercase()}",
                enabled = enabled,
                isError = mismatch,
                onSubmit = { onSubmit() },
            )
            if (mismatch) Text("The passwords don't match.", color = Theme[colors][error])
        }
    }
}

@Composable
private fun PasswordStrengthMeter(password: String) {
    val strength = vaultPasswordStrength(password)
    val (filled, color, message) = when {
        password.isEmpty() -> Triple(
            0,
            Theme[colors][warning],
            "Without a password, anyone who can get to this computer or a copy of the vault file can read everything " +
                "in it, including saved passwords and keys.",
        )
        password.length < RECOMMENDED_VAULT_PASSWORD_LENGTH -> Triple(
            1,
            Theme[colors][warning],
            "Short passwords are easy to guess. $RECOMMENDED_VAULT_PASSWORD_LENGTH or more characters is safer.",
        )
        strength == PasswordStrength.Weak -> Triple(1, Theme[colors][warning], "This password is easy to guess.")
        strength == PasswordStrength.Fair -> Triple(2, Theme[colors][textSecondary], "Fair. Longer is stronger.")
        else -> Triple(3, Theme[colors][success], "Strong.")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Box(
                Modifier.weight(1f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) color else Theme[colors][border]),
            )
        }
    }
    Text(message, color = if (filled == 3) Theme[colors][textSecondary] else color)
}

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean = false,
    focusRequester: FocusRequester? = null,
    onSubmit: ((String) -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isPassword = !visible,
        enabled = enabled,
        isError = isError,
        focusRequester = focusRequester,
        onSubmit = onSubmit,
        // The icon is taller than a line of text, so this keeps the field as tall as the plain ones beside it.
        contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        trailing = {
            IconButton(
                onClick = { visible = !visible },
                imageVector = if (visible) Icons.EyeClosed else Icons.EyeOpen,
                contentDescription = if (visible) "Hide password" else "Show password",
                tint = Theme[colors][textSecondary],
            )
        },
    )
}
