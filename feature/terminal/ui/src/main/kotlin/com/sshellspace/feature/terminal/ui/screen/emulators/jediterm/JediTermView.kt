package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.unit.sp
import com.sshellspace.core.ui.tabs.LocalTabActive
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.terminalColors
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import kotlinx.coroutines.launch

private val FontSize = 13.sp

/** Shows [session] and sends it what the user types, clicks and scrolls. */
@Composable
internal fun JediTermView(session: JediTermSession, modifier: Modifier = Modifier) {
    val colors by terminalColors()
    val palette = remember(colors) { TerminalPalette.of(colors) }
    session.palette = palette

    val density = LocalDensity.current
    val fonts = remember(density) { TerminalFonts(with(density) { FontSize.toPx() }) }
    val renderer = remember(fonts) { TerminalRenderer(fonts) }

    // Output can arrive far faster than the screen refreshes, so the screen redraws at most once a frame.
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(session) {
        session.changes.collect {
            frame++
            withFrameNanos { }
        }
    }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    DisposableEffect(session, clipboard) {
        session.onCopy = { text -> scope.launch { clipboard.setText(text) } }
        onDispose { session.onCopy = null }
    }
    val copy = { session.selectedText()?.let { text -> scope.launch { clipboard.setText(text) } } }
    val paste = { scope.launch { clipboard.readText()?.let(session::paste) } }

    // A hidden tab's terminal mustn't take the keyboard from the one on show.
    val focusRequester = remember { FocusRequester() }
    val active = LocalTabActive.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) focusRequester.requestFocus()
    }

    // Tao leaves modifiers off pointer events, so they're read from the window.
    val windowInfo = LocalWindowInfo.current
    val modifiers by rememberUpdatedState(windowInfo.keyboardModifiers)

    Canvas(
        modifier
            .onSizeChanged { size ->
                session.resize((size.width / fonts.cellWidth).toInt(), (size.height / fonts.cellHeight).toInt())
            }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                when (val action = keyAction(event, session::codeForKey)) {
                    is KeyAction.Send -> session.type(action.bytes)
                    KeyAction.Copy -> copy()
                    KeyAction.Paste -> paste()
                    KeyAction.Ignore -> Unit
                    KeyAction.Pass -> return@onPreviewKeyEvent false
                }
                true
            }
            .terminalPointer(session, fonts, { modifiers }, onPress = { focusRequester.requestFocus() }),
    ) {
        // Reading the frame count is what redraws on new output.
        @Suppress("UNUSED_EXPRESSION")
        frame
        renderer.draw(this, session, palette, focused)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun Clipboard.setText(text: String) = setClipEntry(ClipEntry(StringSelection(text)))

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun Clipboard.readText(): String? {
    val transferable = getClipEntry()?.asAwtTransferable ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    return try {
        transferable.getTransferData(DataFlavor.stringFlavor) as? String
    } catch (_: UnsupportedFlavorException) {
        null
    } catch (_: IOException) {
        null
    }
}
