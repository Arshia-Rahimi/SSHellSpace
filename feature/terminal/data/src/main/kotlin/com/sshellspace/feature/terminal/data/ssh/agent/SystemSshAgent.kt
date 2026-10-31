package com.sshellspace.feature.terminal.data.ssh.agent

import com.sshellspace.feature.terminal.data.ssh.ConnectionContext
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import org.apache.sshd.agent.SshAgent
import org.apache.sshd.agent.SshAgentFactory
import org.apache.sshd.agent.SshAgentServer
import org.apache.sshd.agent.common.AbstractAgentProxy
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.FactoryManager
import org.apache.sshd.common.channel.ChannelFactory
import org.apache.sshd.common.session.ConnectionService
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.buffer.Buffer
import org.apache.sshd.common.util.buffer.ByteArrayBuffer

/**
 * Talks to the user's running SSH agent: the `SSH_AUTH_SOCK` Unix socket, or the OpenSSH named pipe on Windows.
 * MINA's own `agent.unix` client needs Tomcat's native APR library, so this uses the JDK's socket support instead.
 */
class SshAgentLocator(
    private val socketPathProvider: () -> String? = ::defaultAgentSocketPath,
) {
    val socketPath: String? get() = socketPathProvider()

    fun open(): SshAgent {
        val path = socketPath ?: throw IOException("SSH_AUTH_SOCK isn't set")
        val transport = if (path.startsWith("""\\.\pipe\""")) PipeTransport(path) else UnixSocketTransport(path)
        return AgentProxy(transport)
    }
}

private const val WINDOWS_PIPE = """\\.\pipe\openssh-ssh-agent"""

private fun defaultAgentSocketPath(): String? {
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    return System.getenv("SSH_AUTH_SOCK")?.takeIf { it.isNotBlank() } ?: WINDOWS_PIPE.takeIf { isWindows }
}

/** Hands the system agent to MINA's `publickey` auth, but only for connections that chose the agent. */
internal class SystemSshAgentFactory(private val locator: SshAgentLocator) : SshAgentFactory {
    override fun getChannelForwardingFactories(manager: FactoryManager?): List<ChannelFactory> = emptyList()

    override fun createClient(session: Session?, manager: FactoryManager?): SshAgent? {
        val context = (session as? ClientSession)?.connectionContext?.getAttribute(ConnectionContext.KEY)
        return if (context?.useAgent == true) locator.open() else null
    }

    override fun createServer(service: ConnectionService?): SshAgentServer =
        throw UnsupportedOperationException("Agent forwarding is not supported")
}

private class AgentProxy(private val transport: AgentTransport) : AbstractAgentProxy(null) {
    @Volatile
    private var open = true

    override fun isOpen() = open && transport.isOpen

    /** [buffer] is a complete length-prefixed request; the reply is returned without its length prefix. */
    @Synchronized
    override fun request(buffer: Buffer): Buffer {
        transport.write(buffer.array(), buffer.rpos(), buffer.available())
        val length = ByteBuffer.wrap(transport.read(Int.SIZE_BYTES)).int
        if (length !in 1..MAX_MESSAGE_BYTES) throw IOException("Bad SSH agent reply length: $length")
        return ByteArrayBuffer(transport.read(length))
    }

    override fun close() {
        if (!open) return
        open = false
        transport.close()
        super.close()
    }

    private companion object {
        const val MAX_MESSAGE_BYTES = 256 * 1024
    }
}

private interface AgentTransport : Closeable {
    val isOpen: Boolean
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun read(length: Int): ByteArray
}

private class UnixSocketTransport(path: String) : AgentTransport {
    private val channel = SocketChannel.open(UnixDomainSocketAddress.of(path))

    override val isOpen get() = channel.isOpen

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        val buffer = ByteBuffer.wrap(bytes, offset, length)
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    override fun read(length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw EOFException("SSH agent closed the connection")
        }
        return buffer.array()
    }

    override fun close() = channel.close()
}

private class PipeTransport(path: String) : AgentTransport {
    private val pipe = RandomAccessFile(path, "rw")

    override val isOpen get() = pipe.channel.isOpen

    override fun write(bytes: ByteArray, offset: Int, length: Int) = pipe.write(bytes, offset, length)

    override fun read(length: Int): ByteArray = ByteArray(length).also(pipe::readFully)

    override fun close() = pipe.close()
}
