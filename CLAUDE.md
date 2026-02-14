# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

oo-chat-android is a native Android chat client for ConnectOnion agents. It connects to agents using the same protocol as oo-chat (web) and connectonion-ts SDK:
- Ed25519 signed messages for authentication
- Agent addressing via relay (wss://oo.openonion.ai) or direct URL
- Session management for multi-turn conversations
- Real-time streaming of tool calls, thinking, and responses

## Development Commands

```bash
# Build the project
./gradlew build

# Run on emulator/device
./gradlew installDebug

# Run tests
./gradlew test

# Clean build
./gradlew clean
```

## Architecture

```
app/src/main/java/ai/openonion/oochat/
├── MainActivity.kt           # Entry point, sets up Compose
├── OOChatApp.kt             # Application class
├── crypto/
│   └── KeyManager.kt        # Ed25519 key generation, storage, signing
├── data/
│   └── Message.kt           # Data models (ChatItem, SessionState, etc.)
├── network/
│   └── AgentConnection.kt   # WebSocket connection to agents
└── ui/
    ├── chat/
    │   ├── ChatScreen.kt    # Main chat UI
    │   └── ChatViewModel.kt # Chat state management
    └── theme/
        └── Theme.kt         # Material 3 theming
```

### Connection Protocol

The app implements the ConnectOnion WebSocket protocol:

1. **Connect**: Opens WebSocket to `wss://oo.openonion.ai/ws/input` (relay) or direct agent URL
2. **Send INPUT**: Signed message with prompt, timestamp, agent address
3. **Receive Events**: Streaming events (llm_call, tool_call, assistant, etc.)
4. **Receive OUTPUT**: Final response with session state

### Ed25519 Signing

Uses lazysodium-android for Ed25519:
- Keys stored in SharedPreferences (encrypted)
- Canonical JSON (sorted keys) for consistent signatures
- Payload: `{ prompt, timestamp, to? }` → Sign → `{ payload, from, signature }`

### Chat Items

The UI renders different item types:
- `ChatItem.User` - User messages (right-aligned)
- `ChatItem.Agent` - Agent responses (left-aligned)
- `ChatItem.Thinking` - LLM thinking indicator
- `ChatItem.ToolCall` - Tool execution status
- `ChatItem.AskUser` - Agent question with options
- `ChatItem.ApprovalNeeded` - Tool approval request
- `ChatItem.OnboardRequired` - Verification requirement
- `ChatItem.OnboardSuccess` - Verification complete

## Key Dependencies

- **Jetpack Compose** - UI framework
- **OkHttp** - WebSocket connections
- **kotlinx-serialization** - JSON parsing
- **lazysodium-android** - Ed25519 cryptography
- **Material 3** - Design system

## Environment

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9.x
- **Compose**: 2024.01.00 BOM

## Related Projects

- `../oo-chat` - Web client (Next.js)
- `../connectonion-ts` - TypeScript SDK
- `../connectonion` - Python SDK (reference implementation)
