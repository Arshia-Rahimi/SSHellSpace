package com.sshellspace.core.common

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

private const val OWNER_FILE = "rw-------"
private const val OWNER_DIRECTORY = "rwx------"

/**
 * Creates a new empty file only its owner can read, failing if [path] exists. On POSIX systems the permissions are set
 * as part of creating it, so there is no moment when others could open the file.
 */
fun createOwnerOnlyFile(path: Path) {
    if (Files.getFileAttributeView(path.parent, PosixFileAttributeView::class.java) != null) {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_FILE)))
    } else {
        Files.createFile(path)
        restrictToOwner(path)
    }
}

/** Limits an existing file or directory to its owner: POSIX permissions, or an owner-only ACL on Windows. */
fun restrictToOwner(path: Path) {
    if (Files.getFileAttributeView(path, PosixFileAttributeView::class.java) != null) {
        val permissions = if (Files.isDirectory(path)) OWNER_DIRECTORY else OWNER_FILE
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
        return
    }
    val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
    view.acl = listOf(
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(view.owner)
            .setPermissions(AclEntryPermission.entries.toSet())
            .build(),
    )
}
