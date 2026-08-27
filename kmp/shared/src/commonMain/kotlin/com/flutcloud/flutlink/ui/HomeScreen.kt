package com.flutcloud.flutlink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Scaffold { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Desktop-style header with logo + tabs
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 0.dp
            ) {
                Column {
                    // Top row: Logo + app name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "FlutLink",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight(600)
                        )
                    }

                    // Tab row with underline indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Tab.entries.forEach { tab ->
                            val visible = tab != Tab.Admin || isAdmin
                            if (visible) {
                                val isSelected = selectedTab == tab
                                Column(
                                    modifier = Modifier
                                        .clickable { selectedTab = tab }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight(600) else FontWeight.Normal
                                        )
                                    }
                                    // Underline indicator
                                    Spacer(Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Tab content
            Box(Modifier.fillMaxSize()) {
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
}
