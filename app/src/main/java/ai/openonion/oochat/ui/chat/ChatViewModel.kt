package ai.openonion.oochat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.openonion.oochat.crypto.KeyManager
import ai.openonion.oochat.data.*
import ai.openonion.oochat.network.AgentConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val chatItems: List<ChatItem> = emptyList(),
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isWaiting: Boolean = false,  // Agent needs user input
    val error: String? = null,
    val agentAddress: String = "",
    val myAddress: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val keyManager = KeyManager(application)
    private var connection: AgentConnection? = null
    private var currentAgentAddress: String = ""
    private var itemIdCounter = 0

    init {
        // Load or generate keys on startup
        val keys = keyManager.loadOrGenerate()
        _uiState.update { it.copy(myAddress = keys.shortAddress) }
    }

    /**
     * Connect to an agent by address.
     *
     * @param address Agent's public address (0x...) or short address
     * @param directUrl Optional direct URL for deployed agents
     */
    fun connectToAgent(address: String, directUrl: String? = null) {
        if (address.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, agentAddress = address) }
            currentAgentAddress = address

            // Disconnect existing connection
            connection?.disconnect()
            connection = AgentConnection(keyManager)

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
                                        myAddress = event.address.take(6) + "..." + event.address.takeLast(4)
                                    )
                                }
                            }

                            is AgentConnection.ConnectionEvent.ChatItemReceived -> {
                                _uiState.update { state ->
                                    state.copy(
                                        chatItems = state.chatItems + event.item,
                                        isLoading = when (event.item) {
                                            is ChatItem.Agent -> false
                                            else -> state.isLoading
                                        }
                                    )
                                }
                            }

                            is AgentConnection.ConnectionEvent.ChatItemUpdated -> {
                                _uiState.update { state ->
                                    val updatedItems = state.chatItems.map { item ->
                                        if (item.id == event.item.id) event.item else item
                                    }
                                    state.copy(chatItems = updatedItems)
                                }
                            }

                            is AgentConnection.ConnectionEvent.OutputReceived -> {
                                _uiState.update { state ->
                                    // Add agent response if not already present
                                    val hasResponse = state.chatItems.any {
                                        it is ChatItem.Agent && it.content == event.result
                                    }
                                    val items = if (!hasResponse && event.result.isNotEmpty()) {
                                        state.chatItems + ChatItem.Agent(
                                            id = (++itemIdCounter).toString(),
                                            content = event.result
                                        )
                                    } else {
                                        state.chatItems
                                    }
                                    state.copy(
                                        chatItems = items,
                                        isLoading = false,
                                        isWaiting = false
                                    )
                                }
                            }

                            is AgentConnection.ConnectionEvent.Waiting -> {
                                _uiState.update { state ->
                                    state.copy(isWaiting = true, isLoading = false)
                                }
                            }

                            is AgentConnection.ConnectionEvent.Error -> {
                                _uiState.update { state ->
                                    state.copy(
                                        error = event.message,
                                        isLoading = false,
                                        isConnected = false
                                    )
                                }
                            }

                            is AgentConnection.ConnectionEvent.Disconnected -> {
                                _uiState.update { state ->
                                    state.copy(
                                        isConnected = false,
                                        isLoading = false
                                    )
                                }
                            }
                        }
                    }
                }

                conn.connect(address, directUrl)
            }
        }
    }

    /**
     * Send a message to the connected agent.
     */
    fun sendMessage(content: String) {
        if (content.isBlank() || currentAgentAddress.isBlank()) return

        // Add user message to the list
        val userItem = ChatItem.User(
            id = (++itemIdCounter).toString(),
            content = content
        )
        _uiState.update { state ->
            state.copy(
                chatItems = state.chatItems + userItem,
                isLoading = true,
                isWaiting = false
            )
        }

        // Send to agent
        connection?.sendMessage(content, currentAgentAddress)
    }

    /**
     * Respond to ask_user event.
     */
    fun respond(answer: String) {
        connection?.respond(answer)
        _uiState.update { it.copy(isWaiting = false, isLoading = true) }
    }

    /**
     * Respond to approval_needed event.
     */
    fun respondToApproval(approved: Boolean) {
        connection?.respondToApproval(approved)
        _uiState.update { it.copy(isWaiting = false, isLoading = true) }
    }

    /**
     * Disconnect from the current agent.
     */
    fun disconnect() {
        connection?.disconnect()
        _uiState.update { state ->
            state.copy(
                isConnected = false,
                agentAddress = ""
            )
        }
        currentAgentAddress = ""
    }

    /**
     * Clear chat history and reset session.
     */
    fun clearChat() {
        connection?.reset()
        _uiState.update { state ->
            state.copy(chatItems = emptyList())
        }
        itemIdCounter = 0
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        connection?.disconnect()
    }
}
