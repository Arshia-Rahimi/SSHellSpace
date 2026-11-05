package com.sshellspace.core.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.composables.icons.radix.ArrowLeft
import com.composables.icons.radix.ArrowUp
import com.composables.icons.radix.Archive
import com.composables.icons.radix.Check
import com.composables.icons.radix.ChevronDown
import com.composables.icons.radix.ChevronLeft
import com.composables.icons.radix.ChevronRight
import com.composables.icons.radix.Code
import com.composables.icons.radix.Copy
import com.composables.icons.radix.Cross2
import com.composables.icons.radix.Desktop
import com.composables.icons.radix.DotsHorizontal
import com.composables.icons.radix.Download
import com.composables.icons.radix.ExclamationTriangle
import com.composables.icons.radix.Exit
import com.composables.icons.radix.EyeClosed
import com.composables.icons.radix.EyeOpen
import com.composables.icons.radix.File
import com.composables.icons.radix.FileText
import com.composables.icons.radix.Home
import com.composables.icons.radix.Gear
import com.composables.icons.radix.Globe
import com.composables.icons.radix.IdCard
import com.composables.icons.radix.Image
import com.composables.icons.radix.Keyboard
import com.composables.icons.radix.Link2
import com.composables.icons.radix.LockClosed
import com.composables.icons.radix.MagnifyingGlass
import com.composables.icons.radix.Pencil1
import com.composables.icons.radix.Play
import com.composables.icons.radix.Plus
import com.composables.icons.radix.Radix
import com.composables.icons.radix.Reload
import com.composables.icons.radix.SpeakerLoud
import com.composables.icons.radix.Stop
import com.composables.icons.radix.Trash
import com.composables.icons.radix.Upload
import com.composables.icons.radix.Video
import dev.nucleusframework.window.MacOSLargeCornerRadiusNode

private const val RADIX_GRID = 15f
private const val FOLDER_OUTLINE = "M1.5 3.9a.9 .9 0 0 1 .9-.9h3.1a.9 .9 0 0 1 .64 .26l1.1 1.14h5.36a.9 .9 0 0 1 .9 .9v5.8a.9 .9 0 0 1-.9 .9H2.4a.9 .9 0 0 1-.9-.9z"

/** Radix has no folder, so these are drawn on its grid with its line width and default size, to sit beside the rest. */
private fun outlineIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, RADIX_GRID, RADIX_GRID).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object Icons {
    val Plus = Radix.Plus
    val Cross = Radix.Cross2
    val Close = Radix.Cross2
    val Vault = Radix.LockClosed
    val SshKeys = Radix.IdCard
    val KnownHosts = Radix.Globe
    val PortForwarding = Radix.ArrowLeft
    val Pencil = Radix.Pencil1
    val Trash = Radix.Trash
    val Check = Radix.Check
    val Back = Radix.ChevronLeft
    val ChevronDown = Radix.ChevronDown
    val More = Radix.DotsHorizontal
    val Snippet = Radix.Code
    val EyeOpen = Radix.EyeOpen
    val EyeClosed = Radix.EyeClosed
    val Key = Radix.Keyboard
    val Copy = Radix.Copy
    val Search = Radix.MagnifyingGlass
    val Folder = outlineIcon("folder", FOLDER_OUTLINE)
    val NewFolder = outlineIcon("new-folder", FOLDER_OUTLINE, "M7.5 6.6v3.6M5.7 8.4h3.6")
    val File = Radix.File
    val FileText = Radix.FileText
    val Archive = Radix.Archive
    val Image = Radix.Image
    val Video = Radix.Video
    val Audio = Radix.SpeakerLoud
    val Code = Radix.Code
    val ChevronRight = Radix.ChevronRight
    val Warning = Radix.ExclamationTriangle
    val Settings = Radix.Gear
    val Host = Radix.Desktop
    val Duplicate = Radix.Copy
    val Connect = Radix.Play
    val Start = Radix.Play
    val Stop = Radix.Stop
    val Up = Radix.ArrowUp
    val Exit = Radix.Exit
    val Terminal = Radix.Code
    val Home = Radix.Home
    val Refresh = Radix.Reload
    val Upload = Radix.Upload
    val Download = Radix.Download
    val Link = Radix.Link2
}
