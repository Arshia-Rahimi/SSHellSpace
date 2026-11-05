package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.AnchorSide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.LocalWindowClass
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.textDisabled
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.SftpFailure
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/** Only what the bundled decoder reads. SVG isn't here because rendering one means running a whole document format. */
private val PREVIEW_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico")

internal const val MAX_PREVIEW_BYTES = 32L * 1024 * 1024

/** A small file can claim to be any size, and decoding reserves 4 bytes for each pixel it claims. */
internal const val MAX_PREVIEW_PIXELS = 64_000_000L

/** More than any screen shows, so nothing bigger stays in memory while it's on show. */
private const val MAX_PREVIEW_SIDE = 4096

internal val RemoteFile.canPreview: Boolean
    get() = kind == RemoteFileKind.File && name.substringAfterLast('.', "").lowercase() in PREVIEW_EXTENSIONS

/** [width] and [height] are the picture's own, which [bitmap] may be smaller than. */
data class SftpPreviewImage(val bitmap: ImageBitmap, val width: Int, val height: Int)

/** @throws SftpFailure for what isn't a picture, or claims to be too big to hold in memory, before decoding that. */
internal fun decodePreview(bytes: ByteArray): SftpPreviewImage {
    try {
        Codec.makeFromData(Data.makeFromBytes(bytes)).use { codec ->
            if (codec.width.toLong() * codec.height > MAX_PREVIEW_PIXELS) {
                throw SftpFailure("This picture is too large to preview (${codec.width} × ${codec.height}).")
            }
        }
        return Image.makeFromEncoded(bytes).use { image ->
            val scale = minOf(1f, MAX_PREVIEW_SIDE.toFloat() / max(image.width, image.height))
            val bitmap = if (scale < 1f) image.scaledBy(scale) else image.toComposeImageBitmap()
            SftpPreviewImage(bitmap, image.width, image.height)
        }
    } catch (_: IllegalArgumentException) {
        throw SftpFailure("This file can't be read as a picture.")
    }
}

private fun Image.scaledBy(scale: Float): ImageBitmap {
    val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Surface.makeRasterN32Premul(scaledWidth, scaledHeight).use { surface ->
        surface.canvas.drawImageRect(
            this,
            Rect.makeWH(width.toFloat(), height.toFloat()),
            Rect.makeWH(scaledWidth.toFloat(), scaledHeight.toFloat()),
            SamplingMode.LINEAR,
            null,
            true,
        )
        surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
    }
}

/** Looks at one picture over the list. Arrow keys go through the rest of the pictures on show. */
@Composable
internal fun ImagePreview(
    preview: SftpDialog.Preview,
    pictures: List<RemoteFile>,
    downloadLabel: String,
    onDownload: () -> Unit,
    onAction: (SftpAction) -> Unit,
) {
    val dismiss = { onAction(SftpAction.DismissDialog) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Comes before what takes focus, so these keep working after a click on a button in here.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape, Key.Spacebar -> dismiss()
                    Key.DirectionLeft, Key.DirectionUp -> onAction(SftpAction.MovePreview(-1))
                    Key.DirectionRight, Key.DirectionDown -> onAction(SftpAction.MovePreview(1))
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { dismiss() } }
            .padding(if (LocalWindowClass.current.isCompact) 12.dp else 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][border], shape)
                // Keeps a click on the panel from closing it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            PreviewHeader(preview, pictures, downloadLabel, onDownload, onAction)
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][separator]))
            PreviewBody(preview, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewHeader(
    preview: SftpDialog.Preview,
    pictures: List<RemoteFile>,
    downloadLabel: String,
    onDownload: () -> Unit,
    onAction: (SftpAction) -> Unit,
) {
    val index = pictures.indexOfFirst { it.path == preview.file.path }
    val details = listOfNotNull(
        preview.image?.let { "${it.width} × ${it.height}" },
        formatSize(preview.file.size).takeIf { !preview.file.isSymlink },
        "${index + 1} of ${pictures.size}".takeIf { index >= 0 && pictures.size > 1 },
    ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FittedText(displayed = preview.file.displayName, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(details, color = Theme[colors][textSecondary], fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(end = 8.dp))
        PreviewButton(Icons.Back, "Previous picture", enabled = index > 0) { onAction(SftpAction.MovePreview(-1)) }
        PreviewButton(Icons.ChevronRight, "Next picture", enabled = index in 0 until pictures.lastIndex) {
            onAction(SftpAction.MovePreview(1))
        }
        PreviewButton(Icons.Download, downloadLabel, onClick = onDownload)
        PreviewButton(Icons.Close, "Close") { onAction(SftpAction.DismissDialog) }
    }
}

@Composable
private fun PreviewButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Tooltip(text = label, side = AnchorSide.Bottom) {
        HoverButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
            UnstyledIcon(
                imageVector = icon,
                contentDescription = label,
                tint = Theme[colors][if (enabled) onSurface else textDisabled],
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun PreviewBody(preview: SftpDialog.Preview, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().background(Theme[colors][background]),
        contentAlignment = Alignment.Center,
    ) {
        when {
            preview.image != null -> Image(
                bitmap = preview.image.bitmap,
                contentDescription = preview.file.displayName,
                // Inside, not Fit: a small icon stays sharp at its own size instead of being blown up.
                contentScale = ContentScale.Inside,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
            preview.error != null -> Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UnstyledIcon(
                    imageVector = Icons.Warning,
                    contentDescription = null,
                    tint = Theme[colors][error],
                    modifier = Modifier.size(24.dp),
                )
                Text(preview.error, textAlign = TextAlign.Center)
            }
            else -> {
                LoadingBar(modifier = Modifier.align(Alignment.TopCenter))
                Text("Loading preview…", color = Theme[colors][textSecondary])
            }
        }
    }
}
