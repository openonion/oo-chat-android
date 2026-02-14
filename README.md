# OO Chat Android

A native Android chat client for ConnectOnion agents. Connect to any ConnectOnion agent from your mobile device.

## Features

- 💬 Real-time chat with ConnectOnion agents
- 🔌 WebSocket connection to agent relay
- 🎨 Material Design 3 with Jetpack Compose
- 🌙 Dark mode support
- 📱 Modern Android architecture (ViewModel, StateFlow, Coroutines)

## Screenshots

*Coming soon*

## Requirements

- Android 8.0 (API 26) or higher
- Android Studio Hedgehog (2023.1.1) or newer

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/openonion/oo-chat-android.git
cd oo-chat-android
```

### 2. Open in Android Studio

Open the project in Android Studio and let it sync the Gradle dependencies.

### 3. Run the app

Connect an Android device or start an emulator, then click Run.

## Usage

1. **Connect to an Agent**: Tap the link icon in the top bar
2. **Enter Agent Address**: e.g., `my-agent` or `my-agent@relay.connectonion.com`
3. **Start Chatting**: Type your message and tap send

## Architecture

```
app/
├── src/main/java/ai/openonion/oochat/
│   ├── MainActivity.kt          # Entry point
│   ├── OOChatApp.kt             # App navigation
│   ├── data/
│   │   └── Message.kt           # Data models
│   ├── network/
│   │   └── AgentConnection.kt   # WebSocket client
│   └── ui/
│       ├── chat/
│       │   ├── ChatScreen.kt    # Chat UI
│       │   └── ChatViewModel.kt # Chat logic
│       └── theme/
│           └── Theme.kt         # Material theme
```

## Tech Stack

- **UI**: Jetpack Compose + Material 3
- **Networking**: OkHttp WebSocket
- **Serialization**: Kotlinx Serialization
- **Architecture**: MVVM with StateFlow
- **Concurrency**: Kotlin Coroutines

## Connecting to ConnectOnion Agents

### Via Relay (Recommended)

The app connects to agents through the ConnectOnion relay server:

```kotlin
// Connect to an agent
viewModel.connectToAgent("my-agent")

// The connection URL becomes:
// wss://relay.connectonion.com/connect/my-agent
```

### Agent Requirements

Your ConnectOnion agent should be running with relay mode enabled:

```python
from connectonion import Agent

agent = Agent("my-agent")
agent.host(relay=True)  # Announces to relay
```

## Roadmap

- [ ] Agent discovery (browse available agents)
- [ ] Message persistence (local history)
- [ ] Push notifications
- [ ] Voice input
- [ ] File/image sharing
- [ ] Multiple chat sessions
- [ ] ConnectOnion managed keys (co/ auth)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [ConnectOnion Framework](https://github.com/openonion/connectonion)
- [ConnectOnion Docs](https://docs.connectonion.com)
- [Discord Community](https://discord.gg/4xfD9k8AUF)
