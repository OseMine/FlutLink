package com.flutcloud.flutlink.desktop

import com.flutcloud.flutlink.core.KeyValueStorage
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * File-backed [KeyValueStorage] for the Desktop-JVM client. Accounts metadata
 * lives in a normal properties file; the "secure" variant stores tokens in a
 * separate file with owner-only permissions under `$XDG_STATE_HOME/flutlink`
 * (defaulting to `~/.local/state/flutlink`), mirroring the desktop Rust client's
 * keyring/file split.
 *
 * Note: unlike the OS keyring this file is not encrypted at rest — a real
 * keyring integration (e.g. Android Keystore / macOS Keychain / Linux
 * Secret Service) is a follow-up task (L22-F3). The owner-only permission
 * restriction limits access to the current user.
 */
class FileKeyValueStorage(
    file: Path,
    private val secure: Boolean = false
) : KeyValueStorage {

    private val props = Properties()
    private val path: Path

    init {
        val base = if (secure) stateDir() else configDir()
        Files.createDirectories(base)
        path = base.resolve(file.fileName.toString())
        if (Files.exists(path)) {
            path.toFile().inputStream().use { props.load(it) }
        }
        if (secure) restrictOwnerPermissions(path)
    }

    override fun getString(key: String): String? = props.getProperty(key)

    override fun putString(key: String, value: String) {
        props.setProperty(key, value)
        persist()
    }

    override fun remove(key: String) {
        props.remove(key)
        persist()
    }

    private fun persist() {
        FileOutputStream(path.toFile()).use { out ->
            props.store(out, "FlutLink desktop client")
        }
    }

    private fun restrictOwnerPermissions(p: Path) {
        runCatching {
            val view = Files.getFileAttributeView(p, java.nio.file.attribute.PosixFileAttributeView::class.java)
            view?.setPermissions(java.util.HashSet(listOf(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
            )))
        }
    }

    companion object {
        private fun xdgHome(name: String, fallback: String): Path {
            val env = System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home") + "/" + fallback
            return Path.of(env)
        }

        /** `$XDG_CONFIG_HOME/flutlink`, defaulting to `~/.config/flutlink`. */
        fun configDir(): Path = xdgHome("XDG_CONFIG_HOME", ".config").resolve("flutlink")

        /** `$XDG_STATE_HOME/flutlink`, defaulting to `~/.local/state/flutlink`. */
        fun stateDir(): Path = xdgHome("XDG_STATE_HOME", ".local/state").resolve("flutlink")
    }
}
