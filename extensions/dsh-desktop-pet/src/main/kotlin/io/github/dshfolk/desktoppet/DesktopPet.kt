package io.github.dshfolk.desktoppet

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 桌宠的可视部分。
 *
 * ## 三种显示位置共用同一个 Composable
 *
 * WEBUI / HOST / OVERLAY 的区别只在**宿主把它放在哪个容器里**，宠物本身的画法完全一样。
 * 做成三个组件会让"改一处忘另外两处"变成必然。
 *
 * ## 拖动与坐标
 *
 * 拖动用 [Modifier.offset] 记相对位移，不写回配置 —— 每次会话内有效。写回配置的话，
 * 用户不小心拖一下就会永久改变位置，而"回到默认位置"又得另做一个按钮。
 * 想固定位置时用长按菜单里的"记住位置"（见 [PetActions]），那是显式意图。
 */
@Composable
fun DesktopPet(
    config: PetConfig,
    bubble: String?,
    onInteract: (PetScene) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var actionsOpen by remember { mutableStateOf(false) }
    var sleeping by remember { mutableStateOf(false) }

    // 呼吸动画：缩放 0.98–1.02，2 秒一个来回。
    // 幅度必须很小 —— 大幅跳动会让人分心，而桌宠应该像"在那儿呼吸"，不是"在动"。
    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    // 睡觉时停止呼吸动画的视觉强度：变暗 + 更慢。
    val effectiveScale = if (sleeping) 1f else breath
    val effectiveAlpha = config.opacity * if (sleeping) 0.55f else 1f

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 气泡
            bubble?.let { text ->
                Box(
                    Modifier
                        .widthIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF21F2937))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(text, color = Color(0xFFF8FAFC), fontSize = 12.sp)
                }
            }

            // 宠物本体
            Box(
                Modifier
                    .size(config.size.dp.dp)
                    .scale(effectiveScale)
                    .alpha(effectiveAlpha)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        }
                    }
                    .clickable {
                        if (sleeping) {
                            sleeping = false
                            onInteract(PetScene.WAKE)
                        } else {
                            actionsOpen = !actionsOpen
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                PetImageOrDefault(config = config, sleeping = sleeping)
            }

            // 动作条
            if (actionsOpen && !sleeping) {
                PetActions(
                    onAction = { scene ->
                        actionsOpen = false
                        if (scene == PetScene.SLEEP) sleeping = true
                        onInteract(scene)
                    },
                    onClose = { actionsOpen = false },
                )
            }
        }
    }
}

/** 有自定义图就画图，没有就画一个占位圆。 */
@Composable
private fun PetImageOrDefault(config: PetConfig, sleeping: Boolean) {
    val store = PetConfigHolder.store()
    val file = store?.petImageFile()
    val bitmap = remember(file?.absolutePath, file?.length()) {
        file?.takeIf { it.isFile }?.let { f ->
            runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "桌宠",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        // 没有图时的占位：一个圆 + 表情。
        // 不画空白框 —— 用户会以为扩展坏了。
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (sleeping) "z Z" else "•‿•",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 下方横向动作条。 */
@Composable
private fun PetActions(onAction: (PetScene) -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF21F2937))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ActionChip("摸") { onAction(PetScene.PAT) }
        ActionChip("喂") { onAction(PetScene.FEED) }
        ActionChip("睡") { onAction(PetScene.SLEEP) }
        ActionChip("聊") { onAction(PetScene.GREET) }
        ActionChip("×") { onClose() }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color(0xFFF8FAFC), fontSize = 13.sp)
    }
}
