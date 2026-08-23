package com.flutcloud.flutlink.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Persisted account metadata (username, instance URL, flags) — no tokens. */
@Serializable
data class AccountMeta(
    @SerialName("username") val username: String,
    @SerialName("instanceUrl") val instanceUrl: String,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("isAdmin") val isAdmin: Boolean = false,
    @SerialName("isActive") val isActive: Boolean = false
) {
    val key: String get() = "$username@${instanceUrl.trimEnd('/')}"
}