package com.asmr.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmr.player.util.MessageType

data class VisibleAppMessage(
    val id: Long,
    val message: String,
    val type: MessageType
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppMessageOverlay(
    messages: List<VisibleAppMessage>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        userScrollEnabled = false
    ) {
        items(items = messages, key = { it.id }) { msg ->
            Box(modifier = Modifier.animateItemPlacement()) {
                AppSnackbar(message = msg.message, type = msg.type)
            }
        }
    }
}
