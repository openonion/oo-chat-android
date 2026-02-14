package ai.openonion.oochat.data

import kotlinx.serialization.SerialName
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

// Session state synced from server
@Serializable
data class SessionState(
    @SerialName("session_id") val sessionId: String? = null,
    val messages: List<SessionMessage>? = null,
    val trace: List<String>? = null,
    val turn: Int? = null
)

@Serializable
data class SessionMessage(
    val role: String,
    val content: String
)

// INPUT message sent to agent
@Serializable
data class InputMessage(
    val type: String = "INPUT",
    @SerialName("input_id") val inputId: String,
    val prompt: String,
    val to: String? = null,  // Agent address (for relay mode)
    val payload: InputPayload? = null,
    val from: String? = null,  // Our address
    val signature: String? = null,
    val timestamp: Long? = null,
    val session: SessionState? = null,
    val images: List<String>? = null
)

@Serializable
data class InputPayload(
    val prompt: String,
    val to: String? = null,
    val timestamp: Long
)

// Server events
@Serializable
data class ServerEvent(
    val type: String,
    // OUTPUT fields
    val result: String? = null,
    @SerialName("input_id") val inputId: String? = null,
    val session: SessionState? = null,
    // Error fields
    val message: String? = null,
    val error: String? = null,
    // Tool call fields
    val id: String? = null,
    @SerialName("tool_id") val toolId: String? = null,
    val name: String? = null,
    val args: Map<String, String>? = null,
    val status: String? = null,
    // Thinking fields
    val model: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    val content: String? = null,
    val kind: String? = null,
    // Ask user fields
    val text: String? = null,
    val options: List<String>? = null,
    @SerialName("multi_select") val multiSelect: Boolean? = null,
    // Approval fields
    val tool: String? = null,
    val arguments: Map<String, String>? = null,
    val description: String? = null,
    // Onboard fields
    val methods: List<String>? = null,
    @SerialName("payment_amount") val paymentAmount: Double? = null,
    val level: String? = null
)

// Chat UI item types
sealed class ChatItem {
    abstract val id: String

    data class User(
        override val id: String,
        val content: String,
        val images: List<String>? = null
    ) : ChatItem()

    data class Agent(
        override val id: String,
        val content: String
    ) : ChatItem()

    data class Thinking(
        override val id: String,
        val status: ThinkingStatus,
        val model: String? = null,
        val durationMs: Long? = null,
        val content: String? = null
    ) : ChatItem()

    data class ToolCall(
        override val id: String,
        val name: String,
        val args: Map<String, String>? = null,
        var status: ToolStatus = ToolStatus.RUNNING,
        var result: String? = null,
        var timingMs: Long? = null
    ) : ChatItem()

    data class AskUser(
        override val id: String,
        val text: String,
        val options: List<String>,
        val multiSelect: Boolean
    ) : ChatItem()

    data class ApprovalNeeded(
        override val id: String,
        val tool: String,
        val arguments: Map<String, String>,
        val description: String? = null
    ) : ChatItem()

    data class OnboardRequired(
        override val id: String,
        val methods: List<String>,
        val paymentAmount: Double? = null
    ) : ChatItem()

    data class OnboardSuccess(
        override val id: String,
        val level: String,
        val message: String
    ) : ChatItem()
}

enum class ThinkingStatus { RUNNING, DONE, ERROR }
enum class ToolStatus { RUNNING, DONE, ERROR }
