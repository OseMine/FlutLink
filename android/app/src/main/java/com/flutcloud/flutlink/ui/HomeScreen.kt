package com.flutcloud.flutlink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.ui.admin.AdminScreen
import com.flutcloud.flutlink.ui.files.FilesScreen
import com.flutcloud.flutlink.ui.settings.SettingsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Files("Files", Icons.Default.Folder),
    Admin("Admin", Icons.Default.AdminPanelSettings),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun HomeScreen(container: AppContainer, onLoggedOut: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Files) }
    val session by container.sessionManager.session.collectAsState()
    val accounts by container.sessionManager.accounts.collectAsState()
    val isAdmin = session?.let {
        accounts.firstOrNull { a ->
            a.username == it.username && a.instanceUrl.trimEnd('/') == it.baseUrl.trimEnd('/')
        }?.isAdmin
    } ?: false

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val visible = tab != Tab.Admin || isAdmin
                    if (visible) {
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                Tab.Files -> FilesScreen(container)
                Tab.Admin -> AdminScreen(container)
                Tab.Settings -> SettingsScreen(container, onLoggedOut)
            }
        }
    }
}