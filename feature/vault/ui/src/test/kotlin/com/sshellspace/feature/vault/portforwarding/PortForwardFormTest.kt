package com.sshellspace.feature.vault.portforwarding

import com.sshellspace.core.domain.models.PortForwardType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PortForwardFormTest {

    private val form = PortForwardForm(
        name = "proxy",
        connectionId = Uuid.random(),
        type = PortForwardType.Dynamic,
        bindPort = "1080",
    )

    private fun bindError(form: PortForwardForm) = form.validate(emptyList(), null, showRequired = true).bind

    @Test
    fun `loopback addresses aren't exposed`() {
        listOf("127.0.0.1", "127.0.0.2", "localhost", "::1").forEach {
            assertFalse(form.copy(bindHost = it).isExposed, it)
        }
    }

    @Test
    fun `other addresses are exposed`() {
        listOf("0.0.0.0", "192.168.1.5", "::", "").forEach { assertTrue(form.copy(bindHost = it).isExposed, it) }
    }

    @Test
    fun `saving an exposed listener needs confirmation`() {
        assertNotNull(bindError(form.copy(bindHost = "0.0.0.0")))
        assertNull(bindError(form.copy(bindHost = "0.0.0.0", networkAccessConfirmed = true)))
    }

    @Test
    fun `a local listener needs no confirmation`() {
        assertNull(bindError(form))
    }
}
