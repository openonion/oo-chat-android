package ai.openonion.oochat.network

import ai.openonion.oochat.crypto.KeyManager
import ai.openonion.oochat.data.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages WebSocket connection to ConnectOnion agents.
 *
 * Implements the same protocol as connectonion-ts:
 * - Ed25519 signed messages
 * - Agent addressing via relay or direct URL
 * - Session management for multi-turn conversations
 * - Streaming events (tool_call, thinking, etc.)
 */
class AgentConnection(
    private val keyManager: KeyManager,
    private val relayUrl: String = DEFAULT_RELAY
) {
    private var webSocket: WebSocket? = null
    private var keys: KeyManager.AddressData? = null
    private var currentSession: SessionState? = null
    private var currentInputId: String? = null
    private var isDirect: Boolean = false

    private val client = OkHttpClient.Builder()
        .readTimeout(600, TimeUnit.SECONDS)  // 10 min timeout like TS SDK
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _events = Channel<ConnectionEvent>(Channel.BUFFERED)
    val events: Flow<ConnectionEvent> = _events.receiveAsFlow()

    sealed class ConnectionEvent {
        data class Connected(val address: String) : ConnectionEvent()
        data class ChatItemReceived(val item: ChatItem) : ConnectionEvent()
        data class ChatItemUpdated(val item: ChatItem) : ConnectionEvent()
        data class OutputReceived(val result: String, val session: SessionState?) : ConnectionEvent()
        data class Error(val message: String) : ConnectionEvent()
        data object Disconnected : ConnectionEvent()
        data object Waiting : ConnectionEvent()  // Agent needs user input
    }

    /**
     * Connect to an agent by address.
     *
     * @param agentAddress Agent's public address (0x...)
     * @param directUrl Optional direct URL for deployed agents (bypasses relay)
     */
    fun connect(agentAddress: String, directUrl: String? = null) {
        // Load or generate keys
        keys = keyManager.loadOrGenerate()

        // Choose connection mode
        val wsUrl = if (directUrl != null) {
            isDirect = true
            val baseUrl = directUrl.replace(Regex("^https?://"), "")
            val protocol = if (directUrl.startsWith("https")) "wss" else "ws"
            "$protocol://$baseUrl/ws"
        } else {
            isDirect = false
            "$relayUrl/ws/input"
        }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _events.trySend(ConnectionEvent.Connected(keys?.address ?: "unknown"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text, agentAddress)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _events.trySend(ConnectionEvent.Error(t.message ?: "Connection failed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _events.trySend(ConnectionEvent.Disconnected)
            }
        })
    }

    /**
     * Send a message to the agent.
     */
    fun sendMessage(prompt: String, agentAddress: String) {
        val k = keys ?: return
        val inputId = UUID.randomUUID().toString()
        currentInputId = inputId

        val timestamp = System.currentTimeMillis() / 1000
        val sessionId = currentSession?.sessionId ?: UUID.randomUUID().toString()

        // Build payload for signing
        val payloadMap = mutableMapOf<String, Any>(
            "prompt" to prompt,
            "timestamp" to timestamp
        )
        if (!isDirect) {
            payloadMap["to"] = agentAddress
        }

        // Sign with canonical JSON
        val canonicalMessage = keyManager.canonicalJson(payloadMap)
        val signature = keyManager.sign(k, canonicalMessage)

        // Build the full INPUT message
        val payload = InputPayload(
            prompt = prompt,
            to = if (!isDirect) agentAddress else null,
            timestamp = timestamp
        )

        val session = currentSession?.copy(sessionId = sessionId)
            ?: SessionState(sessionId = sessionId)

        val inputMessage = InputMessage(
            inputId = inputId,
            prompt = prompt,
            to = if (!isDirect) agentAddress else null,
            payload = payload,
            from = k.address,
            signature = signature,
            timestamp = timestamp,
            session = session
        )

        val jsonString = json.encodeToString(inputMessage)
        webSocket?.send(jsonString)
    }

    /**
     * Respond to ask_user event.
     */
    fun respond(answer: String) {
        val response = mapOf(
            "type" to "ASK_USER_RESPONSE",
            "answer" to answer
        )
        webSocket?.send(json.encodeToString(response))
    }

    /**
     * Respond to approval_needed event.
     */
    fun respondToApproval(approved: Boolean, scope: String = "once") {
        val response = mapOf(
            "type" to "APPROVAL_RESPONSE",
            "approved" to approved,
            "scope" to scope
        )
        webSocket?.send(json.encodeToString(response))
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        currentSession = null
        currentInputId = null
    }

    fun isConnected(): Boolean = webSocket != null

    fun reset() {
        currentSession = null
        currentInputId = null
    }

    private fun handleMessage(text: String, agentAddress: String) {
        val event = json.decodeFromString<ServerEvent>(text)

        when (event.type) {
            // Keep-alive
            "PING" -> {
                webSocket?.send("""{"type":"PONG"}""")
            }

            // Streaming events
            "llm_call" -> {
                val id = event.id ?: UUID.randomUUID().toString()
                _events.trySend(ConnectionEvent.ChatItemReceived(
                    ChatItem.Thinking(
                        id = id,
                        status = ThinkingStatus.RUNNING,
                        model = event.model
                    )
                ))
            }

            "llm_result" -> {
                val id = event.id ?: return
                _events.trySend(ConnectionEvent.ChatItemUpdated(
                    ChatItem.Thinking(
                        id = id,
                        status = if (event.status == "error") ThinkingStatus.ERROR else ThinkingStatus.DONE,
                        model = event.model,
                        durationMs = event.durationMs
                    )
                ))
            }

            "thinking" -> {
                val id = event.id ?: UUID.randomUUID().toString()
                _events.trySend(ConnectionEvent.ChatItemReceived(
                    ChatItem.Thinking(
                        id = id,
                        status = ThinkingStatus.DONE,
                        content = event.content,
                        model = event.model
                    )
                ))
            }

            "tool_call" -> {
                val id = event.toolId ?: event.id ?: UUID.randomUUID().toString()
                _events.trySend(ConnectionEvent.ChatItemReceived(
                    ChatItem.ToolCall(
                        id = id,
                        name = event.name ?: "unknown",
                        args = event.args,
                        status = ToolStatus.RUNNING
                    )
                ))
            }

            "tool_result" -> {
                val id = event.toolId ?: event.id ?: return
                _events.trySend(ConnectionEvent.ChatItemUpdated(
                    ChatItem.ToolCall(
                        id = id,
                        name = event.name ?: "",
                        status = if (event.status == "error") ToolStatus.ERROR else ToolStatus.DONE,
                        result = event.result
                    )
                ))
            }

            "assistant" -> {
                if (event.content != null) {
                    val id = event.id ?: UUID.randomUUID().toString()
                    _events.trySend(ConnectionEvent.ChatItemReceived(
                        ChatItem.Agent(id = id, content = event.content)
                    ))
                }
            }

            // Interactive events
            "ask_user" -> {
                val id = event.id ?: UUID.randomUUID().toString()
                _events.trySend(ConnectionEvent.ChatItemReceived(
                    ChatItem.AskUser(
                        id = id,
                        text = event.text ?: "",
                        options = event.options ?: emptyList(),
                        multiSelect = event.multiSelect ?: false
                    )
                ))
                _events.trySend(ConnectionEvent.Waiting)
            }

            "approval_needed" -> {
                val id = event.id ?: UUID.randomUUID().toString()
                _events.trySend(ConnectionEvent.ChatItemReceived(
                    ChatItem.ApprovalNeeded(
                        id = id,
                        tool = event.tool ?: "",
                        arguments = event.arguments ?: emptyMap(),
                        description = event.description
                    )
                ))
                _events.trySend(ConnectionEvent.Waiting)
            }

            "ONBOARD_REQUIRED" -> {
                val id = event.id ?: UUID.randomUUID().toString()
                _events.trySend(ConnectionEvent.ChatItemReceived(
                    ChatItem.OnboardRequired(
                        id = id,
                        methods = event.methods ?: emptyList(),
                        paymentAmount = event.paymentAmount
                    )
                ))
                _events.trySend(ConnectionEvent.Waiting)
            }

            "ONBOARD_SUCCESS" -> {
                val id = event.id ?: UUID.randomUUID().toString()
                _events.trySend(ConnectionEvent.ChatItemReceived(
                    ChatItem.OnboardSuccess(
                        id = id,
                        level = event.level ?: "",
                        message = event.message ?: ""
                    )
                ))
            }

            // Final response
            "OUTPUT" -> {
                // Match input_id for relay mode
                val isForUs = isDirect || event.inputId == currentInputId
                if (isForUs) {
                    // Sync session from server
                    event.session?.let { currentSession = it }

                    _events.trySend(ConnectionEvent.OutputReceived(
                        result = event.result ?: "",
                        session = event.session
                    ))
                }
            }

            // Error
            "ERROR" -> {
                _events.trySend(ConnectionEvent.Error(
                    event.message ?: event.error ?: "Unknown error"
                ))
            }
        }

        // Sync session state from any event
        event.session?.let { currentSession = it }
    }

    companion object {
        const val DEFAULT_RELAY = "wss://oo.openonion.ai"
    }
}
