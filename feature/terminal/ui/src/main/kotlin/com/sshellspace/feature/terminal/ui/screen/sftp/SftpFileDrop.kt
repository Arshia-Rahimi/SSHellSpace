package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.feature.terminal.domain.printableRemoteText
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val DROP_TINT_ALPHA = 0.12f
private val DropCardMaxWidth = 460.dp

/** The files in a drop, or nothing when it holds something else, such as text dragged from another app. */
internal fun droppedPaths(transferable: Transferable): List<Path> {
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    val files = try {
        transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
    } catch (_: UnsupportedFlavorException) {
        null
    } catch (_: IOException) {
        // The app the drag came from can stop offering its data at any point.
        null
    }
    return files.orEmpty().filterIsInstance<File>().mapNotNull { file ->
        try {
            file.toPath()
        } catch (_: InvalidPathException) {
            null
        }
    }
}

/**
 * Accepts files dragged in from the desktop. The file list only arrives with the drop, since the Tao backend doesn't
 * report it during the drag, so [onHoverChange] can fire for a drag that turns out to hold no files.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.fileDropTarget(
    enabled: Boolean,
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<Path>) -> Unit,
): Modifier {
    val isEnabled by rememberUpdatedState(enabled)
    val hoverChanged by rememberUpdatedState(onHoverChange)
    val dropped by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = hoverChanged(true)
            override fun onExited(event: DragAndDropEvent) = hoverChanged(false)
            override fun onEnded(event: DragAndDropEvent) = hoverChanged(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hoverChanged(false)
                val paths = droppedPaths(event.awtTransferable)
                if (paths.isEmpty()) return false
                dropped(paths)
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            isEnabled && event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        },
        target = target,
    )
}

/** Says where a drop will land, since that depends on the folder on show and can't be undone by cancelling. */
@Composable
internal fun FileDropOverlay(directory: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .clip(shape)
            .background(Theme[colors][primary].copy(alpha = DROP_TINT_ALPHA))
            .border(2.dp, Theme[colors][primary], shape),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = DropCardMaxWidth)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Theme[colors][primary])
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UnstyledIcon(imageVector = Icons.Upload, contentDescription = null, tint = Theme[colors][onPrimary])
            Text(
                "Drop to upload to ${printableRemoteText(directory)}",
                color = Theme[colors][onPrimary],
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
