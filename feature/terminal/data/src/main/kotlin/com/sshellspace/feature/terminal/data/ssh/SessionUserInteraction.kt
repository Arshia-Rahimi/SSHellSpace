package com.sshellspace.feature.terminal.data.ssh

import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.ChallengePrompt
import com.sshellspace.feature.terminal.domain.SecretPrompt
import org.apache.sshd.client.auth.keyboard.UserInteraction
import org.apache.sshd.client.session.ClientSession

/** Answers MINA's password and keyboard-interactive callbacks by asking the user. */
internal class SessionUserInteraction(
    private val context: ConnectionContext,
) : UserInteraction {

    /** Called once the saved password (if any) is used up or rejected. */
    override fun resolveAuthPasswordAttempt(session: ClientSession): String? {
        if (!context.allowsPasswordPrompts || context.isCancelled) return null
        val attempt = context.passwordAttempts.incrementAndGet()
        if (attempt > MAX_PASSWORD_ATTEMPTS) return null

        val rejected = attempt > 1 || context.savedPassword != null
        return askSecret(
            SecretPrompt(
                title = "Password",
                message = "Enter the password for ${context.address}.",
                label = "Password",
                error = "Wrong password, try again.".takeIf { rejected },
            ),
        )
    }

    override fun interactive(
        session: ClientSession,
        name: String?,
        instruction: String?,
        lang: String?,
        prompt: Array<String>?,
        echo: BooleanArray?,
    ): Array<String>? {
        if (!context.allowsChallenges || context.isCancelled) return null
        val prompts = prompt.orEmpty().mapIndexed { i, text ->
            ChallengePrompt(text.fromServer(MAX_LABEL_LENGTH), echo?.getOrNull(i) ?: false)
        }
        val serverName = name?.fromServer(MAX_LABEL_LENGTH)
        val serverInstruction = instruction?.fromServer(MAX_MESSAGE_LENGTH)
        // Info-only rounds carry no questions but still need an (empty) reply.
        if (prompts.isEmpty()) return emptyArray()

        // A lone hidden prompt is almost always the account password, so show it like one.
        val single = prompts.singleOrNull()
        if (single != null && !single.echo) {
            return askSecret(
                SecretPrompt(
                    title = serverName?.takeIf { it.isNotBlank() } ?: "Verification",
                    message = serverInstruction?.takeIf { it.isNotBlank() } ?: "The server for ${context.address} asks:",
                    label = single.text.trim().removeSuffix(":"),
                    fromServer = true,
                ),
            )?.let { arrayOf(it) }
        }

        val challenge = AuthChallenge(
            title = serverName?.takeIf { it.isNotBlank() } ?: "Verification for ${context.address}",
            instruction = serverInstruction.orEmpty(),
            prompts = prompts,
        )
        return context.ask { answerChallenge(challenge) }
            ?.toTypedArray()
            ?: null.also { context.cancel() }
    }

    override fun getUpdatedPassword(session: ClientSession, prompt: String?, lang: String?): String? =
        askSecret(
            SecretPrompt(
                title = "Password change required",
                message = prompt?.fromServer(MAX_MESSAGE_LENGTH)?.takeIf { it.isNotBlank() }
                    ?: "The server asks for a new password for ${context.address}.",
                label = "New password",
                fromServer = true,
            ),
        )

    private fun askSecret(prompt: SecretPrompt): String? =
        context.ask { askSecret(prompt) } ?: null.also { context.cancel() }

    private companion object {
        const val MAX_PASSWORD_ATTEMPTS = 3
        const val MAX_LABEL_LENGTH = 80
        const val MAX_MESSAGE_LENGTH = 500
    }
}

/**
 * Makes server-supplied prompt text safe to show: a server shouldn't be able to reorder what the user reads with bidi
 * controls, or push the real buttons out of sight with endless text.
 */
internal fun String.fromServer(maxLength: Int): String =
    filterNot { (it.isISOControl() && it != '\n') || it in '\u200E'..'\u200F' || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }
        .take(maxLength)
