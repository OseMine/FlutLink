package com.flutcloud.flutlink.ui

import android.content.Context
import androidx.annotation.StringRes
import com.flutcloud.flutlink.R
import com.flutcloud.flutlink.data.ApiException

/**
 * A user-facing message that is resolved at render time so the device language
 * (Android resource qualifier) is honoured. ViewModels emit resource IDs +
 * format args instead of hardcoded English strings.
 */
class UiMessage(
    @param:StringRes val resId: Int,
    vararg val args: Any
) {
    fun resolve(context: Context): String =
        if (args.isEmpty()) context.getString(resId)
        else context.getString(resId, *args)
}

/** Network failure message; keeps the underlying cause when available. */
fun networkUiMessage(cause: Throwable?): UiMessage {
    val detail = cause?.message
    return if (detail.isNullOrBlank()) UiMessage(R.string.error_network_reach)
    else UiMessage(R.string.error_network_reach_detail, detail)
}

/** Map an API-layer exception to a localized message by its code. */
fun ApiException.toUiMessage(): UiMessage = when {
    code == "flutcloud_app_missing" -> UiMessage(R.string.error_flutcloud_app_missing)
    code == "not_flutcloud" -> UiMessage(R.string.error_not_flutcloud, message)
    code == "target_exists" -> UiMessage(R.string.error_target_exists, message)
    code == "ocs_error" -> UiMessage(R.string.error_ocs, message)
    code.startsWith("http_") -> UiMessage(R.string.error_http, message)
    else -> UiMessage(R.string.error_api, message)
}

/** Fallback for unexpected exceptions. */
fun unexpectedUiMessage(message: String?): UiMessage =
    if (message.isNullOrBlank()) UiMessage(R.string.error_generic)
    else UiMessage(R.string.error_generic_detail, message)