package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.data.dto.AppInfoDto
import com.flutcloud.flutlink.data.dto.SessionUser
import com.flutcloud.flutlink.data.dto.Quota
import com.flutcloud.flutlink.data.dto.QuotaDto
import com.flutcloud.flutlink.data.dto.UserInfoDto
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * OCS + FlutCloud-app API client. All HTTP traffic runs through Ktor; every
 * request is verified against the `flutcloud` capability before an account
 * is accepted (FlutCloud-only policy). Admin/share/link endpoints live in
 * `FlutCloudOcs.kt` as extensions on this class.
 */
class FlutCloudApi(private val client: HttpClient) {

    internal val json = Json { ignoreUnknownKeys = true }
    private val tag = "FlutLinkOcs"

    /** Parse the OCS meta + return `(data, errorMessage)`. */
    internal fun parseOcs(body: String): Pair<JsonElement?, String?> {
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

    /** Execute an OCS request; throws [ApiException] on HTTP/meta errors.
     *  Internal so the endpoint extensions in `FlutCloudOcs.kt` can reuse it. */
    internal suspend fun execute(
        session: AuthSession,
        method: HttpMethod,
        url: String,
        form: List<Pair<String, String>>? = null
    ): JsonElement? {
        val started = TimeSource.Monotonic.markNow()
        try {
            val response = client.request(url) {
                this.method = method
                header(HttpHeaders.Authorization, basicAuth(session.username, session.token))
                header(HttpHeaders.Accept, "application/json")
                if (url.contains("/ocs/")) header("OCS-APIRequest", "true")
                if (form != null) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(form.formUrlEncode())
                }
            }
            val body = response.bodyAsText()
            flutLog(
                tag,
                "$method $url -> ${response.status.value} in ${started.elapsedNow().inWholeMilliseconds}ms body=${body.length}"
            )

            val (data, ocsError) = parseOcs(body)

            if (response.status.value >= 400) {
                throw ApiException(
                    ocsError ?: "Server answered ${response.status.value}: $body".trim(),
                    "http_${response.status.value}",
                    response.status.value
                )
            }

            if (ocsError != null) throw ApiException(ocsError, "ocs_error", response.status.value)
            return data
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(e)
        }
    }

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
        val data = execute(session, HttpMethod.Get, url) ?: throw FlutCloudAppMissing()
        val has = data.jsonObject["capabilities"]
            ?.jsonObject
            ?.containsKey("flutcloud") == true
        if (!has) throw FlutCloudAppMissing()
    }

    // --- Current user / session -------------------------------------------

    suspend fun getCurrentUser(session: AuthSession): SessionUser {
        val data = execute(session, HttpMethod.Get, "${session.normalizedBaseUrl}/ocs/v2.php/cloud/user?format=json")
            ?: throw ApiException("Missing user data")
        val user = json.decodeFromString<UserInfoDto>(data.toString())
        return SessionUser(
            id = user.id ?: throw ApiException("Missing user id"),
            displayName = user.displayName,
            isAdmin = user.isAdmin ?: false
        )
    }

    suspend fun getCurrentQuota(session: AuthSession): Quota? {
        val data = execute(session, HttpMethod.Get, "${session.normalizedBaseUrl}/ocs/v2.php/cloud/user?format=json")
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
    suspend fun isAdmin(session: AuthSession): Boolean {
        val url = "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users?format=json&limit=1"
        return try {
            execute(session, HttpMethod.Get, url)
            true
        } catch (e: ApiException) {
            // If it's a 401/403 or an OCS "failure", they are not an admin.
            if (e.statusCode == 401 || e.statusCode == 403 || e.code == "ocs_error") {
                false
            } else {
                throw e
            }
        }
    }

    suspend fun ping(session: AuthSession): AppInfoDto? {
        val data = execute(session, HttpMethod.Get, "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/ping")
            ?: return null
        return json.decodeFromString<AppInfoDto>(data.toString())
    }
}

internal fun QuotaDto.toQuota(): Quota = Quota(
    total = totalBytes,
    used = usedBytes,
    free = freeBytes,
    relative = relativePercent
)
