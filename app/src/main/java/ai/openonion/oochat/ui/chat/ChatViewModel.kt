package ai.openonion.oochat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.openonion.oochat.data.Message
import ai.openonion.oochat.data.Role
import ai.openonion.oochat.network.AgentConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val agentAddress: String = ""
)

class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var connection: AgentConnection? = null

    init {
        // Add welcome message
        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    Message(
                        role = Role.SYSTEM,
                        content = "Welcome to OO Chat! Enter an agent address to connect, or start chatting with the default agent."
                    )
                )
            )
        }
    }

    fun connectToAgent(address: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, agentAddress = address) }

            connection?.disconnect()
            connection = AgentConnection()

            connection?.let { conn ->
                // Collect events from the connection
                launch {
                    conn.events.collect { event ->
                        when (event) {
                            is AgentConnection.ConnectionEvent.Connected -> {
                                _uiState.update { state ->
                                    state.copy(
                                        isConnected = true,
                                        isLoading = false,
                                        messages = state.messages + Message(
                                            role = Role.SYSTEM,
                                            content = if (address.isNotEmpty()) {
                                                "Connected to agent: $address"
                                            } else {
                                                "Connected to default agent"
                                            }
                                        )
                                    )
                                }
                            }

                            is AgentConnection.ConnectionEvent.MessageReceived -> {
                                event.response.content?.let { content ->
                                    _uiState.update { state ->
                                        state.copy(
                                            isLoading = false,
                                            messages = state.messages + Message(
                                                role = Role.ASSISTANT,
                                                content = content
                                            )
                                        )
                                    }
                                }
                                event.response.error?.let { error ->
                                    _uiState.update { it.copy(error = error, isLoading = false) }
                                }
                            }

                            is AgentConnection.ConnectionEvent.Error -> {
                                _uiState.update {
                                    it.copy(
                                        error = event.message,
                                        isLoading = false,
                                        isConnected = false
                                    )
                                }
                            }

                            is AgentConnection.ConnectionEvent.Disconnected -> {
                                _uiState.update {
                                    it.copy(
                                        isConnected = false,
                                        isLoading = false
                                    )
                                }
                            }
                        }
                    }
                }

                conn.connect(address.ifEmpty { null })
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // Add user message to the list
        val userMessage = Message(role = Role.USER, content = content)
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                isLoading = true
            )
        }

        // Send to agent
        connection?.sendMessage(content)
    }

    fun disconnect() {
        connection?.disconnect()
        _uiState.update {
            it.copy(
                isConnected = false,
                messages = it.messages + Message(
                    role = Role.SYSTEM,
                    content = "Disconnected from agent"
                )
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        connection?.disconnect()
    }
}
