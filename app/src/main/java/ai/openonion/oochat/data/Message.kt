package ai.openonion.oochat.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

@Serializable
data class AgentConfig(
    val name: String,
    val relayUrl: String = "wss://relay.connectonion.com",
    val apiKey: String? = null
)

// WebSocket message formats
@Serializable
data class ChatRequest(
    val type: String = "chat",
    val message: String,
    val agent: String? = null
)

@Serializable
data class ChatResponse(
    val type: String,
    val content: String? = null,
    val error: String? = null
)
