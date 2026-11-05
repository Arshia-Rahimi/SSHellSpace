package utils

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

private const val ALGORITHM = "Ed25519"
private const val PUBLIC_KEY_BYTES = 32
private val MANIFEST_NAME = Regex("""(latest|beta|alpha)(-mac|-linux)?\.yml""")
private val MANIFEST_VERSION = Regex("""(?m)^version:\s*['"]?([^'"\s]+)""")
private val MANIFEST_FILE = Regex("""(?m)^\s*-?\s*url:\s*['"]?([^'"\n]+?)['"]?\s*$""")

/** Keep in step with `updateSignatureMessage` in the app, whose test pins this format. */
private fun signatureMessage(version: String, file: File): ByteArray {
    val digest = MessageDigest.getInstance("SHA-512")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    val hash = digest.digest().joinToString("") { "%02x".format(it) }
    return "SSHellSpace update 1\n$version\n$hash".toByteArray()
}

/** Creates the key that signs releases, readable only by this user, and prints the public half for the app to embed. */
@DisableCachingByDefault(because = "Generates a secret")
abstract class GenerateUpdateKeyTask : DefaultTask() {
    @get:Input
    abstract val keyFile: Property<String>

    @TaskAction
    fun generate() {
        val path = File(keyFile.get()).toPath()
        val pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
        Files.createDirectories(path.parent)
        try {
            if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            } else {
                Files.createFile(path)
            }
        } catch (_: FileAlreadyExistsException) {
            // Replacing the key would strand every install that trusts it.
            throw GradleException("$path already exists, so nothing was changed.")
        }
        Files.writeString(path, Base64.getEncoder().encodeToString(pair.private.encoded))
        val publicKey = pair.public.encoded.takeLast(PUBLIC_KEY_BYTES).toByteArray()
        logger.lifecycle("Private key: $path. Back it up: releases can't be signed without it.")
        logger.lifecycle("Public key: ${Base64.getEncoder().encodeToString(publicKey)}")
    }
}

/** Writes `<artifact>.sig` beside every artifact the update manifests under [artifactsDir] list. */
@DisableCachingByDefault(because = "Signing is quick, and the key must stay out of the build cache")
abstract class SignUpdatesTask : DefaultTask() {
    @get:Internal
    abstract val artifactsDir: DirectoryProperty

    @get:Input
    abstract val keyFile: Property<String>

    @TaskAction
    fun sign() {
        val manifests = artifactsDir.get().asFile.walkTopDown()
            .filter { it.isFile && MANIFEST_NAME.matches(it.name) }
            .toList()
        if (manifests.isEmpty()) throw GradleException("No update manifests under ${artifactsDir.get().asFile}. Package the app first.")

        val keyPath = File(keyFile.get())
        if (!keyPath.isFile) throw GradleException("No signing key at $keyPath. See generateUpdateSigningKey.")
        val key = KeyFactory.getInstance(ALGORITHM)
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyPath.readText().trim())))

        manifests.forEach { manifest ->
            val text = manifest.readText()
            val version = MANIFEST_VERSION.find(text)?.groupValues?.get(1)
                ?: throw GradleException("$manifest has no version.")
            MANIFEST_FILE.findAll(text).map { it.groupValues[1] }.toSet().forEach { name ->
                val artifact = File(manifest.parentFile, name)
                if (!artifact.isFile) throw GradleException("$manifest lists $name, which isn't beside it.")
                val signature = Signature.getInstance(ALGORITHM).run {
                    initSign(key)
                    update(signatureMessage(version, artifact))
                    sign()
                }
                File(manifest.parentFile, "$name.sig").writeText(Base64.getEncoder().encodeToString(signature))
                logger.lifecycle("Signed $name as version $version")
            }
        }
    }
}
