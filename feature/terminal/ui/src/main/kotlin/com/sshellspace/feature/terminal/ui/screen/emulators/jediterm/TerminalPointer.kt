package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.jediterm.core.input.MouseEvent
import com.jediterm.core.input.MouseWheelEvent
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import com.sshellspace.core.domain.models.OS
import com.sshellspace.core.domain.models.getOs
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.browserUri
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.openInBrowser
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SCROLLBACK_LINES_PER_STEP = 3
private const val MULTI_CLICK_MILLIS = 400L
private const val AUTO_SCROLL_MILLIS = 50L
private val UrlPattern = Regex("""https?://[^\s<>"'`]+""")
private val isMac = getOs() == OS.MacOS

/**
 * Where the pointer is: the cell under it, and the cell boundary nearest it, which is where a selection starts.
 * [edge] is -1 above the terminal, 1 below it and 0 over it.
 */
private data class PointerCell(val column: Int, val row: Int, val boundary: Int, val edge: Int = 0)

/**
 * The terminal's mouse handling. While the program asks for the mouse, as tmux and vim do with mouse support on,
 * clicks, drags and the wheel go to it, and holding Shift selects text instead. Otherwise dragging selects (a double
 * click selects a word, a triple click a line, and dragging past the top or bottom scrolls), the wheel scrolls back
 * through the output, the middle button calls [onMiddleClick], and Cmd-click (Ctrl-click off macOS) opens a web link.
 */
internal fun Modifier.terminalPointer(
    session: JediTermSession,
    fonts: TerminalFonts,
    keyboardModifiers: () -> PointerKeyboardModifiers,
    onPress: () -> Unit,
    onMiddleClick: () -> Unit,
): Modifier = pointerInput(session, fonts) {
    val pointer = PointerState(session, onMiddleClick)
    awaitPointerEventScope {
        while (true) {
            val columns = (size.width / fonts.cellWidth).toInt().coerceAtLeast(1)
            val rows = (size.height / fonts.cellHeight).toInt().coerceAtLeast(1)
            // A selection dragged past the top or bottom keeps scrolling while the pointer stays there.
            val event = if (pointer.isDraggingPastEdge) {
                withTimeoutOrNull(AUTO_SCROLL_MILLIS) { awaitPointerEvent() } ?: run {
                    pointer.autoScroll(rows)
                    null
                }
            } else {
                awaitPointerEvent()
            }
            val change = event?.changes?.firstOrNull() ?: continue
            val cell = cellAt(change.position, fonts, columns, rows)
            val keys = keyboardModifiers()
            when (event.type) {
                PointerEventType.Press -> {
                    onPress()
                    pointer.press(event, cell, keys, columns)
                }
                PointerEventType.Move -> pointer.move(cell, keys)
                PointerEventType.Release -> pointer.release(cell, keys)
                PointerEventType.Scroll -> pointer.scroll(change.scrollDelta.y, cell, keys)
                else -> continue
            }
            change.consume()
        }
    }
}

private fun cellAt(position: Offset, fonts: TerminalFonts, columns: Int, rows: Int) = PointerCell(
    column = (position.x / fonts.cellWidth).toInt().coerceIn(0, columns - 1),
    row = (position.y / fonts.cellHeight).toInt().coerceIn(0, rows - 1),
    boundary = (position.x / fonts.cellWidth).roundToInt().coerceIn(0, columns),
    edge = when {
        position.y < 0 -> -1
        position.y >= rows * fonts.cellHeight -> 1
        else -> 0
    },
)

private class PointerState(private val session: JediTermSession, private val onMiddleClick: () -> Unit) {
    /** The button whose press went to the program, so its drags and release go there too. */
    private var reportedButton = MouseButtonCodes.NONE
    private var anchor: Cell? = null
    private var clicks = 0
    private var lastClickTime = 0L
    private var lastClickCell: PointerCell? = null
    private var scrollRemainder = 0f
    private var lastDragCell: PointerCell? = null

    val isDraggingPastEdge: Boolean get() = anchor != null && (lastDragCell?.edge ?: 0) != 0

    /** Scrolls a line towards the edge the selection was dragged past, and extends the selection with it. */
    fun autoScroll(rows: Int) {
        val selectionAnchor = anchor ?: return
        val cell = lastDragCell ?: return
        session.scrollBy(-cell.edge)
        val row = if (cell.edge < 0) 0 else rows - 1
        session.selection = Selection(selectionAnchor, Cell(cell.boundary, row - session.scrollOffset))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun press(event: PointerEvent, cell: PointerCell, keys: PointerKeyboardModifiers, columns: Int) {
        val button = buttonCode(event.button)
        if (isReporting(keys) && button != MouseButtonCodes.NONE &&
            session.mouse(cell.column, cell.row, MouseEvent(MouseEvent.Type.PRESSED, button, flagsOf(keys)))
        ) {
            reportedButton = button
            return
        }
        if (event.button == PointerButton.Tertiary) {
            onMiddleClick()
            return
        }
        if (event.button != PointerButton.Primary) return
        if ((if (isMac) keys.isMetaPressed else keys.isCtrlPressed) && openLinkAt(cell)) return

        val now = System.currentTimeMillis()
        clicks = if (now - lastClickTime < MULTI_CLICK_MILLIS && cell.copy(boundary = 0) == lastClickCell) clicks + 1 else 1
        lastClickTime = now
        lastClickCell = cell.copy(boundary = 0)
        val line = cell.row - session.scrollOffset
        anchor = Cell(cell.boundary, line).takeIf { clicks == 1 }
        session.selection = when (clicks) {
            1 -> null
            2 -> session.wordAt(Cell(cell.column, line))
            else -> Selection(Cell(0, line), Cell(columns, line))
        }
    }

    fun move(cell: PointerCell, keys: PointerKeyboardModifiers) {
        val selectionAnchor = anchor
        when {
            reportedButton != MouseButtonCodes.NONE -> session.mouse(
                cell.column,
                cell.row,
                MouseEvent(MouseEvent.Type.DRAGGED, reportedButton, flagsOf(keys)),
            )
            selectionAnchor != null -> {
                lastDragCell = cell
                session.selection = Selection(selectionAnchor, Cell(cell.boundary, cell.row - session.scrollOffset))
            }
            isReporting(keys) -> session.mouse(
                cell.column,
                cell.row,
                MouseEvent(MouseEvent.Type.MOVED, MouseButtonCodes.NONE, flagsOf(keys)),
            )
        }
    }

    fun release(cell: PointerCell, keys: PointerKeyboardModifiers) {
        if (reportedButton != MouseButtonCodes.NONE) {
            session.mouse(cell.column, cell.row, MouseEvent(MouseEvent.Type.RELEASED, reportedButton, flagsOf(keys)))
            reportedButton = MouseButtonCodes.NONE
        }
        anchor = null
        lastDragCell = null
    }

    /**
     * Scrolls by [delta] wheel steps, down when positive. A trackpad sends fractions of a step, which add up. In a
     * full-screen program the wheel goes to the program, as arrow keys when it hasn't asked for the mouse.
     */
    fun scroll(delta: Float, cell: PointerCell, keys: PointerKeyboardModifiers) {
        val toProgram = isReporting(keys) || session.isUsingAlternateBuffer
        scrollRemainder += if (toProgram) delta else delta * SCROLLBACK_LINES_PER_STEP
        val steps = scrollRemainder.toInt()
        if (steps == 0) return
        scrollRemainder -= steps
        if (toProgram) {
            // JediTerm's SCROLLUP moves the text up, which is scrolling down.
            val button = if (steps > 0) MouseButtonCodes.SCROLLUP else MouseButtonCodes.SCROLLDOWN
            repeat(abs(steps)) { session.mouse(cell.column, cell.row, MouseWheelEvent(button, flagsOf(keys), 1)) }
        } else {
            session.scrollBy(-steps)
        }
    }

    private fun isReporting(keys: PointerKeyboardModifiers) = session.isMouseReporting && !keys.isShiftPressed

    private fun openLinkAt(cell: PointerCell): Boolean {
        val line = cell.row - session.scrollOffset
        session.linkAt(Cell(cell.column, line))?.let { link ->
            link.navigate()
            return true
        }
        val text = session.locked { session.textBuffer.getLine(line).text }
        val url = UrlPattern.findAll(text).firstOrNull { cell.column in it.range }?.value?.trimEnd('.', ',', ')', ';')
        val uri = url?.let(::browserUri) ?: return false
        openInBrowser(uri)
        return true
    }
}

private fun buttonCode(button: PointerButton?): Int = when (button) {
    PointerButton.Primary -> MouseButtonCodes.LEFT
    PointerButton.Tertiary -> MouseButtonCodes.MIDDLE
    PointerButton.Secondary -> MouseButtonCodes.RIGHT
    else -> MouseButtonCodes.NONE
}

private fun flagsOf(keys: PointerKeyboardModifiers): Int {
    var flags = 0
    if (keys.isShiftPressed) flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG
    if (keys.isAltPressed || keys.isMetaPressed) flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG
    if (keys.isCtrlPressed) flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG
    return flags
}
