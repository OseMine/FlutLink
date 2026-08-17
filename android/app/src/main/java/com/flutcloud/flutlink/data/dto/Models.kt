package com.flutcloud.flutlink.data.dto

import com.flutcloud.flutlink.data.asDoubleOrNull
import com.flutcloud.flutlink.data.asLongOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull

/** User info from `GET /ocs/v2.php/cloud/user`. */
@Serializable
data class UserInfoDto(
    val id: String? = null,
    @SerialName("display-name") val displayName: String? = null,
    @SerialName("isAdmin") val isAdmin: Boolean? = null,
    val quota: QuotaDto? = null,
    val email: String? = null
)

/** Storage quota, fields may arrive as numbers or strings. */
@Serializable
data class QuotaDto(
    val total: kotlinx.serialization.json.JsonElement? = null,
    val used: kotlinx.serialization.json.JsonElement? = null,
    val free: kotlinx.serialization.json.JsonElement? = null,
    val relative: kotlinx.serialization.json.JsonElement? = null
) {
    val totalBytes: Long? get() = total?.asLongOrNull()
    val usedBytes: Long? get() = used?.asLongOrNull()
    val freeBytes: Long? get() = free?.asLongOrNull()
    val relativePercent: Double? get() = relative?.asDoubleOrNull()
}

/** User details from `GET /ocs/v1.php/cloud/users/{id}`. */
@Serializable
data class UserDetailsDto(
    val id: String? = null,
    @SerialName("display-name") val displayName: String? = null,
    val email: String? = null,
    val quota: QuotaDto? = null,
    val groups: List<String>? = null,
    val enabled: Boolean? = null
)

/** Share payload from the OCS files sharing API. */
@Serializable
data class ShareDto(
    val id: Long? = null,
    @SerialName("share_type") val shareType: Long? = null,
    val path: String? = null,
    @SerialName("share_with") val shareWith: String? = null,
    @SerialName("share_with_displayname") val shareWithDisplayName: String? = null,
    val permissions: Long? = null,
    val url: String? = null,
    val password: kotlinx.serialization.json.JsonElement? = null,
    val expiration: String? = null
) {
    val hasPassword: Boolean?
        get() = when (password) {
            null -> null
            is kotlinx.serialization.json.JsonNull -> false
            is kotlinx.serialization.json.JsonPrimitive ->
                password.let { if (it.isString) it.contentOrNull?.isNotEmpty() ?: false else false }
            else -> null
        }
}

/** FlutCloud app info from `GET /apps/flutcloud/api/v1/ping`. */
@Serializable
data class AppInfoDto(
    val app: String? = null,
    val name: String? = null,
    val version: String? = null,
    val features: List<String>? = null,
    val user: String? = null
)

/** Virtual link / writable part entry from the FlutCloud app API. */
@Serializable
data class FlutCloudItemDto(
    val name: String? = null,
    val path: String? = null,
    val readOnly: Boolean? = null
)

/** Raw OCS capabilities payload (public endpoint). */
@Serializable
data class CapabilitiesDto(
    val capabilities: CapabilitiesData? = null
)

@Serializable
data class CapabilitiesData(
    val flutcloud: kotlinx.serialization.json.JsonElement? = null
)

/** Domain model mirroring the desktop client's `Quota`. */
data class Quota(
    val total: Long? = null,
    val used: Long? = null,
    val free: Long? = null,
    val relative: Double? = null
)

/** A folder listing entry, mirrors the desktop `WebDavEntry`. */
@Serializable
data class WebDavEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long? = null,
    val mtime: String? = null,
    val etag: String? = null,
    val contentType: String? = null,
    val isResource: Boolean = false,
    val isPart: Boolean = false,
    val linkTarget: String? = null,
    val pairedPath: String? = null
) {
    val isVirtualLink: Boolean get() = linkTarget != null
}

/** A share (public link or user/group share). */
data class Share(
    val id: Long,
    val shareType: Int,
    val path: String? = null,
    val shareWith: String? = null,
    val shareWithDisplayName: String? = null,
    val permissions: Long? = null,
    val url: String? = null,
    val hasPassword: Boolean? = null,
    val expiration: String? = null
)

/** Parsed user info for the signed-in account. */
data class SessionUser(
    val id: String,
    val displayName: String?,
    val isAdmin: Boolean
)

/** Full user record for the admin panel. */
data class ManagedUser(
    val id: String,
    val displayName: String?,
    val email: String?,
    val quota: Quota?,
    val groups: List<String>,
    val enabled: Boolean
)