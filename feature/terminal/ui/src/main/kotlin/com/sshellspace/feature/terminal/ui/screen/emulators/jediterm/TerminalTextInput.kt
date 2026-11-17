// The input method request is still experimental in Compose; Tao implements it, which is what the terminal needs.
@file:OptIn(ExperimentalComposeUiApi::class)

package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the input method does to the terminal. */
internal interface TerminalTextInput {
    /** The text being composed, such as unconverted kana, shown at the cursor until it is committed. */
    var composing: String

    fun commit(text: String)

    /** The input method replacing text it already committed, such as an accent picked after holding a key. */
    fun deleteBefore(codePoints: Int)

    /** Where the cursor is in the terminal, for the input method to show its candidates beside. */
    fun cursorRect(): Rect
}

/**
 * Opens an input method session while the terminal has focus, so dead keys and input methods such as Chinese,
 * Japanese or Korean work. The terminal has no editable text, so the input method only ever sees what is being
 * composed.
 */
internal fun Modifier.terminalTextInput(input: TerminalTextInput): Modifier = this then TextInputElement(input)

private data class TextInputElement(val input: TerminalTextInput) : ModifierNodeElement<TextInputNode>() {
    override fun create() = TextInputNode(input)

    override fun update(node: TextInputNode) {
        node.input = input
    }
}

private class TextInputNode(var input: TerminalTextInput) :
    Modifier.Node(),
    PlatformTextInputModifierNode,
    FocusEventModifierNode,
    LayoutAwareModifierNode {

    private var session: Job? = null
    private var coordinates: LayoutCoordinates? = null

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused) {
            if (session == null) {
                session = coroutineScope.launch { establishTextInputSession { startInputMethod(Request()) } }
            }
        } else {
            session?.cancel()
            session = null
            input.composing = ""
        }
    }

    override fun onDetach() {
        session = null
    }

    private fun toRoot(rect: Rect): Rect {
        val origin = coordinates?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
        return rect.translate(origin)
    }

    private fun bounds(): Rect = coordinates?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero

    private inner class Request : PlatformTextInputMethodRequest {
        private val editor = object : TextEditingScope {
            override fun commitText(text: CharSequence, newCursorPosition: Int) {
                input.composing = ""
                if (text.isNotEmpty()) input.commit(text.toString())
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int) {
                input.composing = text.toString()
            }

            override fun finishComposingText() {
                val composed = input.composing
                input.composing = ""
                if (composed.isNotEmpty()) input.commit(composed)
            }

            override fun deleteSurroundingTextInCodePoints(lengthBeforeCursor: Int, lengthAfterCursor: Int) {
                if (lengthBeforeCursor > 0) input.deleteBefore(lengthBeforeCursor)
            }

            override fun setSelection(start: Int, end: Int) = Unit
            override fun setComposingRegion(start: Int, end: Int) = Unit
        }

        @Deprecated("Superseded by state")
        override val value: () -> TextFieldValue = {
            val composing = input.composing
            TextFieldValue(
                composing,
                TextRange(composing.length),
                TextRange(0, composing.length).takeIf { composing.isNotEmpty() },
            )
        }

        override val state: TextEditorState = object : TextEditorState {
            override val text: String get() = input.composing
            override val selection: TextRange get() = TextRange(text.length)
            override val composition: TextRange? get() = TextRange(0, text.length).takeIf { text.isNotEmpty() }
            override val length: Int get() = text.length
            override fun get(index: Int): Char = text[index]
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
                text.subSequence(startIndex, endIndex)
        }

        override val imeOptions: ImeOptions = ImeOptions(autoCorrect = false, imeAction = ImeAction.None)

        override val onEditCommand: (List<EditCommand>) -> Unit = { commands ->
            commands.forEach { command ->
                when (command) {
                    is CommitTextCommand -> editor.commitText(command.text, command.newCursorPosition)
                    is SetComposingTextCommand -> editor.setComposingText(command.text, command.newCursorPosition)
                    is FinishComposingTextCommand -> editor.finishComposingText()
                    else -> Unit
                }
            }
        }

        override val onImeAction: ((ImeAction) -> Unit)? = null
        override val textLayoutResult: () -> TextLayoutResult? = { null }
        override val focusedRectInRoot: () -> Rect? = { toRoot(input.cursorRect()) }
        override val textFieldRectInRoot: () -> Rect? = { bounds() }
        override val textClippingRectInRoot: () -> Rect? = { bounds() }
        override val unclippedTextOffsetInRoot: () -> Offset? = { bounds().topLeft }
        override val editText: (block: TextEditingScope.() -> Unit) -> Unit = { block -> editor.block() }
    }
}
