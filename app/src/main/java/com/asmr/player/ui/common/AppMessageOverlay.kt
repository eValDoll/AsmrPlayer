package com.asmr.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmr.player.util.MessageType

data class VisibleAppMessage(
    val id: Long,
    val key: String,
    val message: String,
    val type: MessageType,
    val count: Int = 1
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppMessageOverlay(
    messages: List<VisibleAppMessage>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
        userScrollEnabled = false,
        reverseLayout = true
    ) {
        items(items = messages, key = { it.id }) { msg ->
            Box(modifier = Modifier.animateItemPlacement()) {
                AppSnackbar(message = msg.message, type = msg.type, count = msg.count)
            }
        }
    }
}
