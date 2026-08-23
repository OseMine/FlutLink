package com.flutcloud.flutlink.ui

import androidx.compose.runtime.Composable
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * A user-facing message that is resolved at render time so the device
 * language is honoured. ViewModels emit resource references + format args
 * instead of hardcoded strings.
 */
class UiMessage(
    val resource: org.jetbrains.compose.resources.StringResource,
    vararg val args: Any
) {
    /** Resolve inside composables. */
    @Composable
    fun resolve(): String =
        if (args.isEmpty()) stringResource(resource) else stringResource(resource, *args)

    /** Resolve inside coroutines (snackbar effects etc.). */
    suspend fun resolveSuspend(): String =
        if (args.isEmpty()) org.jetbrains.compose.resources.getString(resource)
        else org.jetbrains.compose.resources.getString(resource, *args)
}

/** Network failure message; keeps the underlying cause when available. */
fun networkUiMessage(cause: Throwable?): UiMessage {
    val detail = cause?.message
    return if (detail.isNullOrBlank()) UiMessage(Res.string.error_network_reach)
    else UiMessage(Res.string.error_network_reach_detail, detail)
}

/** Map an API-layer exception to a localized message by its code. */
fun ApiException.toUiMessage(): UiMessage = when {
    code == "flutcloud_app_missing" -> UiMessage(Res.string.error_flutcloud_app_missing)
    code == "not_flutcloud" -> UiMessage(Res.string.error_not_flutcloud, message)
    code == "target_exists" -> UiMessage(Res.string.error_target_exists, message)
    code == "ocs_error" -> UiMessage(Res.string.error_ocs, message)
    code.startsWith("http_") -> UiMessage(Res.string.error_http, message)
    else -> UiMessage(Res.string.error_api, message)
}

/** Fallback for unexpected exceptions. */
fun unexpectedUiMessage(message: String?): UiMessage =
    if (message.isNullOrBlank()) UiMessage(Res.string.error_generic)
    else UiMessage(Res.string.error_generic_detail, message)
