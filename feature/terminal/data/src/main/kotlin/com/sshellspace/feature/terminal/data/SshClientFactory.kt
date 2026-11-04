package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.data.ssh.SessionConfigurer
import com.sshellspace.feature.terminal.data.ssh.agent.SshAgentLocator
import com.sshellspace.feature.terminal.data.ssh.agent.SystemSshAgentFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.server.forward.ForwardingFilter
import org.apache.sshd.server.forward.TcpForwardingFilter

/** The OpenSSH known_hosts file, shared with the `ssh` command line tool. */
val DefaultKnownHostsPath: Path = Path.of(System.getProperty("user.home"), ".ssh", "known_hosts")

/**
 * The single MINA client used for every connection. Per-connection settings come from each session's
 * connection context (see [SessionConfigurer]), so nothing here is specific to one host.
 */
fun createSshClient(
    knownHosts: Path = DefaultKnownHostsPath,
    agentLocator: SshAgentLocator = SshAgentLocator(),
): SshClient = SshClient.setUpDefaultClient().apply {
    // Saved connections are the source of truth; don't let ~/.ssh/config rewrite hosts, users or jumps.
    hostConfigEntryResolver = HostConfigEntryResolver.EMPTY
    keyIdentityProvider = KeyIdentityProvider.EMPTY_KEYS_PROVIDER
    // Sessions without a connection context aren't ours, so they get no trust.
    serverKeyVerifier = RejectAllServerKeyVerifier.INSTANCE
    agentFactory = SystemSshAgentFactory(agentLocator)
    // The default rejects the server's `forwarded-tcpip` channels, which breaks remote forwards. The client only
    // accepts those channels, and only for ports it asked to forward, so allowing TCP lets nothing else in.
    forwardingFilter = ForwardingFilter.asForwardingFilter(null, null, TcpForwardingFilter.DEFAULT)
    // Try the plain password method before keyboard-interactive, so a password connection doesn't run into a
    // 2FA challenge first. Methods a connection didn't pick have nothing to offer and are skipped quickly.
    CoreModuleProperties.PREFERRED_AUTHS.set(this, "publickey,password,keyboard-interactive")
    // Old algorithms an attacker in the middle could otherwise negotiate down to. RSA keys still work, using SHA-2.
    cipherFactories = cipherFactories.filterNot { it.name.endsWith("-cbc") }
    macFactories = macFactories.filterNot { it.name.startsWith("hmac-sha1") }
    signatureFactories = signatureFactories.filterNot { it.name == "ssh-rsa" }
    // Waiting for the user's answer to a login question holds a worker, so one session's open prompt shouldn't stall others.
    nioWorkers = NIO_WORKERS
    addSessionListener(SessionConfigurer { knownHosts.also(::ensureParentDirectory) })
    start()
}

private const val NIO_WORKERS = 16

private fun ensureParentDirectory(file: Path) {
    val dir = file.parent ?: return
    if (Files.isDirectory(dir)) return
    Files.createDirectories(dir)
    runCatching { Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------")) }
}
