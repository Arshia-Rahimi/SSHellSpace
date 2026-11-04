package com.sshellspace.feature.terminal.domain

import com.sshellspace.core.domain.models.SshConnection
import kotlin.uuid.Uuid

interface SshConnectionService {
    /**
     * Connects to the saved connection [connectionId], going through its jump hosts, and authenticates every hop.
     * Anything that needs the user (unknown host keys, missing passwords, 2FA codes) goes through [prompter].
     */
    suspend fun connect(connectionId: Uuid, prompter: SshPrompter): SshConnectResult
}

/** Questions the connection flow can ask the user. A null answer means the user cancelled. */
interface SshPrompter {
    suspend fun confirmHostKey(prompt: HostKeyPrompt): Boolean
    suspend fun askSecret(prompt: SecretPrompt): String?
    suspend fun answerChallenge(challenge: AuthChallenge): List<String>?
}

data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    /** Set when known_hosts has a different key for this host, which can mean a man-in-the-middle attack. */
    val expectedFingerprint: String? = null,
) {
    val isChangedKey: Boolean get() = expectedFingerprint != null
}

data class SecretPrompt(
    val title: String,
    val message: String,
    val label: String,
    /** Set when an earlier answer was rejected. */
    val error: String? = null,
    /** The wording was written by the server, so the user shouldn't take it as coming from this app. */
    val fromServer: Boolean = false,
)

/** A keyboard-interactive round from the server, e.g. a one-time code. */
data class AuthChallenge(
    val title: String,
    val instruction: String,
    val prompts: List<ChallengePrompt>,
)

data class ChallengePrompt(
    val text: String,
    /** False for secrets that shouldn't be shown while typing. */
    val echo: Boolean,
)

sealed interface SshConnectResult {
    data class Success(
        val session: ShellSession.Ssh,
        val connection: SshConnection,
    ) : SshConnectResult

    data object Cancelled : SshConnectResult
    data class Failed(val message: String) : SshConnectResult
}
