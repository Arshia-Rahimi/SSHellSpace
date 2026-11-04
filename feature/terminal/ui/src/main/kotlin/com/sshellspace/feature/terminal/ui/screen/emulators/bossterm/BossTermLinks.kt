package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.hyperlinks.HyperlinkInfo
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import kotlin.concurrent.thread

private const val OPEN_TARGET_PATTERN = "osc:open-target"
private val BROWSER_SCHEMES = setOf("http", "https")

/** [url] as a URI when it is safe to give to a browser, else null. Parsing also refuses spaces and shell syntax. */
internal fun browserUri(url: String): URI? = try {
    URI(url).takeIf { it.scheme?.lowercase() in BROWSER_SCHEMES && !it.host.isNullOrEmpty() }
} catch (_: URISyntaxException) {
    null
}

/**
 * Decides what clicking a terminal link does, returning true when BossTerm shouldn't open it itself. Link text comes
 * from whatever is printed, including by a remote server, and BossTerm would hand it to `cmd /c start`, `open` or
 * `xdg-open`, which can run commands or launch local apps. So only http(s) opens, and never through a shell.
 *
 * The exception is a local command asking to open a file with `open`, which BossTerm authenticates with a token that
 * only local shells get.
 */
internal fun handleTerminalLink(link: HyperlinkInfo, browse: (URI) -> Unit = ::openInBrowser): Boolean {
    val uri = browserUri(link.url)
    if (uri != null) {
        browse(uri)
        return true
    }
    return link.patternId != OPEN_TARGET_PATTERN
}

private fun openInBrowser(uri: URI) {
    // Opening a browser can block for a while, and this is called from a click handler.
    thread(isDaemon = true) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
            } else {
                ProcessBuilder("xdg-open", uri.toASCIIString()).start()
            }
        } catch (_: IOException) {
            // Nothing to fall back on: the link just doesn't open, as when no browser is installed.
        } catch (_: UnsupportedOperationException) {
            // Same: this desktop environment can't open links.
        }
    }
}
