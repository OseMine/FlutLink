package com.flutcloud.flutlink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flutcloud.flutlink.AppContainer

/** The [AppContainer] provided by the platform root composable. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap your UI in FlutLinkRoot")
}

/** Build a FlutLink ViewModel wired to the app container. */
@Composable
inline fun <reified VM : ViewModel> flutLinkViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}