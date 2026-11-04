package com.sshellspace.feature.vault.data

import com.sshellspace.core.domain.models.DEFAULT_SSH_PORT
import com.sshellspace.feature.vault.domain.SshConfigHost

private const val MAX_INCLUDE_DEPTH = 8
private const val NONE = "none"

private val IGNORED_WITH_WARNING = mapOf(
    "proxycommand" to "ProxyCommand isn't supported, so this host will try to connect directly.",
    "localforward" to "LocalForward wasn't imported. Add it on the Port Forwarding page.",
    "remoteforward" to "RemoteForward wasn't imported. Add it on the Port Forwarding page.",
    "dynamicforward" to "DynamicForward wasn't imported. Add it on the Port Forwarding page.",
)

private class ConfigBlock(val patterns: List<String>) {
    val options = mutableListOf<Pair<String, String>>()
}

/**
 * Reads OpenSSH client config text into concrete hosts. Like OpenSSH, the first value found for a setting wins, across
 * every `Host` block matching the host. Wildcard-only blocks such as `Host *` supply defaults but aren't hosts
 * themselves, and `Match` blocks are skipped because they depend on conditions only known while connecting.
 *
 * @param readInclude returns the contents of every file an `Include` pattern refers to.
 */
internal fun parseSshConfig(text: String, readInclude: (String) -> List<String> = { emptyList() }): List<SshConfigHost> {
    val global = ConfigBlock(listOf("*"))
    val blocks = mutableListOf(global)
    var skippedMatch = false

    fun read(content: String, depth: Int) {
        content.lineSequence().forEach { raw ->
            val (key, value) = splitLine(raw) ?: return@forEach
            when (key) {
                "host" -> {
                    blocks += ConfigBlock(tokens(value))
                    skippedMatch = false
                }
                "match" -> {
                    // A block matching nothing, so the settings under it are dropped.
                    blocks += ConfigBlock(emptyList())
                    skippedMatch = true
                }
                "include" -> if (depth < MAX_INCLUDE_DEPTH && !skippedMatch) {
                    tokens(value).flatMap(readInclude).forEach { read(it, depth + 1) }
                }
                else -> blocks.last().options += key to value
            }
        }
    }
    read(text, depth = 0)

    val aliases = blocks.flatMap { it.patterns }.filter { pattern -> pattern.none { it in "*?!" } }.distinct()
    return aliases.map { alias -> resolveHost(alias, blocks.filter { matches(alias, it.patterns) }) }
}

private fun resolveHost(alias: String, blocks: List<ConfigBlock>): SshConfigHost {
    val options = blocks.flatMap { it.options }
    fun first(key: String) = options.firstOrNull { it.first == key }?.second

    val hostName = first("hostname")?.replace("%h", alias) ?: alias
    val user = first("user")
    val identityFiles = options.filter { it.first == "identityfile" }.map { it.second }
        .filterNot { it.equals(NONE, ignoreCase = true) }
        .map { it.replace("%h", hostName).replace("%r", user.orEmpty()).replace("%d", "~") }
    val proxyJump = first("proxyjump")?.takeUnless { it.equals(NONE, ignoreCase = true) }

    val warnings = buildList {
        IGNORED_WITH_WARNING.forEach { (key, message) -> if (first(key) != null) add(message) }
        if (identityFiles.size > 1) add("Only the first of ${identityFiles.size} IdentityFile entries was used.")
        if (identityFiles.firstOrNull().orEmpty().contains('%')) add("The IdentityFile path uses tokens to fill in.")
    }
    return SshConfigHost(
        alias = alias,
        hostName = hostName,
        user = user,
        port = first("port")?.toIntOrNull() ?: DEFAULT_SSH_PORT,
        identityFile = identityFiles.firstOrNull(),
        proxyJump = proxyJump,
        compression = first("compression").equals("yes", ignoreCase = true),
        keepAliveSeconds = first("serveraliveinterval")?.toIntOrNull(),
        connectTimeoutSeconds = first("connecttimeout")?.toIntOrNull(),
        warnings = warnings,
    )
}

/** The lowercased keyword and its value, or null for blank lines and comments. */
private fun splitLine(raw: String): Pair<String, String>? {
    val line = raw.trim().removePrefix("﻿")
    if (line.isEmpty() || line.startsWith("#")) return null
    val end = line.indexOfFirst { it.isWhitespace() || it == '=' }
    if (end <= 0) return null
    val value = line.substring(end).trimStart { it.isWhitespace() || it == '=' }
    return line.substring(0, end).lowercase() to stripComment(value).trim().let(::unquote)
}

private fun stripComment(value: String): String {
    var quoted = false
    value.forEachIndexed { index, char ->
        if (char == '"') quoted = !quoted
        if (char == '#' && !quoted && (index == 0 || value[index - 1].isWhitespace())) {
            return value.substring(0, index)
        }
    }
    return value
}

private fun unquote(value: String) =
    if (value.length >= 2 && value.startsWith('"') && value.endsWith('"') && value.count { it == '"' } == 2) {
        value.substring(1, value.length - 1)
    } else {
        value
    }

private fun tokens(value: String): List<String> =
    Regex("\"([^\"]*)\"|(\\S+)").findAll(value).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

/** A negated pattern that matches rules a host out, whatever else matches, as OpenSSH does. */
private fun matches(alias: String, patterns: List<String>): Boolean {
    if (patterns.any { it.startsWith("!") && glob(it.removePrefix("!")).matches(alias) }) return false
    return patterns.any { !it.startsWith("!") && glob(it).matches(alias) }
}

private fun glob(pattern: String) = Regex(
    pattern.map {
        when (it) {
            '*' -> ".*"
            '?' -> "."
            else -> Regex.escape(it.toString())
        }
    }.joinToString(""),
)
