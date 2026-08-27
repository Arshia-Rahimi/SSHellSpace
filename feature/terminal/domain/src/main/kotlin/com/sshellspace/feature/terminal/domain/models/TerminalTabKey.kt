package com.sshellspace.feature.terminal.domain.models

import com.sshellspace.core.domain.models.TabKey

data class TerminalTabKey(
    val shellName: String,
): TabKey
