package com.sshellspace.feature.terminal.screen.components

import com.pty4j.PtyProcessBuilder

@Composable
fun XTermTerminal(modifier: Modifier = Modifier) {
    val navigator = rememberWebViewNavigator()
    val bridge = rememberWebViewJsBridge(navigator)
    val state = rememberWebViewStateWithHTMLFile(
        fileName = "files/xterm/index.html",
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
            while (isActive && process.isAlive) {
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
