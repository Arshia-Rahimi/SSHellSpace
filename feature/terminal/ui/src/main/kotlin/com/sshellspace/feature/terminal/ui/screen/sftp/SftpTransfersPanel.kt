package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.success
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.warning

private val TransferRowHeight = 28.dp
private val TransfersMaxHeight = TransferRowHeight * 5
private val TransferBarWidth = 96.dp
private val CollapsedBarWidth = 120.dp
private val PercentWidth = 32.dp
private const val COLLAPSED_ROTATION = -90f
private const val PERCENT = 100
private const val MAX_LISTED_NAMES = 5

@Composable
internal fun TransfersPanel(transfers: List<SftpTransfer>, collapsed: Boolean, onAction: (SftpAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Theme[colors][surface])) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][separator]))
        TransfersHeader(transfers, collapsed, onAction)
        if (!collapsed) {
            val listState = rememberLazyListState()
            val contentHeight = TransferRowHeight * transfers.size
            Box(modifier = Modifier.fillMaxWidth().height(minOf(contentHeight, TransfersMaxHeight))) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(transfers, key = { it.id }) { transfer ->
                        TransferRow(transfer) { onAction(SftpAction.CancelTransfer(transfer.id)) }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    style = sftpScrollbarStyle(),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(1.dp),
                )
            }
        }
    }
}

@Composable
private fun TransfersHeader(transfers: List<SftpTransfer>, collapsed: Boolean, onAction: (SftpAction) -> Unit) {
    val fraction = transfersFraction(transfers)
    Row(
        modifier = Modifier.fillMaxWidth().height(TransferRowHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoverButton(
            onClick = { onAction(SftpAction.ToggleTransfers) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UnstyledIcon(
                    imageVector = Icons.ChevronDown,
                    contentDescription = if (collapsed) "Show transfers" else "Hide transfers",
                    tint = Theme[colors][textSecondary],
                    modifier = Modifier.size(14.dp).rotate(if (collapsed) COLLAPSED_ROTATION else 0f),
                )
                Text("Transfers", fontSize = 13.sp, maxLines = 1)
                Text(transfersSummary(transfers), color = Theme[colors][textSecondary], fontSize = 12.sp, maxLines = 1)
                // Said apart from the rest, since a failure can have scrolled out of view or be collapsed away.
                val failed = transfers.count { it.status is SftpTransferStatus.Failed }
                if (failed > 0) Text("$failed failed", color = Theme[colors][error], fontSize = 12.sp, maxLines = 1)
                if (collapsed && fraction != null) ProgressBar(fraction, Modifier.width(CollapsedBarWidth))
            }
        }
        if (transfers.any { it.status.isFinished }) {
            HoverButton(onClick = { onAction(SftpAction.ClearFinishedTransfers) }) {
                Text(
                    "Clear finished",
                    color = Theme[colors][primary],
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TransferRow(transfer: SftpTransfer, onCancel: () -> Unit) {
    val status = transfer.status
    val isRunning = status == SftpTransferStatus.Running
    Row(
        modifier = Modifier.fillMaxWidth().height(TransferRowHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnstyledIcon(
            imageVector = if (transfer.direction == SftpDirection.Download) Icons.Download else Icons.Upload,
            contentDescription = transfer.direction.name,
            tint = transfer.statusColor(),
            modifier = Modifier.size(14.dp),
        )
        FittedText(displayed = transfer.name, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(TransferBarWidth)) {
            if (isRunning) ProgressBar(transfer.fraction())
        }
        Text(
            if (isRunning && transfer.progress != null) "${(transfer.fraction() * PERCENT).toInt()}%" else "",
            color = Theme[colors][textSecondary],
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(PercentWidth),
        )
        FittedText(
            displayed = transfer.statusText(),
            color = if (status is SftpTransferStatus.Failed) Theme[colors][error] else Theme[colors][textSecondary],
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        // Stays as an empty slot once finished, so the columns of every row line up.
        Box(modifier = Modifier.size(20.dp)) {
            if (!status.isFinished) {
                HoverButton(onClick = onCancel, modifier = Modifier.size(20.dp)) {
                    UnstyledIcon(
                        imageVector = Icons.Close,
                        contentDescription = "Cancel ${transfer.name}",
                        tint = Theme[colors][onSurface],
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SftpTransfer.statusColor(): Color = when (status) {
    SftpTransferStatus.Running -> Theme[colors][primary]
    SftpTransferStatus.Done -> Theme[colors][if (skipped.isEmpty()) success else warning]
    is SftpTransferStatus.Failed -> Theme[colors][error]
    SftpTransferStatus.Queued, SftpTransferStatus.Cancelled -> Theme[colors][textSecondary]
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            // Not surfaceElevated: in the light themes that's the same white as the panel.
            .background(Theme[colors][separator]),
    ) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Theme[colors][primary]))
    }
}

internal fun SftpTransfer.fraction(): Float {
    val done = progress ?: return 0f
    if (done.bytesTotal <= 0) return 0f
    return (done.bytesDone.toFloat() / done.bytesTotal).coerceIn(0f, 1f)
}

/** How far along everything still running is together, or null if none of it knows yet. */
internal fun transfersFraction(transfers: List<SftpTransfer>): Float? {
    val running = transfers.filter { it.status == SftpTransferStatus.Running }.mapNotNull { it.progress }
    val total = running.sumOf { it.bytesTotal }
    if (total <= 0) return null
    return (running.sumOf { it.bytesDone }.toFloat() / total).coerceIn(0f, 1f)
}

/** What's still under way, to tell how things stand with the list collapsed. */
internal fun transfersSummary(transfers: List<SftpTransfer>): String {
    val parts = listOfNotNull(
        transfers.count { it.status == SftpTransferStatus.Running }.takeIf { it > 0 }?.let { "$it running" },
        transfers.count { it.status == SftpTransferStatus.Queued }.takeIf { it > 0 }?.let { "$it waiting" },
    )
    return if (parts.isEmpty()) "All finished" else parts.joinToString(", ")
}

internal fun SftpTransfer.statusText(): String = when (val current = status) {
    SftpTransferStatus.Queued -> "Waiting"
    SftpTransferStatus.Running -> progress?.let { done ->
        val files = if (done.filesTotal > 1) ", ${done.filesDone} of ${done.filesTotal} files" else ""
        "${formatSize(done.bytesDone)} of ${formatSize(done.bytesTotal)}$files"
    } ?: "Preparing"
    SftpTransferStatus.Done -> buildString {
        append("Done")
        progress?.let { append(", ").append(formatSize(it.bytesDone)) }
        if (skipped.isNotEmpty()) {
            append(". Skipped ").append(skipped.size).append(": ")
            append(skipped.take(MAX_LISTED_NAMES).joinToString("; "))
            if (skipped.size > MAX_LISTED_NAMES) append("; …")
        }
    }
    SftpTransferStatus.Cancelled -> "Cancelled"
    is SftpTransferStatus.Failed -> current.message
}
