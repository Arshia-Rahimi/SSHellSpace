package com.sshellspace.feature.terminal.ui.screen.sftp

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class SftpFileDropTest {

    private class Files(private val read: () -> Any) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor?) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor?) = read()
    }

    @Test
    fun `files in a drop become paths, in order`() {
        val paths = droppedPaths(Files { listOf(File("/tmp/b.txt"), File("/tmp/a.txt")) })

        assertEquals(listOf(File("/tmp/b.txt").toPath(), File("/tmp/a.txt").toPath()), paths)
    }

    @Test
    fun `dragged text isn't a file`() {
        assertEquals(emptyList(), droppedPaths(StringSelection("/etc/passwd")))
    }

    @Test
    fun `a source that stops offering its data gives nothing`() {
        assertEquals(emptyList(), droppedPaths(Files { throw IOException("gone") }))
        assertEquals(emptyList(), droppedPaths(Files { throw UnsupportedFlavorException(DataFlavor.javaFileListFlavor) }))
    }

    @Test
    fun `anything in the list that isn't a file is left out`() {
        assertEquals(listOf(File("/tmp/a").toPath()), droppedPaths(Files { listOf("text", File("/tmp/a"), 1) }))
    }

    @Test
    fun `an unexpected payload gives nothing`() {
        assertEquals(emptyList(), droppedPaths(Files { "not a list" }))
    }
}
