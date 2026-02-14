package ai.openonion.oochat.network

import ai.openonion.oochat.data.ChatRequest
import ai.openonion.oochat.data.ChatResponse
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
import java.util.concurrent.TimeUnit

/**
 * Manages WebSocket connection to ConnectOnion agents.
 *
 * Supports two connection modes:
 * 1. Relay mode: Connect via relay.connectonion.com
 * 2. Direct mode: Connect directly to a hosted agent
 */
class AgentConnection(
    private val baseUrl: String = "wss://relay.connectonion.com"
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _messages = Channel<ConnectionEvent>(Channel.BUFFERED)
    val events: Flow<ConnectionEvent> = _messages.receiveAsFlow()

    sealed class ConnectionEvent {
        data class Connected(val message: String = "Connected") : ConnectionEvent()
        data class MessageReceived(val response: ChatResponse) : ConnectionEvent()
        data class Error(val message: String) : ConnectionEvent()
        data object Disconnected : ConnectionEvent()
    }

    fun connect(agentAddress: String? = null) {
        val url = if (agentAddress != null) {
            "$baseUrl/connect/$agentAddress"
        } else {
            baseUrl
        }

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _messages.trySend(ConnectionEvent.Connected())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val response = json.decodeFromString<ChatResponse>(text)
                    _messages.trySend(ConnectionEvent.MessageReceived(response))
                } catch (e: Exception) {
                    // If not valid JSON, treat as plain text response
                    _messages.trySend(
                        ConnectionEvent.MessageReceived(
                            ChatResponse(type = "message", content = text)
                        )
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _messages.trySend(ConnectionEvent.Error(t.message ?: "Connection failed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _messages.trySend(ConnectionEvent.Disconnected)
            }
        })
    }

    fun sendMessage(content: String, agentName: String? = null) {
        val request = ChatRequest(
            message = content,
            agent = agentName
        )
        val jsonString = json.encodeToString(request)
        webSocket?.send(jsonString)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun isConnected(): Boolean = webSocket != null
}
