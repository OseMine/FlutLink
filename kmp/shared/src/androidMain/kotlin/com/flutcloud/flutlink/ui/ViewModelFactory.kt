package com.flutcloud.flutlink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.FlutLinkApplication

/** The [AppContainer] from the Application for the current composition. */
@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as FlutLinkApplication).container

/** Build a FlutLink ViewModel wired to the app container. */
@Composable
inline fun <reified VM : ViewModel> flutLinkViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = rememberAppContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}