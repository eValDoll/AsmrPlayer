package com.asmr.player.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NonTouchableAppMessageOverlay(
    messages: List<VisibleAppMessage>,
    startPadding: Dp = 16.dp,
    bottomPadding: Dp = 80.dp,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    typography: Typography = MaterialTheme.typography,
    shapes: Shapes = MaterialTheme.shapes
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() } ?: return
    val windowManager = remember(activity) {
        activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    var currentMessages by remember { mutableStateOf<List<VisibleAppMessage>>(emptyList()) }
    SideEffect { currentMessages = messages }

    var currentColorScheme by remember { mutableStateOf(colorScheme) }
    var currentTypography by remember { mutableStateOf(typography) }
    var currentShapes by remember { mutableStateOf(shapes) }
    SideEffect {
        currentColorScheme = colorScheme
        currentTypography = typography
        currentShapes = shapes
    }

    val overlayView = remember(activity) {
        ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme(
                    colorScheme = currentColorScheme,
                    typography = currentTypography,
                    shapes = currentShapes
                ) {
                    if (currentMessages.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                            Box(modifier = Modifier.padding(start = startPadding, bottom = bottomPadding)) {
                                AppMessageOverlay(messages = currentMessages)
                            }
                        }
                    }
                }
            }
        }
    }

    val layoutParams = remember(activity) {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            token = activity.window.decorView.windowToken
        }
    }

    DisposableEffect(activity) {
        runCatching { windowManager.addView(overlayView, layoutParams) }
        onDispose {
            runCatching { windowManager.removeViewImmediate(overlayView) }
        }
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}
