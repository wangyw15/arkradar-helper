package me.nanip.arkradarhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.nanip.arkradarhelper.accessibility.AccessibilityHelper
import me.nanip.arkradarhelper.ui.theme.ArkCyan
import me.nanip.arkradarhelper.ui.theme.ArkCyanDim
import me.nanip.arkradarhelper.ui.theme.ArkGrey
import me.nanip.arkradarhelper.ui.theme.ArkRadarHelperTheme
import me.nanip.arkradarhelper.ui.theme.ArkWhite

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArkRadarHelperTheme {
                val context = LocalContext.current
                // 复选框状态 = AccessibilityHelper.isAccessibilityEnabled 的返回值，
                // 并在每次回到前台（ON_RESUME）时重新查询，以便用户开启权限返回后立即刷新
                var accessibilityGranted by remember {
                    mutableStateOf(AccessibilityHelper.isAccessibilityEnabled(context))
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            accessibilityGranted =
                                AccessibilityHelper.isAccessibilityEnabled(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                ArkRadarScreen(
                    accessibilityGranted = accessibilityGranted,
                    onRequestAccessibility = {
                        AccessibilityHelper.openAccessibilitySettings(context)
                    },
                    onStartGreeting = {
                        // TODO: 启动无障碍自动点击助手
                    }
                )
            }
        }
    }
}

@Composable
fun ArkRadarScreen(
    onStartGreeting: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityGranted: Boolean = false,
    onRequestAccessibility: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BlueprintBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            TopBar()

            // Stage: indexed operation title
            Column(modifier = Modifier.weight(1f)) {
                Spacer(modifier = Modifier.height(64.dp))
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 }
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.stage_index),
                            color = ArkCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.stage_title),
                            color = ArkWhite,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = stringResource(R.string.stage_title_en),
                            color = ArkGrey,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 6.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        // 1px cyan state rule
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(2.dp)
                                .background(ArkCyan)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.stage_subtitle),
                            color = ArkWhite,
                            fontSize = 16.sp
                        )
                        Text(
                            text = stringResource(R.string.stage_subtitle_en),
                            color = ArkGrey,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // Accessibility permission status (read-only indicator)
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 100)) +
                        slideInVertically(tween(500, delayMillis = 100)) { it / 4 }
            ) {
                AccessibilityStatusRow(
                    granted = accessibilityGranted,
                    onRequestPermission = onRequestAccessibility
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary action
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 150)) +
                        slideInVertically(tween(500, delayMillis = 150)) { it / 4 }
            ) {
                GreetingButton(onClick = onStartGreeting)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Baseline()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // diagonal slash marker
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .background(ArkCyan)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.top_bar_title),
                color = ArkWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.top_bar_title_en),
                color = ArkGrey,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(ArkCyan)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.status_standby),
            color = ArkCyan,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Accessibility permission indicator.
 * 已授权时为纯展示；未授权时整行可点击，跳转系统无障碍设置页。
 * Squared 1px-stroke box; cyan fill + check when granted, hollow when not.
 */
@Composable
private fun AccessibilityStatusRow(granted: Boolean, onRequestPermission: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !granted,
                onClickLabel = stringResource(R.string.accessibility_go_enable),
                role = Role.Button,
                onClick = onRequestPermission
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReadOnlyCheckbox(checked = granted)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.accessibility_permission),
                color = ArkWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.accessibility_permission_en),
                color = ArkGrey,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(
                if (granted) R.string.accessibility_enabled
                else R.string.accessibility_disabled
            ),
            color = if (granted) ArkCyan else ArkGrey,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        if (!granted) {
            // 未授权时的可点击提示
            Text(
                text = "›",
                color = ArkGrey,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun ReadOnlyCheckbox(checked: Boolean) {
    val borderColor = if (checked) ArkCyan else ArkGrey.copy(alpha = 0.6f)
    val fillColor = if (checked) ArkCyan else Color.Transparent
    val checkColor = MaterialTheme.colorScheme.onPrimary
    Canvas(
        modifier = Modifier
            .size(20.dp)
            .border(1.dp, borderColor)
            .background(fillColor)
    ) {
        if (checked) {
            val w = size.width
            val h = size.height
            val stroke = 2.dp.toPx()
            drawLine(checkColor, Offset(w * 0.22f, h * 0.52f), Offset(w * 0.42f, h * 0.72f), stroke)
            drawLine(checkColor, Offset(w * 0.42f, h * 0.72f), Offset(w * 0.78f, h * 0.28f), stroke)
        }
    }
}

@Composable
private fun GreetingButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ArkCyan,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.action_start_greeting),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    text = stringResource(R.string.action_start_greeting_en),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}

@Composable
private fun Baseline() {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ArkGrey.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.baseline_module),
                color = ArkGrey,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.baseline_version),
                color = ArkGrey,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * One restrained blueprint layer: sparse 1px guide lines and corner ticks.
 * Every line supports grouping/direction; no random HUD noise.
 */
@Composable
private fun BlueprintBackdrop() {
    val lineColor = ArkCyanDim.copy(alpha = 0.18f)
    val tickColor = ArkCyan.copy(alpha = 0.35f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()

        // vertical guide line on the left edge
        drawLine(lineColor, Offset(w * 0.08f, h * 0.2f), Offset(w * 0.08f, h * 0.55f), stroke)
        // horizontal guide line through the stage
        drawLine(lineColor, Offset(w * 0.08f, h * 0.55f), Offset(w * 0.85f, h * 0.55f), stroke)

        // corner ticks, top-right
        val tick = 10.dp.toPx()
        drawLine(tickColor, Offset(w - tick, h * 0.1f), Offset(w, h * 0.1f), stroke)
        drawLine(tickColor, Offset(w, h * 0.1f), Offset(w, h * 0.1f + tick), stroke)
        // corner ticks, bottom-left
        drawLine(tickColor, Offset(0f, h * 0.92f - tick), Offset(0f, h * 0.92f), stroke)
        drawLine(tickColor, Offset(0f, h * 0.92f), Offset(tick, h * 0.92f), stroke)
    }
}

@Preview(showBackground = true, name = "Permission disabled")
@Composable
fun ArkRadarScreenPreview() {
    ArkRadarHelperTheme {
        ArkRadarScreen(onStartGreeting = {}, accessibilityGranted = false)
    }
}

@Preview(showBackground = true, name = "Permission enabled")
@Composable
fun ArkRadarScreenGrantedPreview() {
    ArkRadarHelperTheme {
        ArkRadarScreen(onStartGreeting = {}, accessibilityGranted = true)
    }
}
