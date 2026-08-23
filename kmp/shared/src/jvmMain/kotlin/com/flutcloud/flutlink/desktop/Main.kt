package com.flutcloud.flutlink.desktop

import com.flutcloud.flutlink.data.AuthSession
import com.flutcloud.flutlink.data.FlutCloudApi
import com.flutcloud.flutlink.data.HttpClientFactory
import com.flutcloud.flutlink.data.WebDavApi
import kotlinx.coroutines.runBlocking

/**
 * Headless Desktop-JVM client — the first functional consumer of the shared
 * KMP network stack outside Android. It verifies a FlutCloud server, shows
 * the signed-in user and lists/downloads files via WebDAV.
 *
 * Credentials are read from the environment (never hard-coded):
 *
 *   FLUTCLOUD_URL, FLUTCLOUD_USERNAME, FLUTCLOUD_TOKEN
 *
 * Usage (from `kmp/`):  ./gradlew :shared:desktopCli [-- ls <path>]
 */
private const val USAGE = """
FlutLink desktop CLI

Environment:
  FLUTCLOUD_URL       FlutCloud server base URL (required)
  FLUTCLOUD_USERNAME  account name (required)
  FLUTCLOUD_TOKEN     app password / token (required)

Commands:
  whoami              verify the server and print the current user + quota
  ls [path]           list a folder via WebDAV (default: "/")
"""

fun main(args: Array<String>) = runBlocking {
    val url = env("FLUTCLOUD_URL")
    val username = env("FLUTCLOUD_USERNAME")
    val token = env("FLUTCLOUD_TOKEN")

    if (url == null || username == null || token == null) {
        System.err.println(USAGE.trimIndent())
        return@runBlocking
    }

    val session = AuthSession(url, username, token)
    val client = HttpClientFactory.create("FlutLink-Desktop/1.0.0")
    val ocsApi = FlutCloudApi(client)
    val webDavApi = WebDavApi(client)

    when (args.firstOrNull()) {
        "whoami", null -> {
            ocsApi.verifyServer(session)
            println("Server verified: FlutCloud capability present.")
            val user = ocsApi.getCurrentUser(session)
            println("User: ${user.id}" + (user.displayName?.let { " ($it)" } ?: ""))
            println("Admin: ${if (user.isAdmin) "yes" else "no"}")
            val quota = runCatching { ocsApi.getCurrentQuota(session) }.getOrNull()
            quota?.let { q ->
                val used = q.used?.let { humanBytes(it) } ?: "?"
                val total = q.total?.let { humanBytes(it) } ?: "unlimited"
                println("Quota: $used of $total" + (q.relative?.let { " (${it.toInt()}%)" } ?: ""))
            }
        }
        "ls" -> {
            val path = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "/"
            val entries = webDavApi.list(session, path)
            if (entries.isEmpty()) {
                println("(empty)")
            }
            for (entry in entries) {
                val marker = if (entry.isDir) "d" else "-"
                val flags = buildString {
                    append(marker)
                    if (entry.isResource) append("r")
                    if (entry.isPart) append("p")
                    if (entry.isVirtualLink) append("→${entry.linkTarget}")
                }
                val size = entry.size?.let { humanBytes(it).padStart(9) } ?: "".padStart(9)
                println("$flags $size  ${entry.name}")
            }
        }
        "--help", "-h", "help" -> println(USAGE.trimIndent())
        else -> {
            System.err.println("Unknown command: ${args[0]}")
            System.err.println(USAGE.trimIndent())
            kotlin.system.exitProcess(2)
        }
    }
}

private fun env(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024.0
        unit++
    }
    return "%.1f %s".format(java.util.Locale.ROOT, value, units[unit])
}
