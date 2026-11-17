package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import java.net.URI
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

private const val WAIT_MILLIS = 5_000L

/** A program the test writes output for, and that records what the terminal sends it. */
private class FakeProcess : ShellProcess {
    val output = Channel<String?>(Channel.UNLIMITED)
    val written = CopyOnWriteArrayList<String>()
    val exit = CompletableDeferred<Int>()

    override suspend fun read(): String? = output.receive()
    override suspend fun write(bytes: ByteArray) {
        written += String(bytes, Charsets.UTF_8)
    }

    override suspend fun resize(columns: Int, rows: Int) = Unit
    override suspend fun waitForExit(): Int = exit.await()
    override suspend fun close() {
        output.trySend(null)
    }
}

class JediTermSessionTest {

    private val process = FakeProcess()
    private var session: JediTermSession? = null

    @AfterTest
    fun tearDown() {
        session?.close()
    }

    private val opened = CopyOnWriteArrayList<URI>()

    private fun start(startupCommand: String? = null, onExit: ((Int) -> Unit)? = null) =
        JediTermSession(process, startupCommand, onExit, openLink = { opened += it }).also { session = it }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS)
        while (!condition()) {
            if (System.nanoTime() > deadline) throw TimeoutException("Timed out waiting")
            Thread.sleep(10)
        }
    }

    private fun JediTermSession.screenLine(row: Int) = locked { textBuffer.getLine(row).text.trimEnd() }

    @Test
    fun `output is shown on the screen`() {
        val session = start()
        process.output.trySend("hello\r\n\u001b[1mworld")

        waitUntil { session.screenLine(1) == "world" }
        assertEquals("hello", session.screenLine(0))
        assertEquals(5, session.cursorX)
    }

    @Test
    fun `typed text and special keys reach the program`() {
        val session = start()
        session.type("ls")
        session.codeForKey(com.jediterm.core.input.KeyEvent.VK_ENTER, 0)?.let(session::type)

        waitUntil { process.written.joinToString("") == "ls\r" }
    }

    @Test
    fun `arrow keys follow the program's cursor key mode`() {
        val session = start()
        process.output.trySend("\u001b[?1h")
        waitUntil { session.codeForKey(com.jediterm.core.input.KeyEvent.VK_UP, 0)?.decodeToString() == "\u001bOA" }
    }

    @Test
    fun `a paste is bracketed once the program asks, without escapes that could end it early`() {
        val session = start()
        session.paste("one\ntwo")
        waitUntil { process.written.joinToString("") == "one\rtwo" }

        process.output.trySend("\u001b[?2004h")
        process.output.trySend("ready")
        waitUntil { session.screenLine(0) == "ready" }
        process.written.clear()
        session.paste("rm -rf x\u001b[201~\ndone")

        waitUntil { process.written.isNotEmpty() }
        assertEquals("\u001b[200~rm -rf x[201~\rdone\u001b[201~", process.written.joinToString(""))
    }

    @Test
    fun `a program's copy reaches the clipboard and isn't shown`() {
        val session = start()
        val copied = CopyOnWriteArrayList<String>()
        session.onCopy = { copied += it }
        val payload = Base64.getEncoder().encodeToString("from tmux".toByteArray())
        process.output.trySend("a\u001b]52;c;$payload\u0007b")

        waitUntil { session.screenLine(0) == "ab" }
        assertEquals(listOf("from tmux"), copied)
    }

    @Test
    fun `the program's color queries are answered with the theme's colors`() {
        start()
        process.output.trySend("\u001b]11;?\u0007")

        waitUntil { process.written.any { it.startsWith("\u001b]11;rgb:") } }
    }

    @Test
    fun `the startup command is typed once the shell prints something`() {
        start(startupCommand = "cd /srv")
        assertTrue(process.written.isEmpty())

        process.output.trySend("$ ")
        waitUntil { process.written.toList() == listOf("cd /srv\r") }
    }

    @Test
    fun `the exit code is reported when the program ends`() {
        val exitCode = CompletableDeferred<Int>()
        start(onExit = { exitCode.complete(it) })

        process.exit.complete(3)
        process.output.trySend(null)

        waitUntil { exitCode.isCompleted }
        assertEquals(3, exitCode.getCompleted())
    }

    @Test
    fun `closing doesn't report an exit`() {
        var reported = false
        val session = start(onExit = { reported = true })
        process.exit.complete(0)

        session.close()
        Thread.sleep(200)

        assertTrue(!reported)
    }

    @Test
    fun `selected text is copied as it appears`() {
        val session = start()
        process.output.trySend("first line\r\nsecond")
        waitUntil { session.screenLine(1) == "second" }

        session.selection = Selection(Cell(6, 0), Cell(3, 1))

        assertEquals("line\nsec", session.selectedText())
        assertEquals(Selection(Cell(0, 1), Cell(6, 1)), session.wordAt(Cell(2, 1)))
    }

    @Test
    fun `the program's title is kept, without characters that could disguise it`() {
        val session = start()
        process.output.trySend("\u001b]2;vim \u202Etxt.exe\u0007")

        waitUntil { session.title.value != null }
        assertEquals("vim \uFFFDtxt.exe", session.title.value)
    }

    @Test
    fun `an OSC 8 web link opens when clicked, and other schemes aren't links`() {
        val session = start()
        process.output.trySend("\u001b]8;;https://example.com/docs\u0007docs\u001b]8;;\u0007 ")
        process.output.trySend("\u001b]8;;file:///etc/passwd\u0007file\u001b]8;;\u0007")
        waitUntil { session.screenLine(0) == "docs file" }

        session.linkAt(Cell(1, 0))?.navigate()

        assertEquals(listOf(URI("https://example.com/docs")), opened.toList())
        assertEquals(null, session.linkAt(Cell(6, 0)))
    }

    @Test
    fun `the cursor blinks unless the program asks for a steady one`() {
        val session = start()
        assertTrue(session.cursorBlinks)

        process.output.trySend("\u001b[2 q")
        waitUntil { !session.cursorBlinks }
    }
}
