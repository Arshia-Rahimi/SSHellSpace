package com.sshellspace.feature.terminal.domain.models

import com.sshellspace.core.domain.models.TabParams

data class TerminalParams(
    val shell: Shell,
): TabParams
