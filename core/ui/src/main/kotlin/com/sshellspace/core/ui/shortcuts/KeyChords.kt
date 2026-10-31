package com.sshellspace.core.ui.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.OS
import com.sshellspace.core.domain.models.getOs

private val isMac = getOs() == OS.MacOS

private val modifierKeys = setOf(
    Key.MetaLeft, Key.MetaRight,
    Key.CtrlLeft, Key.CtrlRight,
    Key.AltLeft, Key.AltRight,
    Key.ShiftLeft, Key.ShiftRight,
    Key.CapsLock, Key.Function,
)

private val letterKeys = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
)

private val digitKeys = listOf(
    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)

private val functionKeys = listOf(
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
)

private val namedKeys: Map<Key, String> = buildMap {
    letterKeys.forEachIndexed { i, key -> put(key, ('A' + i).toString()) }
    digitKeys.forEachIndexed { i, key -> put(key, i.toString()) }
    functionKeys.forEachIndexed { i, key -> put(key, "F${i + 1}") }
    put(Key.Enter, "Enter")
    put(Key.Escape, "Esc")
    put(Key.Tab, "Tab")
    put(Key.Spacebar, "Space")
    put(Key.Backspace, "Backspace")
    put(Key.Delete, "Delete")
    put(Key.DirectionUp, "↑")
    put(Key.DirectionDown, "↓")
    put(Key.DirectionLeft, "←")
    put(Key.DirectionRight, "→")
    put(Key.Comma, ",")
    put(Key.Period, ".")
    put(Key.Slash, "/")
    put(Key.Semicolon, ";")
    put(Key.Minus, "-")
    put(Key.Equals, "=")
    put(Key.LeftBracket, "[")
    put(Key.RightBracket, "]")
    put(Key.Backslash, "\\")
    put(Key.Grave, "`")
    put(Key.Apostrophe, "'")
}

/** Cmd on macOS, Ctrl everywhere else. */
fun primaryChord(key: Key, shift: Boolean = false, alt: Boolean = false) = KeyChord(
    keyCode = key.keyCode,
    meta = isMac,
    ctrl = !isMac,
    alt = alt,
    shift = shift,
)

/** The chord for this event, or null when only modifier keys are held. */
fun KeyEvent.toKeyChord(): KeyChord? {
    if (key in modifierKeys) return null
    return KeyChord(
        keyCode = key.keyCode,
        meta = isMetaPressed,
        ctrl = isCtrlPressed,
        alt = isAltPressed,
        shift = isShiftPressed,
    )
}

/** Whether the chord has a modifier, so it won't swallow normal typing. */
val KeyChord.hasModifier: Boolean get() = meta || ctrl || alt

val KeyChord.label: String
    get() {
        val keyName = namedKeys[Key(keyCode)] ?: "Key $keyCode"
        return if (isMac) {
            buildString {
                if (ctrl) append("⌃")
                if (alt) append("⌥")
                if (shift) append("⇧")
                if (meta) append("⌘")
                append(keyName)
            }
        } else {
            buildList {
                if (ctrl) add("Ctrl")
                if (alt) add("Alt")
                if (shift) add("Shift")
                if (meta) add("Win")
                add(keyName)
            }.joinToString("+")
        }
    }
