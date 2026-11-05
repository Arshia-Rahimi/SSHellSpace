package com.sshellspace.feature.vault.knownhosts

import com.sshellspace.core.domain.models.KnownHost
import com.sshellspace.feature.vault.domain.DeviceKnownHosts
import com.sshellspace.feature.vault.domain.KnownHostExportResult
import com.sshellspace.feature.vault.domain.KnownHostImportResult
import com.sshellspace.feature.vault.domain.KnownHostRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class KnownHostsPageViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun host(name: String, fingerprint: String = "SHA256:$name") =
        KnownHost(line = "$name ssh-ed25519 x", hosts = listOf(name), keyType = "ssh-ed25519", fingerprint = fingerprint)

    private class FakeRepository : KnownHostRepository {
        val vault = MutableStateFlow(emptyList<KnownHost>())
        var device = DeviceKnownHosts(path = "/home/alice/.ssh/known_hosts")
        var deviceReads = 0
        var importedFrom: String? = null
        var exportedTo: String? = null
        var importResult: KnownHostImportResult = KnownHostImportResult.Success(1, 0, 0, 0, 0)
        var exportResult: KnownHostExportResult = KnownHostExportResult.Success(1)

        override fun getVaultHosts() = vault
        override suspend fun getDeviceHosts() = device.also { deviceReads++ }
        override suspend fun removeFromVault(host: KnownHost) { vault.value = vault.value - host }
        override suspend fun importToVault(path: String) = importResult.also { importedFrom = path }
        override suspend fun exportVault(path: String) = exportResult.also { exportedTo = path }
    }

    private val repository = FakeRepository()

    private fun newViewModel() = KnownHostsPageViewModel(repository)

    @Test
    fun `lists both lists, and reads the device's file again on refresh`() = runTest {
        repository.vault.value = listOf(host("vault.example.com"))
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        repository.device = repository.device.copy(hosts = listOf(host("device.example.com")))
        vm.execute(KnownHostsPageAction.RefreshDevice)

        assertEquals(listOf("vault.example.com"), vm.uiState.value.filteredVaultHosts.flatMap { it.hosts })
        assertEquals(listOf("device.example.com"), vm.uiState.value.filteredDeviceHosts.flatMap { it.hosts })

        repository.device = repository.device.copy(hosts = emptyList())
        vm.execute(KnownHostsPageAction.RefreshDevice)
        assertEquals(emptyList(), vm.uiState.value.filteredDeviceHosts)
        assertEquals(2, repository.deviceReads)
    }

    @Test
    fun `search covers host names and fingerprints in both lists`() = runTest {
        repository.vault.value = listOf(host("alpha.example.com"), host("beta.example.com", fingerprint = "SHA256:zzz"))
        repository.device = repository.device.copy(hosts = listOf(host("alpha.other.org")))
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.execute(KnownHostsPageAction.RefreshDevice)

        vm.execute(KnownHostsPageAction.SetSearch(" ALPHA "))
        assertEquals(1, vm.uiState.value.filteredVaultHosts.size)
        assertEquals(1, vm.uiState.value.filteredDeviceHosts.size)

        vm.execute(KnownHostsPageAction.SetSearch("zzz"))
        assertEquals(listOf("beta.example.com"), vm.uiState.value.filteredVaultHosts.flatMap { it.hosts })
        assertEquals(emptyList(), vm.uiState.value.filteredDeviceHosts)
    }

    @Test
    fun `removing goes to the vault's list`() = runTest {
        val target = host("a.example.com")
        repository.vault.value = listOf(target)
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.execute(KnownHostsPageAction.Remove(target))

        assertEquals(emptyList(), vm.uiState.value.vaultHosts)
    }

    @Test
    fun `cancelling the file dialog does nothing`() = runTest {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.execute(KnownHostsPageAction.Import { null })
        vm.execute(KnownHostsPageAction.Export { null })

        assertNull(repository.importedFrom)
        assertNull(repository.exportedTo)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `an import reports what happened, and the notice can be dismissed`() = runTest {
        repository.importResult = KnownHostImportResult.Success(added = 2, alreadyKnown = 1, conflicting = 1, notAllowed = 1, unreadable = 3)
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.execute(KnownHostsPageAction.Import { "/tmp/hosts" })

        assertEquals("/tmp/hosts", repository.importedFrom)
        val notice = assertNotNull(vm.uiState.value.notice)
        assertEquals(false, notice.isError)
        assertEquals(
            "Added 2 hosts. 1 host already here. " +
                "Skipped 1 host with a different key from the one trusted here. Remove that one first to replace it. " +
                "Skipped 1 host using a wildcard or certificate authority, which could trust a key for many servers. " +
                "Skipped 3 unreadable lines.",
            notice.message,
        )

        vm.execute(KnownHostsPageAction.DismissNotice)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `failures show as errors`() = runTest {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        repository.importResult = KnownHostImportResult.Error("Couldn't read that file.")
        vm.execute(KnownHostsPageAction.Import { "/x" })
        assertEquals(KnownHostsNotice("Couldn't read that file.", isError = true), vm.uiState.value.notice)

        repository.exportResult = KnownHostExportResult.Error("Couldn't write that file.")
        vm.execute(KnownHostsPageAction.Export { "/x" })
        assertEquals(KnownHostsNotice("Couldn't write that file.", isError = true), vm.uiState.value.notice)
    }

    @Test
    fun `an export warns that host names leave the vault unencrypted`() = runTest {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.execute(KnownHostsPageAction.Export { "/x" })

        assertEquals("/x", repository.exportedTo)
        assertTrue(assertNotNull(vm.uiState.value.notice).message.contains("aren't encrypted"))
    }
}
