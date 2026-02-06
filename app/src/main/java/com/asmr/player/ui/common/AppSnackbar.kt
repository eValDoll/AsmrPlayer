package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.util.MessageType

@Composable
fun AppSnackbar(
    message: String,
    type: MessageType,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (type) {
        MessageType.Success -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        MessageType.Error -> Icons.Default.Error to Color(0xFFF44336)
        MessageType.Warning -> Icons.Default.Warning to Color(0xFFFF9800)
        MessageType.Info -> Icons.Default.Info to Color(0xFF2196F3)
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            // 这里我们解析 message 中的特殊前缀来判断类型，或者直接使用默认
            // 稍后在 MessageManager 集成时，我们会通过 SnackbarHostState.showSnackbar 传入特定格式的字符串
            val (message, type) = parseSnackbarMessage(data.visuals.message)
            AppSnackbar(
                message = message,
                type = type
            )
        }
    )
}

private fun parseSnackbarMessage(rawMessage: String): Pair<String, MessageType> {
    return when {
        rawMessage.startsWith("[SUCCESS]") -> rawMessage.removePrefix("[SUCCESS]") to MessageType.Success
        rawMessage.startsWith("[ERROR]") -> rawMessage.removePrefix("[ERROR]") to MessageType.Error
        rawMessage.startsWith("[WARNING]") -> rawMessage.removePrefix("[WARNING]") to MessageType.Warning
        rawMessage.startsWith("[INFO]") -> rawMessage.removePrefix("[INFO]") to MessageType.Info
        else -> rawMessage to MessageType.Info
    }
}
