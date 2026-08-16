package com.flutcloud.flutlink.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.ui.components.ErrorBanner
import com.flutcloud.flutlink.ui.components.ScrollableColumn
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(container: AppContainer, onLoggedIn: () -> Unit) {
    val vm = flutLinkViewModel { LoginViewModel(it) }
    val serverUrl by vm.serverUrl.collectAsState()
    val username by vm.username.collectAsState()
    val token by vm.token.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val step by vm.step.collectAsState()
    val registerMode by vm.registerMode.collectAsState()
    val displayName by vm.displayName.collectAsState()
    val adminUsername by vm.adminUsername.collectAsState()
    val adminPassword by vm.adminPassword.collectAsState()

    Scaffold { innerPadding ->
        ScrollableColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "FlutLink",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "FlutCloud client for Android",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                    .padding(4.dp)
            ) {
                ModeButton(
                    text = "Sign in",
                    selected = !registerMode,
                    onClick = { if (registerMode) vm.toggleMode() },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    text = "Register",
                    selected = registerMode,
                    onClick = { if (!registerMode) vm.toggleMode() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { vm.serverUrl.value = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://cloud.example.com") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { vm.username.value = it },
                label = { Text(if (registerMode) "New username" else "Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { vm.token.value = it },
                label = { Text(if (registerMode) "Password" else "App token / password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (registerMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { vm.displayName.value = it },
                    label = { Text("Display name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Admin credentials",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = adminUsername,
                    onValueChange = { vm.adminUsername.value = it },
                    label = { Text("Admin username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = { vm.adminPassword.value = it },
                    label = { Text("Admin password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "The admin account creates the new user via the server's provisioning API. The registration password becomes the app password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(error.orEmpty())
            }

            Spacer(Modifier.height(24.dp))
            if (loading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator()
                    step?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                Button(
                    onClick = {
                        if (registerMode) vm.register(onLoggedIn) else vm.signIn(onLoggedIn)
                    },
                    enabled = serverUrl.isNotBlank() && username.isNotBlank() && token.isNotBlank() &&
                        (!registerMode || (adminUsername.isNotBlank() && adminPassword.isNotBlank())),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (registerMode) "Register" else "Sign in")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Text(text)
    }
}