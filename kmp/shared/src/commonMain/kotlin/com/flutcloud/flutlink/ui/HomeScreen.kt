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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.tab_admin
import com.flutcloud.flutlink.resources.tab_files
import com.flutcloud.flutlink.resources.tab_settings


private enum class Tab(val labelRes: StringResource, val icon: ImageVector) {
    Files(Res.string.tab_files, Icons.Default.Folder),
    Admin(Res.string.tab_admin, Icons.Default.AdminPanelSettings),
    Settings(Res.string.tab_settings, Icons.Default.Settings)
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

    // Impersonation hand-off: Admin tab picks a target user, Files tab consumes it.
    var impersonateTarget by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val visible = tab != Tab.Admin || isAdmin
                    if (visible) {
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                Tab.Files -> FilesScreen(
                    container = container,
                    impersonateTarget = impersonateTarget,
                    onImpersonationHandled = { impersonateTarget = null }
                )
                Tab.Admin -> AdminScreen(
                    container = container,
                    onViewFiles = { user ->
                        impersonateTarget = user.id
                        selectedTab = Tab.Files
                    }
                )
                Tab.Settings -> SettingsScreen(container, onLoggedOut)
            }
        }
    }
}