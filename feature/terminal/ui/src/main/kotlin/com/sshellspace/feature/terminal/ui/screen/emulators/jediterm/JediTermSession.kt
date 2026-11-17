package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.jediterm.core.Color as JediColor
import com.jediterm.core.compatibility.Point
import com.jediterm.core.input.MouseEvent
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.HyperlinkStyle
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TerminalOutputStream
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.emulator.mouse.MouseEventProcessingSettings
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.SelectionUtil
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.hyperlinks.LinkInfo
import com.jediterm.terminal.model.hyperlinks.LinkResult
import com.jediterm.terminal.model.hyperlinks.LinkResultItem
import com.sshellspace.feature.terminal.domain.printableRemoteText
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.browserUri
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.openInBrowser
import java.io.IOException
import java.net.URI
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val MAX_HISTORY_LINES = 10_000
private const val INITIAL_COLUMNS = 80
private const val INITIAL_ROWS = 24
private const val ESC = '\u001b'
private const val MAX_TITLE_CHARS = 120

/** A cell on the screen or in the scrollback: [y] counts lines from the top of the screen, negative in history. */
internal data class Cell(val x: Int, val y: Int)

/** Selected text, from where the drag started to where it is now, in either order. */
internal data class Selection(val anchor: Cell, val head: Cell) {
    val start: Cell get() = minOf(anchor, head, compareBy<Cell>({ it.y }, { it.x }))
    val end: Cell get() = maxOf(anchor, head, compareBy<Cell>({ it.y }, { it.x }))

    fun contains(x: Int, y: Int): Boolean {
        val (first, last) = start to end
        return when {
            y < first.y || y > last.y -> false
            first.y == last.y -> x >= first.x && x < last.x
            y == first.y -> x >= first.x
            y == last.y -> x < last.x
            else -> true
        }
    }
}

/**
 * One terminal: JediTerm's emulator and screen model, showing [process]. The emulator runs on a thread of its own,
 * reading the process as fast as it writes. The UI reads the screen through [textBuffer] while holding its lock.
 *
 * [onExit] gets the exit code once the program ends, unless [close] ended it.
 */
internal class JediTermSession(
    private val process: ShellProcess,
    private val startupCommand: String? = null,
    private val onExit: ((Int) -> Unit)? = null,
    private val openLink: (URI) -> Unit = ::openInBrowser,
) {
    /** The colors in use, which the view keeps up to date. The emulator reads them to answer color queries. */
    @Volatile
    var palette: TerminalPalette = TerminalPalette.Default

    /** Called with text the program copies (OSC 52). The view sets it, since copying needs its clipboard. */
    @Volatile
    var onCopy: ((String) -> Unit)? = null

    // Declared before the terminal, which reports changes while it is being built.
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits whenever the screen may look different. */
    val changes: Flow<Unit> = _changes

    private val styleState = StyleState().apply {
        setDefaultStyle(
            TextStyle(
                TerminalColor { palette.foreground.toJediColor() },
                TerminalColor { palette.background.toJediColor() },
            ),
        )
    }
    val textBuffer = TerminalTextBuffer(INITIAL_COLUMNS, INITIAL_ROWS, styleState, MAX_HISTORY_LINES)
    private val display = Display()
    private val terminal = JediTerminal(display, textBuffer, styleState)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val input = Channel<ByteArray>(Channel.UNLIMITED)
    private val resizes = Channel<TermSize>(Channel.CONFLATED)
    private var size = TermSize(INITIAL_COLUMNS, INITIAL_ROWS)

    @Volatile
    private var closed = false

    /** How many lines up into the scrollback the view is. 0 shows the screen. Only touched from the UI. */
    var scrollOffset by mutableIntStateOf(0)

    var selection by mutableStateOf<Selection?>(null)

    val cursorX: Int get() = display.cursorX

    /** The cursor's line on the screen, from 0. */
    val cursorY: Int get() = display.cursorY - 1
    val isCursorVisible: Boolean get() = display.showsCursor
    val cursorShape: CursorShape? get() = display.shape

    /** Whether the cursor blinks: unless the program asked for a steady one, as xterm's default cursor does. */
    val cursorBlinks: Boolean get() = display.shape?.isBlinking ?: true

    private val _title = MutableStateFlow<String?>(null)

    /** The title the program set, such as vim's file name, made safe to show. Null when it set none. */
    val title: StateFlow<String?> = _title

    /** Whether the program has asked for mouse events, as tmux and vim do with mouse support on. */
    val isMouseReporting: Boolean get() = display.mouseMode != MouseMode.MOUSE_REPORTING_NONE
    val isUsingAlternateBuffer: Boolean get() = textBuffer.isUsingAlternateBuffer

    init {
        terminal.setTerminalOutput(object : TerminalOutputStream {
            override fun sendBytes(response: ByteArray, userInput: Boolean) = send(response)
            override fun sendString(string: String, userInput: Boolean) = send(string.toByteArray(Charsets.UTF_8))
        })
        textBuffer.addModelListener { markChanged() }
        // OSC 8 links. Only web links are kept, for the same reason as clicked text: see handleTerminalLink.
        terminal.setUrlHyperlinkFilter { uri ->
            browserUri(uri)?.let { target -> LinkResult(LinkResultItem(0, uri.length, LinkInfo { openLink(target) })) }
        }
        scope.launch {
            try {
                for (bytes in input) process.write(bytes)
            } catch (_: IOException) {
                // The program has gone. The emulator thread notices too, when its read ends.
            }
        }
        scope.launch {
            for (newSize in resizes) {
                terminal.resize(newSize, RequestOrigin.User)
                markChanged()
                runCatching { process.resize(newSize.columns, newSize.rows) }
            }
        }
        thread(name = "JediTerm emulator", isDaemon = true) { runEmulator() }
    }

    /** Fits the terminal to [columns] by [rows] cells, telling the program. */
    fun resize(columns: Int, rows: Int) {
        val newSize = TermSize(max(columns, 2), max(rows, 1))
        if (newSize == size) return
        size = newSize
        // JediTerm moves lines between the screen and history on a resize, so a selection would point elsewhere.
        selection = null
        resizes.trySend(newSize)
    }

    /** Sends what the user typed, scrolling back down to the prompt. */
    fun type(bytes: ByteArray) {
        scrollOffset = 0
        selection = null
        send(bytes)
    }

    fun type(text: String) = type(text.toByteArray(Charsets.UTF_8))

    /** The escape sequence a special key sends in the current mode, such as an arrow key in vim. */
    fun codeForKey(keyCode: Int, modifiers: Int): ByteArray? = terminal.getCodeForKey(keyCode, modifiers)

    /**
     * Sends [text] as a paste. Line breaks become Enter, as typed. When the program asked for bracketed paste, as
     * shells and vim do, it's marked as a paste so a shell waits for Enter instead of running each line.
     */
    fun paste(text: String) {
        val lines = text.replace("\r\n", "\r").replace('\n', '\r')
        // Escapes inside a paste could end it early, and run whatever follows as commands.
        val body = if (display.bracketedPaste) "$ESC[200~${lines.replace(ESC.toString(), "")}$ESC[201~" else lines
        type(body)
    }

    /** Hands a mouse event at cell ([x], [y]) on the screen to the program. False if it didn't take it. */
    fun mouse(x: Int, y: Int, event: MouseEvent): Boolean = terminal.onMouseEvent(
        x,
        y,
        event,
        MouseEventProcessingSettings(
            isMouseReportingEnabled = true,
            isUsingAlternateBuffer = textBuffer.isUsingAlternateBuffer,
            isSimulateMouseScrollWithArrowKeysInAlternateScreen = true,
        ),
    )

    /** Scrolls the view [lines] further into the scrollback, or back towards the screen when negative. */
    fun scrollBy(lines: Int) {
        scrollOffset = (scrollOffset + lines).coerceIn(0, textBuffer.historyLinesCount)
    }

    /** The OSC 8 link on [cell], if the program printed one there. */
    fun linkAt(cell: Cell): LinkInfo? = locked {
        (textBuffer.getLine(cell.y).getStyleAt(cell.x) as? HyperlinkStyle)?.linkInfo
    }

    fun selectedText(): String? {
        val current = selection ?: return null
        return locked {
            SelectionUtil.getSelectionText(
                Point(current.start.x, current.start.y),
                Point(current.end.x, current.end.y),
                textBuffer,
            )
        }.takeIf { it.isNotEmpty() }
    }

    /** The word around [cell], as separated by spaces and punctuation, for a double click. */
    fun wordAt(cell: Cell): Selection = locked {
        val start = SelectionUtil.getPreviousSeparator(Point(cell.x, cell.y), textBuffer)
        val end = SelectionUtil.getNextSeparator(Point(cell.x, cell.y), textBuffer)
        Selection(Cell(start.x, start.y), Cell(end.x + 1, end.y))
    }

    fun <T> locked(block: () -> T): T {
        textBuffer.lock()
        try {
            return block()
        } finally {
            textBuffer.unlock()
        }
    }

    /** Ends the program and stops the emulator. */
    fun close() {
        if (closed) return
        closed = true
        input.close()
        resizes.close()
        CoroutineScope(Dispatchers.IO).launch { runCatching { process.close() } }
        scope.cancel()
    }

    private fun send(bytes: ByteArray) {
        input.trySend(bytes)
    }

    private fun markChanged() {
        _changes.tryEmit(Unit)
    }

    private fun runEmulator() {
        val emulator = JediEmulator(TtyBasedArrayDataStream(Connector()), terminal)
        try {
            while (!closed && emulator.hasNext()) emulator.next()
        } catch (_: IOException) {
            // The connection broke. Same as the program ending.
        } finally {
            markChanged()
            if (!closed) {
                CoroutineScope(Dispatchers.IO).launch {
                    val exitCode = process.waitForExit()
                    if (!closed) onExit?.invoke(exitCode)
                }
            }
        }
    }

    /** Feeds the program's output to the emulator, which reads on its own thread and so may block. */
    private inner class Connector : TtyConnector {
        private val osc52 = Osc52Filter { text -> onCopy?.invoke(text) }
        private var pending = ""
        private var position = 0
        private var startupSent = startupCommand == null

        override fun read(buf: CharArray, offset: Int, length: Int): Int {
            while (position >= pending.length) {
                val chunk = runBlocking { process.read() } ?: return -1
                pending = osc52.filter(chunk)
                position = 0
                if (!startupSent && chunk.isNotEmpty()) {
                    // Typed once the shell has printed something, so it isn't lost before the shell reads input.
                    startupSent = true
                    send("$startupCommand\r".toByteArray(Charsets.UTF_8))
                }
            }
            val count = min(length, pending.length - position)
            pending.toCharArray(buf, offset, position, position + count)
            position += count
            return count
        }

        override fun write(bytes: ByteArray) = send(bytes)
        override fun write(string: String) = send(string.toByteArray(Charsets.UTF_8))
        override fun isConnected(): Boolean = !closed
        override fun waitFor(): Int = runBlocking { process.waitForExit() }
        override fun ready(): Boolean = position < pending.length
        override fun getName(): String = "SSHellSpace"
        override fun close() = this@JediTermSession.close()
    }

    /** What JediTerm tells the screen. The view reads it back when it draws. */
    private inner class Display : TerminalDisplay {
        @Volatile var cursorX = 0

        @Volatile var cursorY = 1

        @Volatile var showsCursor = true

        @Volatile var shape: CursorShape? = null

        @Volatile var mouseMode = MouseMode.MOUSE_REPORTING_NONE

        @Volatile var bracketedPaste = false

        @Volatile private var title = ""

        override fun setCursor(x: Int, y: Int) {
            cursorX = x
            cursorY = y
            markChanged()
        }

        override fun setCursorShape(cursorShape: CursorShape?) {
            shape = cursorShape
            markChanged()
        }

        override fun beep() = Unit
        override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) = markChanged()

        override fun setCursorVisible(isCursorVisible: Boolean) {
            showsCursor = isCursorVisible
            markChanged()
        }

        override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) = markChanged()
        override fun getWindowTitle(): String = title

        override fun setWindowTitle(windowTitle: String) {
            title = windowTitle
            // A server sets this, so it can't bring control or direction characters into the tab strip.
            _title.value = printableRemoteText(windowTitle).trim().take(MAX_TITLE_CHARS).ifEmpty { null }
        }

        override fun getSelection(): TerminalSelection? =
            selection?.let { TerminalSelection(Point(it.start.x, it.start.y), Point(it.end.x, it.end.y)) }

        override fun terminalMouseModeSet(mouseMode: MouseMode) {
            this.mouseMode = mouseMode
        }

        override fun setMouseFormat(mouseFormat: MouseFormat) = Unit
        override fun ambiguousCharsAreDoubleWidth(): Boolean = false

        override fun setBracketedPasteMode(bracketedPasteModeEnabled: Boolean) {
            bracketedPaste = bracketedPasteModeEnabled
        }

        // Programs such as vim ask for these to tell whether the background is dark.
        override fun getWindowForeground(): JediColor = palette.foreground.toJediColor()
        override fun getWindowBackground(): JediColor = palette.background.toJediColor()
    }
}

private fun androidx.compose.ui.graphics.Color.toJediColor() = JediColor(toArgb(), true)
