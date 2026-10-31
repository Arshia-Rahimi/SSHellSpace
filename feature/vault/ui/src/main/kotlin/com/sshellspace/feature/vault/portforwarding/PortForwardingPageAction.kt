package com.sshellspace.feature.vault.portforwarding

import com.sshellspace.core.domain.models.PortForward

sealed interface PortForwardingPageAction {
    data object OpenCreate : PortForwardingPageAction
    data class Select(val forward: PortForward) : PortForwardingPageAction
    data object CloseSidebar : PortForwardingPageAction
    data class EditForm(val update: (PortForwardForm) -> PortForwardForm) : PortForwardingPageAction

    /** Saves the open form. A running forward restarts with the new settings. */
    data object Save : PortForwardingPageAction

    /** Saves the open form, then starts the forward. */
    data object SaveAndStart : PortForwardingPageAction
    data object RequestDelete : PortForwardingPageAction

    data class Start(val forward: PortForward) : PortForwardingPageAction

    /** Stops a running forward, cancels one that is starting, or clears a failure. */
    data class Stop(val forward: PortForward) : PortForwardingPageAction

    data class AnswerHostKey(val trust: Boolean) : PortForwardingPageAction
    data class SubmitSecret(val value: String) : PortForwardingPageAction
    data class SubmitChallenge(val answers: List<String>) : PortForwardingPageAction
    data object CancelLogin : PortForwardingPageAction
}
