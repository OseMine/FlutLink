package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.data.dto.AppInfoDto
import com.flutcloud.flutlink.data.dto.FlutCloudItemDto
import com.flutcloud.flutlink.data.dto.ManagedUser
import com.flutcloud.flutlink.data.dto.Quota
import com.flutcloud.flutlink.data.dto.QuotaDto
import com.flutcloud.flutlink.data.dto.SessionUser
import com.flutcloud.flutlink.data.dto.Share
import com.flutcloud.flutlink.data.dto.ShareDto
import com.flutcloud.flutlink.data.dto.UserDetailsDto
import com.flutcloud.flutlink.data.dto.UserInfoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * OCS + FlutCloud-app API client. All HTTP traffic runs on OkHttp; every
 * request is verified against the `flutcloud` capability before an account
 * is accepted (FlutCloud-only policy).
 */
class FlutCloudApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val tag = "FlutLinkOcs"

    /** Parse the OCS meta + return `(data, errorMessage)`. */
    private fun parseOcs(body: String): Pair<JsonElement?, String?> {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null to "Invalid OCS response"
        val meta = obj["ocs"]?.jsonObject?.get("meta")?.jsonObject
        val status = (meta?.get("status") as? JsonPrimitive)?.contentOrNull
        val code = (meta?.get("statuscode") as? JsonPrimitive)?.contentOrNull
        val message = (meta?.get("message") as? JsonPrimitive)?.contentOrNull
            ?: "Unknown OCS error"
        val ok = status.equals("ok", ignoreCase = true) || code == "100" || code == "200"
        return obj["ocs"]?.jsonObject?.get("data") to if (ok) null else message
    }

    private fun build(
        session: AuthSession,
        method: String,
        url: String,
        form: List<Pair<String, String>>? = null
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .method(method, form?.let { pairs ->
                FormBody.Builder().apply { pairs.forEach { (k, v) -> add(k, v) } }.build()
            })
            .header("Authorization", Credentials.basic(session.username, session.token))
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
        return builder.build()
    }

    /** Execute an OCS request; throws [ApiException] on HTTP/meta errors. */
    private suspend fun execute(
        session: AuthSession,
        method: String,
        url: String,
        form: List<Pair<String, String>>? = null
    ): JsonElement? = withContext(Dispatchers.IO) {
        val request = build(session, method, url, form)
        val started = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                android.util.Log.i(tag, "$method $url -> ${response.code} in ${System.currentTimeMillis() - started}ms body=${body.length}")
                if (response.code >= 400) {
                    throw ApiException(
                        "Server answered ${response.code}: $body".trim(),
                        "http_${response.code}",
                        response.code
                    )
                }
                val (data, error) = parseOcs(body)
                if (error != null) throw ApiException(error, "ocs_error", response.code)
                data
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    private fun String.encoded() = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    // --- FlutCloud-only verification --------------------------------------

    /**
     * Verify that the server runs the FlutCloud app. Queries the OCS
     * capabilities endpoint **with the account credentials** — Nextcloud only
     * includes app capabilities (`ocs.data.capabilities.flutcloud`) for
     * authenticated requests, so an anonymous probe would reject valid
     * servers. Mirrors the desktop client's `verify_server`.
     */
    suspend fun verifyServer(session: AuthSession) {
        val url = "${session.normalizedBaseUrl}/ocs/v2.php/cloud/capabilities?format=json"
        val data = execute(session, "GET", url) ?: throw FlutCloudAppMissing()
        val has = data.jsonObject["capabilities"]
            ?.jsonObject
            ?.containsKey("flutcloud") == true
        if (!has) throw FlutCloudAppMissing()
    }

    // --- Current user / session -------------------------------------------

    suspend fun getCurrentUser(session: AuthSession): SessionUser {
        val data = execute(session, "GET", "${session.normalizedBaseUrl}/ocs/v2.php/cloud/user?format=json")
            ?: throw ApiException("Missing user data")
        val user = json.decodeFromString<UserInfoDto>(data.toString())
        return SessionUser(
            id = user.id ?: throw ApiException("Missing user id"),
            displayName = user.displayName,
            isAdmin = user.isAdmin ?: false
        )
    }

    suspend fun getCurrentQuota(session: AuthSession): Quota? {
        val data = execute(session, "GET", "${session.normalizedBaseUrl}/ocs/v2.php/cloud/user?format=json")
            ?: return null
        return json.decodeFromString<UserInfoDto>(data.toString()).quota?.toQuota()
    }

    /**
     * Probe admin rights. OCS v1 answers HTTP 200 even for denied requests,
     * so the result is judged from the OCS meta (`statuscode`), not the HTTP
     * status. Returns `false` when the server answered and denied the request;
     * network/parse failures propagate so callers can keep the previously
     * stored flag instead of demoting an admin account on a transient error.
     */
    suspend fun isAdmin(session: AuthSession): Boolean = withContext(Dispatchers.IO) {
        val request = build(session, "GET", "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users?format=json&limit=1")
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code >= 400) {
                    throw ApiException(
                        "Server answered ${response.code}: $body".trim(),
                        "http_${response.code}",
                        response.code
                    )
                }
                val (_, error) = parseOcs(body)
                error == null
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    suspend fun ping(session: AuthSession): AppInfoDto? {
        val data = execute(session, "GET", "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/ping")
            ?: return null
        return json.decodeFromString<AppInfoDto>(data.toString())
    }

    // --- OCS Provisioning API ---------------------------------------------

    suspend fun listUsers(session: AuthSession, search: String = ""): List<String> {
        val limit = 200
        val all = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var offset = 0
        while (true) {
            var url = "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users?format=json&limit=$limit&offset=$offset"
            if (search.isNotEmpty()) url += "&search=${search.encoded()}"
            val data = execute(session, "GET", url) ?: break
            val users = data.jsonObject["users"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.filter { seen.add(it) }
                .orEmpty()
            if (users.isEmpty()) break
            all += users
            if (users.size < limit) break
            offset += limit
        }
        return all
    }

    suspend fun getUser(session: AuthSession, userId: String): ManagedUser {
        val data = execute(
            session, "GET",
            "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users/${userId.encoded()}?format=json"
        ) ?: throw ApiException("Missing user data")
        val dto = json.decodeFromString<UserDetailsDto>(data.toString())
        return ManagedUser(
            id = dto.id ?: userId,
            displayName = dto.displayName,
            email = dto.email,
            quota = dto.quota?.toQuota(),
            groups = dto.groups.orEmpty(),
            enabled = dto.enabled ?: true
        )
    }

    suspend fun createUser(session: AuthSession, userId: String, password: String, displayName: String? = null) {
        val form = mutableListOf("userid" to userId, "password" to password)
        if (!displayName.isNullOrEmpty()) form += "displayName" to displayName
        execute(session, "POST", "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users?format=json", form)
    }

    suspend fun deleteUser(session: AuthSession, userId: String) {
        execute(session, "DELETE", "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users/${userId.encoded()}?format=json")
    }

    suspend fun updateUser(session: AuthSession, userId: String, key: String, value: String) {
        execute(
            session, "PUT",
            "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users/${userId.encoded()}?format=json",
            listOf("key" to key, "value" to value)
        )
    }

    suspend fun setUserQuota(session: AuthSession, userId: String, quotaBytes: Long?) {
        val value = quotaBytes?.toString() ?: "none"
        updateUser(session, userId, "quota", value)
    }

    // --- OCS files sharing API --------------------------------------------

    suspend fun createShare(
        session: AuthSession,
        path: String,
        shareType: Int,
        shareWith: String? = null,
        password: String? = null,
        expireDate: String? = null,
        publicUpload: Boolean = false,
        permissions: Long? = null
    ): Share {
        val form = mutableListOf("path" to path, "shareType" to shareType.toString())
        shareWith?.let { form += "shareWith" to it }
        password?.let { form += "password" to it }
        expireDate?.let { form += "expireDate" to it }
        val perms = permissions ?: when {
            publicUpload -> 15
            shareType == 3 -> 1
            else -> null
        }
        perms?.let { form += "permissions" to it.toString() }
        val data = execute(
            session, "POST",
            "${session.normalizedBaseUrl}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json",
            form
        ) ?: throw ApiException("Share endpoint returned no data")
        return json.decodeFromString<ShareDto>(data.toString()).toShare()
            ?: throw ApiException("Share endpoint returned no share data")
    }

    suspend fun listShares(session: AuthSession, path: String? = null): List<Share> {
        var url = "${session.normalizedBaseUrl}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json"
        path?.let { url += "&path=${it.encoded()}" }
        val data = execute(session, "GET", url) ?: return emptyList()
        return data.jsonArray.mapNotNull { json.decodeFromString<ShareDto>(it.toString()).toShare() }
    }

    suspend fun deleteShare(session: AuthSession, shareId: Long) {
        execute(session, "DELETE", "${session.normalizedBaseUrl}/ocs/v2.php/apps/files_sharing/api/v1/shares/$shareId?format=json")
    }

    // --- FlutCloud app: virtual links + writable parts --------------------

    suspend fun listLinks(session: AuthSession): List<FlutCloudItemDto> {
        val data = execute(session, "GET", "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/links")
            ?: return emptyList()
        return data.jsonArray.map { json.decodeFromString<FlutCloudItemDto>(it.toString()) }
    }

    suspend fun createLink(session: AuthSession, name: String) {
        execute(
            session, "POST",
            "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/links",
            listOf("name" to name)
        )
    }

    suspend fun deleteLink(session: AuthSession, name: String) {
        execute(session, "DELETE", "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/links/${name.encoded()}")
    }

    suspend fun listParts(session: AuthSession): List<FlutCloudItemDto> {
        val data = execute(session, "GET", "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/parts")
            ?: return emptyList()
        return data.jsonArray.map { json.decodeFromString<FlutCloudItemDto>(it.toString()) }
    }

    suspend fun createPart(session: AuthSession, name: String) {
        execute(
            session, "POST",
            "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/parts",
            listOf("name" to name)
        )
    }
}

private fun QuotaDto.toQuota(): Quota = Quota(
    total = totalBytes,
    used = usedBytes,
    free = freeBytes,
    relative = relativePercent
)

private fun ShareDto.toShare(): Share? {
    val id = id ?: return null
    return Share(
        id = id,
        shareType = shareType?.toInt() ?: 0,
        path = path,
        shareWith = shareWith,
        shareWithDisplayName = shareWithDisplayName,
        permissions = permissions,
        url = url,
        hasPassword = hasPassword,
        expiration = expiration
    )
}