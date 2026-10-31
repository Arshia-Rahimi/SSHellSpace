package com.sshellspace.core.ui.designsystem.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The window can't be resized below this. */
val MinWindowSize = DpSize(720.dp, 480.dp)

/** Size of a freshly opened window. */
val DefaultWindowSize = DpSize(1200.dp, 800.dp)

/**
 * Width-based layout classes the UI supports.
 *
 * - [Compact]: from [MinWindowSize] up to [EXPANDED_MIN_WIDTH]. Navigation collapses to icons and
 *   side panels float over the content instead of sitting next to it.
 * - [Expanded]: [EXPANDED_MIN_WIDTH] and wider.
 */
enum class WindowClass {
    Compact,
    Expanded,
    ;

    val isCompact: Boolean get() = this == Compact

    companion object {
        val EXPANDED_MIN_WIDTH: Dp = 960.dp

        fun fromWidth(width: Dp): WindowClass = if (width < EXPANDED_MIN_WIDTH) Compact else Expanded
    }
}

val LocalWindowClass = compositionLocalOf { WindowClass.Expanded }

val currentWindowClass: WindowClass
    @Composable get() = LocalWindowClass.current

/** Measures the available width and provides the matching [WindowClass] to [content]. */
@Composable
fun ProvideWindowClass(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalWindowClass provides WindowClass.fromWidth(maxWidth)) {
            content()
        }
    }
}
