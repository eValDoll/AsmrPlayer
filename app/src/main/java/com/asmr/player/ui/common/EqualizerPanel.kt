package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.data.settings.AsmrPreset
import com.asmr.player.data.settings.EqualizerPresets
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerPanel(
    settings: EqualizerSettings,
    customPresets: List<AsmrPreset>,
    onSettingsChanged: (EqualizerSettings) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (AsmrPreset) -> Unit,
    playbackSpeed: Float? = null,
    playbackPitch: Float? = null,
    onPlaybackSpeedChanged: ((Float) -> Unit)? = null,
    onPlaybackPitchChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val allPresets = remember(customPresets) {
        EqualizerPresets.DefaultPresets + customPresets
    }
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    val freqLabels = remember { listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }

    fun normalizedLevels(): List<Int> {
        val src = settings.bandLevels
        if (src.size == 10) return src
        val out = MutableList(10) { 0 }
        for (i in 0 until minOf(10, src.size)) out[i] = src[i]
        return out
    }

    fun updateLevel(index: Int, value: Int) {
        val newLevels = normalizedLevels().toMutableList()
        newLevels[index] = value
        onSettingsChanged(settings.copy(enabled = true, bandLevels = newLevels, presetName = "自定义"))
    }

    val quickPresets = remember {
        linkedMapOf(
            "流行" to listOf(-200, -100, 0, 200, 300, 300, 200, 100, 0, -100),
            "舞曲" to listOf(300, 250, 150, 0, -100, 0, 200, 400, 450, 350),
            "摇滚" to listOf(250, 200, 50, -100, -50, 150, 350, 500, 300, 100),
            "古典" to listOf(0, 0, 0, 50, 100, 150, 150, 100, 50, 0),
            "人声" to listOf(-300, -200, -100, 100, 300, 650, 900, 650, 200, -150),
            "慢歌" to listOf(150, 120, 80, 60, 30, 0, -50, -80, -120, -150),
            "电子乐" to listOf(350, 300, 200, 50, -100, 0, 250, 500, 650, 550),
            "重低音" to listOf(600, 500, 300, 100, -100, -200, -250, -300, -350, -400),
            "柔和" to listOf(100, 80, 50, 0, -50, -120, -180, -250, -300, -350)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("音效器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("环境混响音效", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onSettingsChanged(
                            settings.copy(
                                reverbEnabled = false,
                                reverbPreset = "无",
                                reverbWet = 0,
                                originalGain = 1f,
                                presetName = "自定义"
                            )
                        )
                    }) { Text("重置") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.reverbEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(reverbEnabled = it, presetName = "自定义")) }
                    )
                }

                val reverbOptions = listOf("电话", "教堂", "大厅", "电影院", "餐厅", "卫生间", "室内", "反馈弹簧")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reverbOptions.forEach { opt ->
                        val selected = settings.reverbPreset == opt && settings.reverbEnabled
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val enabled = !(selected && settings.reverbEnabled)
                                onSettingsChanged(
                                    settings.copy(
                                        reverbEnabled = enabled,
                                        reverbPreset = opt,
                                        presetName = "自定义"
                                    )
                                )
                            },
                            label = { Text(opt) }
                        )
                    }
                }

                Column {
                    Row {
                        Text("原始音频增益", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${(settings.originalGain * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.originalGain,
                        onValueChange = { onSettingsChanged(settings.copy(originalGain = it, presetName = "自定义")) },
                        valueRange = 0f..2f
                    )
                }

                Column {
                    Row {
                        Text("环境音效增益", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${settings.reverbWet}%", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.reverbWet.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(reverbWet = it.toInt(), presetName = "自定义")) },
                        valueRange = 0f..100f
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("均衡器", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onSettingsChanged(
                            settings.copy(
                                enabled = true,
                                bandLevels = List(10) { 0 },
                                virtualizerStrength = 0,
                                balance = 0f,
                                presetName = "默认"
                            )
                        )
                    }) { Text("重置") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(enabled = it, presetName = "自定义")) }
                    )
                }

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = settings.presetName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("预设") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        allPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(preset.name)
                                        if (preset.isCustom) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            IconButton(onClick = { onDeletePreset(preset) }) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    val levels = preset.bandLevels.let { src ->
                                        val out = MutableList(10) { 0 }
                                        for (i in 0 until minOf(10, src.size)) out[i] = src[i]
                                        out
                                    }
                                    onSettingsChanged(
                                        settings.copy(
                                            enabled = true,
                                            bandLevels = levels,
                                            virtualizerStrength = preset.virtualizerStrength,
                                            presetName = preset.name
                                        )
                                    )
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Text("频段调节", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                val levels = normalizedLevels()
                for (i in 0 until 10) {
                    Column {
                        Row {
                            Text("${freqLabels[i]} Hz", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.weight(1f))
                            val db = (levels[i] / 100f)
                            val dbText = if (abs(db) < 0.01f) "0 dB" else "${db.toInt()} dB"
                            Text(dbText, style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = levels[i].toFloat(),
                            onValueChange = { updateLevel(i, it.toInt()) },
                            valueRange = -1500f..1500f
                        )
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickPresets.forEach { (name, curve) ->
                        FilterChip(
                            selected = settings.presetName == name,
                            onClick = {
                                onSettingsChanged(
                                    settings.copy(
                                        enabled = true,
                                        bandLevels = curve,
                                        presetName = name
                                    )
                                )
                            },
                            label = { Text(name) }
                        )
                    }
                    AssistChip(
                        onClick = { showSaveDialog = true },
                        label = { Text("＋") }
                    )
                }

                Column {
                    Row {
                        Text("空间环绕 (Virtualizer)", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${(settings.virtualizerStrength / 10f).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.virtualizerStrength.toFloat(),
                        onValueChange = {
                            onSettingsChanged(
                                settings.copy(
                                    enabled = true,
                                    virtualizerStrength = it.toInt(),
                                    presetName = "自定义"
                                )
                            )
                        },
                        valueRange = 0f..1000f
                    )
                }

                Column {
                    Row {
                        Text("声道平衡 (L - R)", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        val balText = when {
                            settings.balance < -0.05f -> "左偏 ${(settings.balance * -100).toInt()}%"
                            settings.balance > 0.05f -> "右偏 ${(settings.balance * 100).toInt()}%"
                            else -> "居中"
                        }
                        Text(balText, style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.balance,
                        onValueChange = { onSettingsChanged(settings.copy(enabled = true, balance = it, presetName = "自定义")) },
                        valueRange = -1f..1f
                    )
                }

                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存为自定义预设")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("3D 立体环绕 (需耳机)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onSettingsChanged(
                            settings.copy(
                                orbitEnabled = false,
                                orbitSpeed = 25f,
                                orbitDistance = 5f,
                                presetName = "自定义"
                            )
                        )
                    }) { Text("重置") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.orbitEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(orbitEnabled = it, presetName = "自定义")) }
                    )
                }

                Column {
                    Row {
                        Text("环绕速度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${settings.orbitSpeed.toInt()}", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.orbitSpeed,
                        onValueChange = { onSettingsChanged(settings.copy(orbitSpeed = it, presetName = "自定义")) },
                        valueRange = 0f..50f
                    )
                }

                Column {
                    Row {
                        Text("声音距离", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${settings.orbitDistance.toInt()}", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = settings.orbitDistance,
                        onValueChange = { onSettingsChanged(settings.copy(orbitDistance = it, presetName = "自定义")) },
                        valueRange = 0f..10f
                    )
                }
            }
        }

        if (playbackSpeed != null && playbackPitch != null && onPlaybackSpeedChanged != null && onPlaybackPitchChanged != null) {
            Card(colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("变速变调", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            onPlaybackSpeedChanged(1f)
                            onPlaybackPitchChanged(1f)
                        }) { Text("重置") }
                    }

                    Column {
                        Row {
                            Text("播放速度", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(String.format("%.2fx", playbackSpeed), style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = playbackSpeed,
                            onValueChange = { onPlaybackSpeedChanged(it) },
                            valueRange = 0.5f..2f
                        )
                    }

                    Column {
                        Row {
                            Text("音调", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(String.format("%.2fx", playbackPitch), style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = playbackPitch,
                            onValueChange = { onPlaybackPitchChanged(it) },
                            valueRange = 0.5f..2f
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存预设") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("预设名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            onSavePreset(newPresetName)
                            showSaveDialog = false
                            newPresetName = ""
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
