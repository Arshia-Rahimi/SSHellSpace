package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import java.util.Base64

private const val ESC = '\u001b'
private const val BEL = '\u0007'
private const val OSC_52 = "$ESC]52;"

/** The most base64 one copy may carry, so a program can't make the terminal hold an endless sequence in memory. */
private const val MAX_PAYLOAD_CHARS = 4 * 1024 * 1024

/**
 * Takes OSC 52 sequences out of the output, which is how tmux and vim copy to the system clipboard, and hands what
 * they copy to [onCopy]. JediTerm doesn't support them. Requests to read the clipboard are dropped: a server could
 * otherwise read whatever was copied on this computer.
 *
 * Sequences can be split across reads, so the parts that might start one are held back for the next [filter].
 */
internal class Osc52Filter(private val onCopy: (String) -> Unit) {
    private val held = StringBuilder()
    private val payload = StringBuilder()
    private var collecting = false
    private var overflowed = false

    fun filter(chunk: String): String {
        val input = held.append(chunk).toString()
        held.clear()
        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            index = if (collecting) collect(input, index) else passThrough(input, index, output)
        }
        return output.toString()
    }

    /** Copies output up to the next OSC 52, and returns where to carry on. */
    private fun passThrough(input: String, start: Int, output: StringBuilder): Int {
        val escape = input.indexOf(ESC, start)
        if (escape < 0) {
            output.append(input, start, input.length)
            return input.length
        }
        output.append(input, start, escape)
        val rest = input.substring(escape)
        return when {
            rest.startsWith(OSC_52) -> {
                collecting = true
                overflowed = false
                payload.clear()
                escape + OSC_52.length
            }
            OSC_52.startsWith(rest) -> {
                held.append(rest)
                input.length
            }
            else -> {
                output.append(ESC)
                escape + 1
            }
        }
    }

    /** Collects the sequence up to its terminator, BEL or ESC \, and returns where to carry on. */
    private fun collect(input: String, start: Int): Int {
        val end = (start until input.length).firstOrNull { input[it] == BEL || input[it] == ESC }
        if (end == null) {
            append(input, start, input.length)
            return input.length
        }
        append(input, start, end)
        return when {
            input[end] == BEL -> finish(end + 1)
            end + 1 == input.length -> {
                held.append(ESC)
                input.length
            }
            input[end + 1] == '\\' -> finish(end + 2)
            else -> {
                // Not a terminator, so not a sequence xterm would accept. The escape starts whatever comes next.
                collecting = false
                end
            }
        }
    }

    private fun append(input: String, start: Int, end: Int) {
        if (payload.length + (end - start) > MAX_PAYLOAD_CHARS) overflowed = true
        if (!overflowed) payload.append(input, start, end)
    }

    private fun finish(next: Int): Int {
        collecting = false
        if (!overflowed) decodeOsc52Payload(payload.toString())?.let(onCopy)
        payload.clear()
        return next
    }
}

/** The text an OSC 52 payload (`targets;base64`) copies, or null for a read request or anything malformed. */
internal fun decodeOsc52Payload(payload: String): String? {
    val data = payload.substringAfter(';', missingDelimiterValue = "")
    if (data.isEmpty() || data == "?") return null
    return runCatching { String(Base64.getMimeDecoder().decode(data), Charsets.UTF_8) }.getOrNull()
}
