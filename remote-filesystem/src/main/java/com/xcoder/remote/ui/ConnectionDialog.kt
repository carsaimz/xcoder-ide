package com.xcoder.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xcoder.remote.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDialog(
    editingConnection: RemoteConnectionInfo? = null,
    onDismiss: () -> Unit,
    onSave: (RemoteConnectionInfo) -> Unit,
    onTest: ((RemoteConnectionInfo) -> Unit)? = null
) {
    var nickname by remember(editingConnection) {
        mutableStateOf(editingConnection?.nickname ?: "")
    }
    var protocol by remember(editingConnection) {
        mutableStateOf(editingConnection?.protocol ?: ConnectionProtocol.SFTP)
    }
    var host by remember(editingConnection) {
        mutableStateOf(editingConnection?.host ?: "")
    }
    var port by remember(editingConnection) {
        mutableStateOf(editingConnection?.port?.toString() ?: protocol.defaultPort.toString())
    }
    var username by remember(editingConnection) {
        mutableStateOf(editingConnection?.username ?: "")
    }
    var password by remember { mutableStateOf("") }
    var authMethod by remember(editingConnection) {
        mutableStateOf(editingConnection?.authMethod ?: AuthMethod.PASSWORD)
    }
    var initialPath by remember(editingConnection) {
        mutableStateOf(editingConnection?.initialPath ?: "/")
    }
    var showPassword by remember { mutableStateOf(false) }
    var isAnonymous by remember { mutableStateOf(false) }

    // Auto-set port when protocol changes
    LaunchedEffect(protocol) {
        if (editingConnection == null || editingConnection.protocol != protocol) {
            port = protocol.defaultPort.toString()
        }
        if (protocol == ConnectionProtocol.SFTP && authMethod == AuthMethod.ANONYMOUS) {
            authMethod = AuthMethod.PASSWORD
        }
    }

    val isFormValid = remember(nickname, host, username, protocol) {
        nickname.isNotBlank() && host.isNotBlank() &&
                (isAnonymous || username.isNotBlank())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editingConnection != null) "Edit Connection" else "New Connection")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Nickname
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Name") },
                    placeholder = { Text("My Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Star, null) }
                )

                // Protocol selector
                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = { }
                ) {
                    OutlinedTextField(
                        value = protocol.name,
                        onValueChange = { },
                        label = { Text("Protocol") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = false, onDismissRequest = { }) { }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Host
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.weight(3f),
                        leadingIcon = { Icon(Icons.Default.Language, null) }
                    )
                    // Port
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Auth method selector
                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = { }
                ) {
                    OutlinedTextField(
                        value = authMethod.displayName,
                        onValueChange = { },
                        label = { Text("Authentication") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = false, onDismissRequest = { }) { }
                }

                // Username
                if (!isAnonymous) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, null) }
                    )
                }

                // Password
                if (!isAnonymous && authMethod != AuthMethod.PUBLIC_KEY) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) }
                    )
                }

                // Anonymous toggle (FTP only)
                if (protocol == ConnectionProtocol.FTP) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Anonymous login")
                    }
                }

                // Initial path
                OutlinedTextField(
                    value = initialPath,
                    onValueChange = { initialPath = it },
                    label = { Text("Initial Path") },
                    placeholder = { Text("/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Folder, null) }
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onTest != null) {
                    TextButton(
                        onClick = {
                            val info = buildConnectionInfo(
                                editingConnection, nickname, protocol, host, port,
                                username, password, authMethod, initialPath, isAnonymous
                            )
                            onTest(info)
                        },
                        enabled = isFormValid
                    ) {
                        Text("Test")
                    }
                }
                Button(
                    onClick = {
                        val info = buildConnectionInfo(
                            editingConnection, nickname, protocol, host, port,
                            username, password, authMethod, initialPath, isAnonymous
                        )
                        onSave(info)
                    },
                    enabled = isFormValid
                ) {
                    Text(if (editingConnection != null) "Save" else "Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun buildConnectionInfo(
    editing: RemoteConnectionInfo?,
    nickname: String,
    protocol: ConnectionProtocol,
    host: String,
    port: String,
    username: String,
    password: String,
    authMethod: AuthMethod,
    initialPath: String,
    isAnonymous: Boolean
): RemoteConnectionInfo {
    val id = editing?.id ?: RemoteConnectionInfo.generateId()
    val resolvedAuth = if (isAnonymous) AuthMethod.ANONYMOUS else authMethod
    val resolvedUsername = if (isAnonymous) "anonymous" else username
    return RemoteConnectionInfo(
        id = id,
        nickname = nickname,
        protocol = protocol,
        host = host,
        port = port.toIntOrNull() ?: protocol.defaultPort,
        username = resolvedUsername,
        encryptedPassword = password,
        authMethod = resolvedAuth,
        initialPath = if (initialPath.isBlank()) "/" else initialPath,
        createdAt = editing?.createdAt ?: System.currentTimeMillis(),
        lastConnectedAt = editing?.lastConnectedAt ?: 0L,
        connectCount = editing?.connectCount ?: 0,
        isFavorite = editing?.isFavorite ?: false
    )
}

private val AuthMethod.displayName: String
    get() = when (this) {
        AuthMethod.PASSWORD -> "Password"
        AuthMethod.PUBLIC_KEY -> "Public Key"
        AuthMethod.KEYBOARD_INTERACTIVE -> "Keyboard Interactive"
        AuthMethod.ANONYMOUS -> "Anonymous"
    }
