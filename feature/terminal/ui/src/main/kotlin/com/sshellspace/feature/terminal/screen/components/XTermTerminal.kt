package com.sshellspace.feature.terminal.screen.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pty4j.PtyProcessBuilder
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewFileReadType
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLFile
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun XTermTerminal(modifier: Modifier = Modifier) {
    val navigator = rememberWebViewNavigator()
    val bridge = rememberWebViewJsBridge(navigator)
    val state = rememberWebViewStateWithHTMLFile(
        fileName = "file:///resources/xterm/index.html",
        readType = WebViewFileReadType.COMPOSE_RESOURCE_FILES,
    )

    val process = remember {
        PtyProcessBuilder()
            .setCommand(arrayOf("/bin/bash", "--login"))
            .setEnvironment(HashMap(System.getenv()).apply { put("TERM", "xterm-256color") })
            .setInitialColumns(80)
            .setInitialRows(24)
            .start()
    }

    // Kotlin -> JS: register the handler xterm.js calls on keystrokes
    LaunchedEffect(bridge) {
        bridge.register(object : IJsMessageHandler {
            override fun methodName() = "ptyInput"
            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                process.outputStream.write(message.params.toByteArray(StandardCharsets.UTF_8))
                process.outputStream.flush()
                callback("")
            }
        })
    }

    // PTY -> JS: pump process output into the page via evaluateJavaScript
    LaunchedEffect(process) {
        val buf = ByteArray(4096)
        withContext(Dispatchers.IO) {
            while (process.isAlive) {
                val n = process.inputStream.read(buf)
                if (n <= 0) break
                val chunk = String(buf, 0, n, StandardCharsets.UTF_8)
                val escaped = chunk
                    .replace("\\", "\\\\")
                    .replace("`", "\\`")
                    .replace("$", "\\$")
                withContext(Dispatchers.Main) {
                    navigator.evaluateJavaScript("window.writeToTerminal(`$escaped`)") {}
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { process.destroy() }
    }

    WebView(
        state = state,
        navigator = navigator,
        webViewJsBridge = bridge,
        modifier = modifier.fillMaxSize(),
    )
}
