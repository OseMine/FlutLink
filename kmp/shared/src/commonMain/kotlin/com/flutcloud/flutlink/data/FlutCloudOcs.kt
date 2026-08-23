package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.data.dto.FlutCloudItemDto
import com.flutcloud.flutlink.data.dto.ManagedUser
import com.flutcloud.flutlink.data.dto.Share
import com.flutcloud.flutlink.data.dto.ShareDto
import com.flutcloud.flutlink.data.dto.UserDetailsDto
import io.ktor.http.HttpMethod
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * OCS Provisioning API, files-sharing API and FlutCloud-app endpoints as
 * extensions on [FlutCloudApi] (kept separate to keep each file focused;
 * mirrors the desktop `nextcloud/ocs.rs` surface).
 */

// --- OCS Provisioning API -----------------------------------------------

/**
 * Fetch a single page of users via the OCS `offset`/`limit` parameters.
 * The admin screen pages through large instances with "load more" instead
 * of fetching every user up front (mirrors the desktop AdminPanel).
 */
suspend fun FlutCloudApi.listUsersPage(
    session: AuthSession,
    search: String,
    offset: Int,
    limit: Int = 200
): List<String> {
    var url = "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users?format=json&limit=$limit&offset=$offset"
    if (search.isNotEmpty()) url += "&search=${encodeSegment(search)}"
    val data = execute(session, HttpMethod.Get, url) ?: return emptyList()
    return data.jsonObject["users"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        .orEmpty()
}

suspend fun FlutCloudApi.getUser(session: AuthSession, userId: String): ManagedUser {
    val data = execute(
        session, HttpMethod.Get,
        "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users/${encodeSegment(userId)}?format=json"
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

suspend fun FlutCloudApi.createUser(
    session: AuthSession,
    userId: String,
    password: String,
    displayName: String? = null
) {
    val form = mutableListOf("userid" to userId, "password" to password)
    if (!displayName.isNullOrEmpty()) form += "displayName" to displayName
    execute(session, HttpMethod.Post, "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users?format=json", form)
}

suspend fun FlutCloudApi.deleteUser(session: AuthSession, userId: String) {
    execute(
        session, HttpMethod.Delete,
        "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users/${encodeSegment(userId)}?format=json"
    )
}

suspend fun FlutCloudApi.updateUser(session: AuthSession, userId: String, key: String, value: String) {
    execute(
        session, HttpMethod.Put,
        "${session.normalizedBaseUrl}/ocs/v1.php/cloud/users/${encodeSegment(userId)}?format=json",
        listOf("key" to key, "value" to value)
    )
}

suspend fun FlutCloudApi.setUserQuota(session: AuthSession, userId: String, quotaBytes: Long?) {
    val value = quotaBytes?.toString() ?: "none"
    updateUser(session, userId, "quota", value)
}

// --- OCS groups API -----------------------------------------------------

/** List all groups, paging through `offset`/`limit` like [listUsersPage]. */
suspend fun FlutCloudApi.listGroups(session: AuthSession, search: String = ""): List<String> {
    val limit = 200
    val all = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var offset = 0
    while (true) {
        var url = "${session.normalizedBaseUrl}/ocs/v1.php/cloud/groups?format=json&limit=$limit&offset=$offset"
        if (search.isNotEmpty()) url += "&search=${encodeSegment(search)}"
        val data = execute(session, HttpMethod.Get, url) ?: break
        val groups = data.jsonObject["groups"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { seen.add(it) }
            .orEmpty()
        if (groups.isEmpty()) break
        all += groups
        if (groups.size < limit) break
        offset += limit
    }
    return all
}

suspend fun FlutCloudApi.createGroup(session: AuthSession, groupId: String) {
    execute(
        session, HttpMethod.Post,
        "${session.normalizedBaseUrl}/ocs/v1.php/cloud/groups?format=json",
        listOf("groupid" to groupId)
    )
}

suspend fun FlutCloudApi.addGroupMember(session: AuthSession, groupId: String, userId: String) {
    execute(
        session, HttpMethod.Post,
        "${session.normalizedBaseUrl}/ocs/v1.php/cloud/groups/${encodeSegment(groupId)}?format=json",
        listOf("userid" to userId)
    )
}

suspend fun FlutCloudApi.removeGroupMember(session: AuthSession, groupId: String, userId: String) {
    execute(
        session, HttpMethod.Delete,
        "${session.normalizedBaseUrl}/ocs/v1.php/cloud/groups/${encodeSegment(groupId)}/users/${encodeSegment(userId)}?format=json"
    )
}

// --- OCS files sharing API ----------------------------------------------

suspend fun FlutCloudApi.createShare(
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
        session, HttpMethod.Post,
        "${session.normalizedBaseUrl}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json",
        form
    ) ?: throw ApiException("Share endpoint returned no data")
    return json.decodeFromString<ShareDto>(data.toString()).toShare()
        ?: throw ApiException("Share endpoint returned no share data")
}

suspend fun FlutCloudApi.listShares(session: AuthSession, path: String? = null): List<Share> {
    var url = "${session.normalizedBaseUrl}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json"
    path?.let { url += "&path=${encodeSegment(it)}" }
    val data = execute(session, HttpMethod.Get, url) ?: return emptyList()
    return data.jsonArray.mapNotNull { json.decodeFromString<ShareDto>(it.toString()).toShare() }
}

suspend fun FlutCloudApi.deleteShare(session: AuthSession, shareId: Long) {
    execute(
        session, HttpMethod.Delete,
        "${session.normalizedBaseUrl}/ocs/v2.php/apps/files_sharing/api/v1/shares/$shareId?format=json"
    )
}

// --- FlutCloud app: virtual links + writable parts -----------------------

suspend fun FlutCloudApi.listLinks(session: AuthSession): List<FlutCloudItemDto> {
    val data = execute(
        session, HttpMethod.Get,
        "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/links"
    ) ?: return emptyList()
    return data.jsonArray.map { json.decodeFromString<FlutCloudItemDto>(it.toString()) }
}

suspend fun FlutCloudApi.createLink(session: AuthSession, name: String) {
    execute(
        session, HttpMethod.Post,
        "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/links",
        listOf("name" to name)
    )
}

suspend fun FlutCloudApi.deleteLink(session: AuthSession, name: String) {
    execute(
        session, HttpMethod.Delete,
        "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/links/${encodeSegment(name)}"
    )
}

suspend fun FlutCloudApi.listParts(session: AuthSession): List<FlutCloudItemDto> {
    val data = execute(
        session, HttpMethod.Get,
        "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/parts"
    ) ?: return emptyList()
    return data.jsonArray.map { json.decodeFromString<FlutCloudItemDto>(it.toString()) }
}

suspend fun FlutCloudApi.createPart(session: AuthSession, name: String) {
    execute(
        session, HttpMethod.Post,
        "${session.normalizedBaseUrl}/ocs/v2.php/apps/flutcloud/api/v1/parts",
        listOf("name" to name)
    )
}

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
