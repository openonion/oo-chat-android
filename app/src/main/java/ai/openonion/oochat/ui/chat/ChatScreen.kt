package ai.openonion.oochat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.openonion.oochat.data.Message
import ai.openonion.oochat.data.Role
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var agentAddress by remember { mutableStateOf("") }
    var showConnectionDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Show error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // In production, show a proper snackbar
            viewModel.clearError()
        }
    }

    // Connection dialog
    if (showConnectionDialog) {
        AlertDialog(
            onDismissRequest = { showConnectionDialog = false },
            title = { Text("Connect to Agent") },
            text = {
                Column {
                    Text(
                        "Enter the agent address (e.g., my-agent@relay.connectonion.com)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = agentAddress,
                        onValueChange = { agentAddress = it },
                        label = { Text("Agent Address") },
                        placeholder = { Text("agent-name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.connectToAgent(agentAddress)
                        showConnectionDialog = false
                    }
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConnectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "OO Chat",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (uiState.isConnected) {
                                if (uiState.agentAddress.isNotEmpty()) {
                                    "Connected to ${uiState.agentAddress}"
                                } else {
                                    "Connected"
                                }
                            } else {
                                "Not connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (uiState.isConnected) {
                                viewModel.disconnect()
                            } else {
                                showConnectionDialog = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.isConnected) {
                                Icons.Default.LinkOff
                            } else {
                                Icons.Default.Link
                            },
                            contentDescription = if (uiState.isConnected) {
                                "Disconnect"
                            } else {
                                "Connect"
                            },
                            tint = if (uiState.isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }

                // Loading indicator
                if (uiState.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        maxLines = 4
                    )

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                if (!uiState.isConnected) {
                                    // Auto-connect if not connected
                                    viewModel.connectToAgent()
                                }
                                coroutineScope.launch {
                                    // Small delay to ensure connection is established
                                    kotlinx.coroutines.delay(100)
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == Role.USER
    val isSystem = message.role == Role.SYSTEM

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            else -> Arrangement.Start
        }
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                isSystem -> MaterialTheme.colorScheme.surfaceVariant
                isUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser && !isSystem) {
                    Text(
                        text = "Agent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = message.content,
                    color = when {
                        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
                        isUser -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    style = if (isSystem) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                )
            }
        }
    }
}
