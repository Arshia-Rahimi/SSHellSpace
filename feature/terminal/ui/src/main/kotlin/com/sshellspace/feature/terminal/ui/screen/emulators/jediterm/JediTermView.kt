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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FontSize = 13.sp
private const val CURSOR_BLINK_MILLIS = 530L
private const val DEL: Byte = 0x7f

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
    // As on X11, the middle button pastes what was last selected, here falling back to the clipboard.
    val pasteSelection = { session.selectedText()?.let(session::paste) ?: paste() }

    val typedText = remember(session) { TypedTextFilter() }
    val textInput = remember(session, fonts) { SessionTextInput(session, fonts, typedText) }

    // A hidden tab's terminal mustn't take the keyboard from the one on show.
    val focusRequester = remember { FocusRequester() }
    val active = LocalTabActive.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) focusRequester.requestFocus()
    }

    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(session, focused) {
        cursorOn = true
        if (!focused) return@LaunchedEffect
        while (true) {
            delay(CURSOR_BLINK_MILLIS)
            cursorOn = !session.cursorBlinks || !cursorOn
        }
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
            .terminalTextInput(textInput)
            .focusable()
            .onPreviewKeyEvent { event ->
                when (val action = keyAction(event, session::codeForKey)) {
                    is KeyAction.Send -> session.type(action.bytes)
                    is KeyAction.Type -> if (typedText.accept(action.source, action.text)) {
                        session.type(action.text)
                    }
                    KeyAction.Copy -> copy()
                    KeyAction.Paste -> paste()
                    KeyAction.Ignore -> Unit
                    KeyAction.Pass -> return@onPreviewKeyEvent false
                }
                cursorOn = true
                true
            }
            .terminalPointer(
                session,
                fonts,
                keyboardModifiers = { modifiers },
                onPress = { focusRequester.requestFocus() },
                onMiddleClick = { pasteSelection() },
            ),
    ) {
        // Reading the frame count is what redraws on new output.
        @Suppress("UNUSED_EXPRESSION")
        frame
        renderer.draw(this, session, palette, focused, cursorOn, textInput.composing)
    }
}

/** Sends what the input method commits, and keeps what it is composing for the screen to show. */
private class SessionTextInput(
    private val session: JediTermSession,
    private val fonts: TerminalFonts,
    private val typedText: TypedTextFilter,
) : TerminalTextInput {
    override var composing by mutableStateOf("")

    override fun commit(text: String) {
        if (typedText.accept(TypedTextFilter.Source.InputMethod, text)) session.type(text)
    }

    override fun deleteBefore(codePoints: Int) = session.type(ByteArray(codePoints) { DEL })

    override fun cursorRect() = Rect(
        Offset(session.cursorX * fonts.cellWidth, (session.cursorY + session.scrollOffset) * fonts.cellHeight),
        Size(fonts.cellWidth, fonts.cellHeight),
    )
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
