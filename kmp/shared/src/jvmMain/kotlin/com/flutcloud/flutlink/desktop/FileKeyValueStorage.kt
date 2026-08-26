package com.flutcloud.flutlink.desktop

import com.flutcloud.flutlink.core.KeyValueStorage
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * File-backed [KeyValueStorage] for the Desktop-JVM client. Accounts metadata
 * lives in a normal properties file; the "secure" variant encrypts the entire
 * file content with AES-256-GCM and restricts file permissions to owner-only
 * under `$XDG_STATE_HOME/flutlink` (defaulting to `~/.local/state/flutlink`),
 * mirroring the desktop Rust client's keyring/file split.
 *
 * The encryption key is derived from machine-specific info (hostname + user)
 * via SHA-256.  This is not equivalent to OS keyring protection but
 * significantly better than plaintext — an attacker needs both file access
 * AND machine identity to decrypt.  A proper keyring integration (e.g.
 * Android Keystore / macOS Keychain / Linux Secret Service) remains a
 * follow-up task (L22-F3).
 *
 * Encrypted file format (ASCII):
 * ```
 * Salted:<hex(nonce)><base64(ciphertext)>
 * ```
 * The ciphertext includes the 16-byte GCM auth tag appended by the JCE.
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
            if (secure) {
                loadEncrypted()
            } else {
                path.toFile().inputStream().use { props.load(it) }
            }
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
        if (secure) {
            saveEncrypted()
        } else {
            FileOutputStream(path.toFile()).use { out ->
                props.store(out, "FlutLink desktop client")
            }
        }
    }

    // --- AES-256-GCM encryption for secure mode ---

    private fun deriveKey(): SecretKey {
        val hostname = java.net.InetAddress.getLocalHost().hostName
        val username = System.getProperty("user.name")
        // Use PBKDF2-HMAC-SHA256 with a fixed but hostname-specific salt
        // instead of raw SHA-256 to resist brute-force / dictionary attacks.
        val salt = "flutlink-salt:$hostname:$username".toByteArray()
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec("flutlink-credential-encryption".toCharArray(), salt, 310_000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun saveEncrypted() {
        val plaintext = ByteArrayOutputStream()
        props.store(plaintext, null)
        val key = deriveKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        val hexNonce = nonce.joinToString("") { "%02x".format(it) }
        val b64ct = java.util.Base64.getEncoder().encodeToString(ciphertext)
        val payload = "Salted:$hexNonce$b64ct"
        // Atomic write: write to temp, then move.
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(tmp, payload)
        Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    private fun loadEncrypted() {
        val raw = Files.readString(path).trim()
        if (!raw.startsWith("Salted:")) {
            // Legacy plaintext file — log and migrate to encrypted on next persist.
            System.err.println(
                "warn: loading legacy plaintext credentials from $path — will be encrypted on next save"
            )
            path.toFile().inputStream().use { props.load(it) }
            return
        }
        val rest = raw.removePrefix("Salted:")
        val hexNonce = rest.substring(0, 24) // 12 bytes = 24 hex chars
        val b64ct = rest.substring(24)
        val nonce = hexNonce.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val ciphertext = java.util.Base64.getDecoder().decode(b64ct)
        val key = deriveKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val plaintext = cipher.doFinal(ciphertext)
        plaintext.inputStream().use { props.load(it) }
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
