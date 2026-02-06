package com.asmr.player.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class MessageType {
    Success,
    Error,
    Info,
    Warning
}

data class AppMessage(
    val message: String,
    val type: MessageType = MessageType.Info,
    val durationMs: Long = 3000
) {
    fun formatForSnackbar(): String = "[${type.name.uppercase()}]$message"
}

@Singleton
class MessageManager @Inject constructor() {
    private val _messages = MutableSharedFlow<AppMessage>(extraBufferCapacity = 10)
    val messages = _messages.asSharedFlow()

    fun showMessage(message: String, type: MessageType = MessageType.Info, durationMs: Long = 3000) {
        _messages.tryEmit(AppMessage(message, type, durationMs))
    }

    fun showSuccess(message: String) = showMessage(message, MessageType.Success)
    fun showError(message: String) = showMessage(message, MessageType.Error)
    fun showWarning(message: String) = showMessage(message, MessageType.Warning)
    fun showInfo(message: String) = showMessage(message, MessageType.Info)
}
