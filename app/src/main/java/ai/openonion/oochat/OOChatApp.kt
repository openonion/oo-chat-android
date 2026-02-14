package ai.openonion.oochat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ai.openonion.oochat.ui.chat.ChatScreen

@Composable
fun OOChatApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        ChatScreen(modifier = Modifier.padding(innerPadding))
    }
}
