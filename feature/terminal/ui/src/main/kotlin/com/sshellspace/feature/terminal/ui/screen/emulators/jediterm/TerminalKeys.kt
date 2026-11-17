package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import com.jediterm.core.input.InputEvent
import com.jediterm.core.input.KeyEvent as VK
import com.sshellspace.core.domain.models.OS
import com.sshellspace.core.domain.models.getOs

private const val ESC: Byte = 0x1b
private const val CTRL_SPACE: Byte = 0
private const val CTRL_CARET: Byte = 0x1e
private const val CTRL_UNDERSCORE: Byte = 0x1f
private const val CHAR_UNDEFINED = 0xFFFF
private const val DUPLICATE_WINDOW_NANOS = 150_000_000L

private val isMac = getOs() == OS.MacOS

/** What a key press in the terminal does. */
internal sealed interface KeyAction {
    data class Send(val bytes: ByteArray) : KeyAction

    /** Text typed with the key, which may also arrive from the input method. */
    data class Type(val text: String, val source: TypedTextFilter.Source) : KeyAction
    data object Copy : KeyAction
    data object Paste : KeyAction

    /** The key isn't the terminal's, such as an app shortcut, so it carries on to the window. */
    data object Pass : KeyAction

    /** The key does nothing, such as a modifier pressed alone. */
    data object Ignore : KeyAction
}

/** JediTerm's codes for the keys that send escape sequences rather than text. */
private val SpecialKeys: Map<Key, Int> = mapOf(
    Key.Enter to VK.VK_ENTER, Key.NumPadEnter to VK.VK_ENTER,
    Key.Backspace to VK.VK_BACK_SPACE, Key.Tab to VK.VK_TAB, Key.Escape to VK.VK_ESCAPE,
    Key.Insert to VK.VK_INSERT, Key.Delete to VK.VK_DELETE,
    Key.MoveHome to VK.VK_HOME, Key.MoveEnd to VK.VK_END, Key.PageUp to VK.VK_PAGE_UP, Key.PageDown to VK.VK_PAGE_DOWN,
    Key.DirectionUp to VK.VK_UP, Key.DirectionDown to VK.VK_DOWN,
    Key.DirectionLeft to VK.VK_LEFT, Key.DirectionRight to VK.VK_RIGHT,
    Key.F1 to VK.VK_F1, Key.F2 to VK.VK_F2, Key.F3 to VK.VK_F3, Key.F4 to VK.VK_F4,
    Key.F5 to VK.VK_F5, Key.F6 to VK.VK_F6, Key.F7 to VK.VK_F7, Key.F8 to VK.VK_F8,
    Key.F9 to VK.VK_F9, Key.F10 to VK.VK_F10, Key.F11 to VK.VK_F11, Key.F12 to VK.VK_F12,
)

private val ModifierKeys = setOf(
    Key.ShiftLeft, Key.ShiftRight, Key.CtrlLeft, Key.CtrlRight,
    Key.AltLeft, Key.AltRight, Key.MetaLeft, Key.MetaRight,
)

/**
 * The control character each Ctrl+key sends: Ctrl+A is 0x01 through Ctrl+Z, then Ctrl+[ is Esc. Worked out from the
 * key rather than the typed text, because on macOS Tao gives Ctrl+letter the letter itself.
 */
private val ControlCodes: Map<Key, Byte> = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
    Key.LeftBracket, Key.Backslash, Key.RightBracket,
).mapIndexed { index, key -> key to (index + 1).toByte() }.toMap() + mapOf(
    Key.Spacebar to CTRL_SPACE, Key.Two to CTRL_SPACE,
    Key.Six to CTRL_CARET,
    Key.Minus to CTRL_UNDERSCORE, Key.Slash to CTRL_UNDERSCORE,
)

/**
 * What [event] does in a terminal, with [codeForKey] giving the sequence for special keys in the program's current
 * mode. Cmd is the app's on macOS, so Cmd+C and Cmd+V copy and paste and other Cmd keys pass to the app's shortcuts.
 * Elsewhere Ctrl+Shift+C and Ctrl+Shift+V do, since Ctrl+C has to interrupt.
 */
internal fun keyAction(event: KeyEvent, codeForKey: (Int, Int) -> ByteArray?): KeyAction {
    if (event.type == KeyEventType.Unknown) return typedAction(event)
    if (event.type != KeyEventType.KeyDown) return KeyAction.Ignore
    if (event.key in ModifierKeys) return KeyAction.Ignore
    clipboardAction(event)?.let { return it }
    if (event.isMetaPressed) return KeyAction.Pass

    SpecialKeys[event.key]?.let { code ->
        return codeForKey(code, modifiersOf(event))?.let { KeyAction.Send(it) } ?: KeyAction.Ignore
    }
    if (event.isCtrlPressed) {
        val control = ControlCodes[event.key] ?: return KeyAction.Pass
        return KeyAction.Send(if (event.isAltPressed) byteArrayOf(ESC, control) else byteArrayOf(control))
    }
    val text = printableText(event) ?: return KeyAction.Ignore
    // Option on a Mac types accented letters and symbols, so it only works as Meta elsewhere.
    if (event.isAltPressed && !isMac) return KeyAction.Send(byteArrayOf(ESC) + text.toByteArray(Charsets.UTF_8))
    // A dead key has no text yet; what it combines into arrives as a key-typed event or from the input method.
    return KeyAction.Type(text, TypedTextFilter.Source.KeyDown)
}

/**
 * A key-typed event: the text a key, a dead key sequence or the input method produced. Not while Ctrl, Cmd or (off
 * macOS) Alt is held, since the key press already sent what those mean.
 */
private fun typedAction(event: KeyEvent): KeyAction {
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed && !isMac) return KeyAction.Ignore
    return printableText(event)?.let { KeyAction.Type(it, TypedTextFilter.Source.KeyTyped) } ?: KeyAction.Ignore
}

private fun printableText(event: KeyEvent): String? {
    val codePoint = event.utf16CodePoint
    if (codePoint <= 0 || Character.isISOControl(codePoint) || codePoint == CHAR_UNDEFINED) return null
    return String(Character.toChars(codePoint))
}

/**
 * Typed text can reach the terminal up to three times under Tao: with the key press, as a key-typed event, and from
 * the input method while the terminal has an input session. Text that arrives one way shortly after the same text
 * arrived another way has already been sent.
 */
internal class TypedTextFilter(private val clock: () -> Long = System::nanoTime) {
    enum class Source { KeyDown, KeyTyped, InputMethod }

    private class Arrival(val text: String, val time: Long, val seenBy: MutableSet<Source>)

    private val recent = ArrayDeque<Arrival>()

    /** Whether [text] from [source] should be sent. */
    fun accept(source: Source, text: String): Boolean {
        val now = clock()
        while (recent.isNotEmpty() && now - recent.first().time > DUPLICATE_WINDOW_NANOS) recent.removeFirst()
        val earlier = recent.firstOrNull { it.text == text && source !in it.seenBy }
        if (earlier != null) {
            earlier.seenBy += source
            return false
        }
        recent.addLast(Arrival(text, now, mutableSetOf(source)))
        return true
    }
}

private fun clipboardAction(event: KeyEvent): KeyAction? {
    val shortcut = if (isMac) {
        event.isMetaPressed && !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed
    } else {
        event.isCtrlPressed && event.isShiftPressed && !event.isAltPressed && !event.isMetaPressed
    }
    return when {
        !shortcut -> null
        event.key == Key.C -> KeyAction.Copy
        event.key == Key.V -> KeyAction.Paste
        else -> null
    }
}

/** The modifiers in JediTerm's form. Meta is left out: it's the app's, not the program's. */
private fun modifiersOf(event: KeyEvent): Int {
    var modifiers = 0
    if (event.isShiftPressed) modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK
    if (event.isCtrlPressed) modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
    if (event.isAltPressed) modifiers = modifiers or InputEvent.ALT_DOWN_MASK
    return modifiers
}
