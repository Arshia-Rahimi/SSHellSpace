package com.sshellspace.feature.terminal.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteNamesTest {

    @Test
    fun `names that could leave the folder are not safe`() {
        listOf("", ".", "..", "../evil", "a/b", "/etc/passwd", "a\u0000b").forEach {
            assertFalse(isSafeRemoteName(it), it)
            assertFalse(isSafeLocalName(it), it)
        }
    }

    @Test
    fun `names that mean something else on Windows are not safe locally`() {
        listOf("..\\evil", "a\\b", "file.txt:stream", "C:", "CON", "con.txt", "lpt1", "trailing.", "space ", "tab\there").forEach {
            assertFalse(isSafeLocalName(it), it)
        }
    }

    @Test
    fun `ordinary names are safe`() {
        listOf(".bashrc", "report (final).pdf", "ünï çødé.txt", "console.log", "...hidden").forEach {
            assertTrue(isSafeRemoteName(it), it)
        }
        assertTrue(isSafeLocalName("console.log"))
        assertTrue(isSafeLocalName(".bashrc"))
    }

    @Test
    fun `printable text hides what could disguise a name`() {
        assertEquals("invoice�fdp.exe", printableRemoteText("invoice‮fdp.exe"))
        assertEquals("a�b", printableRemoteText("a\u001Bb"))
        assertEquals("normal name.txt", printableRemoteText("normal name.txt"))
    }

    @Test
    fun `paths join and go up`() {
        assertEquals("/a/b", joinRemotePath("/a", "b"))
        assertEquals("/b", joinRemotePath("/", "b"))
        assertEquals("/a", parentRemotePath("/a/b"))
        assertEquals("/", parentRemotePath("/a"))
        assertNull(parentRemotePath("/"))
    }

    @Test
    fun `unique names keep the extension and skip what is taken`() {
        val taken = setOf("a.txt", "a (1).txt", "folder", ".hidden")
        assertEquals("b.txt", uniqueFileName("b.txt") { it in taken })
        assertEquals("a (2).txt", uniqueFileName("a.txt") { it in taken })
        assertEquals("folder (1)", uniqueFileName("folder") { it in taken })
        assertEquals(".hidden (1)", uniqueFileName(".hidden") { it in taken })
    }
}
