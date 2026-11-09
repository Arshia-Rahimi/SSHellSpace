package com.sshellspace.config.update

import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.updater.provider.GitHubProvider
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestRelativeProviderTest {
    private val provider = ManifestRelativeProvider(GitHubProvider("owner", "repo"))

    @Test
    fun `finds files beside the manifest, whatever the release's tag is`() {
        provider.resolveMetadataUrl("latest", Platform.MacOS, HttpClient.newHttpClient())

        assertEquals(
            "https://github.com/owner/repo/releases/latest/download/app-1.0.1-mac-arm64.zip",
            provider.getDownloadUrl("app-1.0.1-mac-arm64.zip", "1.0.1"),
        )
    }

    @Test
    fun `falls back to the wrapped provider before any manifest is resolved`() {
        assertEquals(
            "https://github.com/owner/repo/releases/download/v1.0.1/app.zip",
            provider.getDownloadUrl("app.zip", "1.0.1"),
        )
    }
}
