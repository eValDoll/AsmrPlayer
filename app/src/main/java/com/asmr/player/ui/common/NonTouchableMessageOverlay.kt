package com.asmr.player.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
    val shouldShow = currentMessages.isNotEmpty()

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
            setTag(androidx.lifecycle.runtime.R.id.view_tree_lifecycle_owner, activity)
            setTag(androidx.lifecycle.viewmodel.R.id.view_tree_view_model_store_owner, activity)
            setTag(androidx.savedstate.R.id.view_tree_saved_state_registry_owner, activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme(
                    colorScheme = currentColorScheme,
                    typography = currentTypography,
                    shapes = currentShapes
                ) {
                    if (currentMessages.isNotEmpty()) AppMessageOverlay(messages = currentMessages)
                }
            }
        }
    }

    val layoutParams = remember(activity, startPadding, bottomPadding) {
        val density = activity.resources.displayMetrics.density
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            token = activity.window.decorView.windowToken
            x = (startPadding.value * density).toInt()
            y = (bottomPadding.value * density).toInt()
        }
    }

    DisposableEffect(activity, shouldShow) {
        if (shouldShow) runCatching { windowManager.addView(overlayView, layoutParams) }
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
