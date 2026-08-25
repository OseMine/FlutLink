package com.flutcloud.flutlink.ui.guest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.ui.components.ErrorBanner
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.format.formatBytes
import com.flutcloud.flutlink.ui.viewmodel.GuestViewModel
import org.jetbrains.compose.resources.stringResource
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.guest_all
import com.flutcloud.flutlink.resources.guest_empty
import com.flutcloud.flutlink.resources.guest_read_only_hint
import com.flutcloud.flutlink.resources.guest_title
import com.flutcloud.flutlink.resources.retry
import com.flutcloud.flutlink.resources.sign_in

/**
 * Guest mode: bundled, strictly read-only view of every completely public
 * shared folder — no login required (desktop `GuestBrowser.vue` parity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onSignIn: () -> Unit
) {
    val vm = flutLinkViewModel { GuestViewModel(it) }
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val shares by vm.shares.collectAsState()
    val activeShare by vm.activeShare.collectAsState()
    val path by vm.path.collectAsState()
    val entries by vm.entries.collectAsState()
    val toast by vm.toast.collectAsState()

    var categoryFilter by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it.resolveSuspend())
            vm.consumeToast()
        }
    }

    val categories = remember(shares) { shares.mapNotNull { it.category }.distinct() }
    val visibleShares = remember(shares, categoryFilter) {
        if (categoryFilter == null) shares else shares.filter { it.category == categoryFilter }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        activeShare?.name ?: stringResource(Res.string.guest_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (activeShare != null) vm.leave() else onExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onSignIn) { Text(stringResource(Res.string.sign_in)) }
                }
            )

            Text(
                stringResource(Res.string.guest_read_only_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            error?.let { err ->
                ErrorBanner(err.resolve(), Modifier.padding(horizontal = 16.dp))
                if (!loading && shares.isEmpty()) {
                    TextButton(onClick = { vm.load() }, modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                activeShare == null -> Column(Modifier.fillMaxSize()) {
                    // Category filter ("alle an einem Ort" + /public/<kategorie>).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { categoryFilter = null },
                            label = { Text(stringResource(Res.string.guest_all)) }
                        )
                        for (category in categories) {
                            AssistChip(
                                onClick = { categoryFilter = category },
                                label = { Text(category) }
                            )
                        }
                    }

                    if (visibleShares.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.guest_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleShares, key = { it.token }) { share ->
                                Card(modifier = Modifier.fillMaxWidth().clickable { vm.enter(share) }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(share.name, style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                share.ownerDisplay ?: share.owner,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        share.category?.let {
                                            Spacer(Modifier.width(8.dp))
                                            AssistChip(onClick = {}, label = { Text(it) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(entries, key = { it.path }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDir) vm.navigateTo(entry.path)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!entry.isDir) {
                                    Text(
                                        formatBytes(entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!entry.isDir) {
                                IconButton(onClick = { vm.download(entry) }) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                }
                            } else {
                                Spacer(Modifier.width(48.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
