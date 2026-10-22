package sc.eidolon.nativeapp.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.theme.herColors

@Composable
fun CallScreen(
    container: AppContainer,
    characterId: String,
    onEnd: () -> Unit,
) {
    val baseUrl = container.session.serverUrl.orEmpty()
    val character by produceState(
        initialValue = container.characters.cached(characterId),
    ) {
        if (value == null) {
            value = runCatching { container.characters.all() }
                .getOrDefault(emptyList())
                .firstOrNull { it.id == characterId }
        }
    }
    val person = character
    val her = herColors(person?.color ?: "#7FB4FF")
    var connected by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }

    BackHandler { onEnd() }
    LaunchedEffect(Unit) {
        delay(1_800)
        connected = true
    }
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        while (true) {
            delay(1_000)
            seconds += 1
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF07090D))) {
        if (person?.backgroundUrl != null) {
            AsyncImage(
                model = baseUrl + person.backgroundUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(her.mark.copy(alpha = 0.55f), Color(0xFF07090D)),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color(0xFF05070B).copy(alpha = 0.96f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                ) {
                    Text(
                        text = if (connected) "通话中" else "正在呼叫…",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PulsingAvatar(person, her.mark, connected, baseUrl)
            Spacer(Modifier.height(24.dp))
            Text(
                text = person?.name ?: "通话",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (connected) formatCallTime(seconds) else "正在接通……",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "语音尚未接入，当前为界面预览",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(visible = connected && person?.callOpenerJa != null, enter = fadeIn(tween(300))) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = person?.callOpenerJa.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        person?.callOpenerZh?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallControl(
                    icon = if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    label = if (muted) "已静音" else "静音",
                    active = muted,
                    onClick = { muted = !muted },
                )
                HangUpControl(onClick = onEnd)
                CallControl(
                    icon = if (speaker) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    label = "免提",
                    active = speaker,
                    onClick = { speaker = !speaker },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PulsingAvatar(character: Character?, color: Color, connected: Boolean, baseUrl: String) {
    val transition = rememberInfiniteTransition(label = "call-avatar")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "call-avatar-scale",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(148.dp)
                .scale(if (connected) scale else 1f)
                .border(1.dp, color.copy(alpha = 0.55f), CircleShape),
        )
        CharacterAvatar(
            name = character?.name ?: "她",
            color = character?.color ?: "#7FB4FF",
            url = character?.avatarUrl?.let { baseUrl + it },
            size = 116.dp,
            modifier = Modifier.clip(CircleShape),
        )
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.padding(18.dp).size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HangUpControl(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = Color(0xFFD93B4A)) {
            Icon(
                imageVector = Icons.Outlined.CallEnd,
                contentDescription = "挂断",
                tint = Color.White,
                modifier = Modifier.padding(18.dp).size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("挂断", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatCallTime(seconds: Int): String =
    "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
