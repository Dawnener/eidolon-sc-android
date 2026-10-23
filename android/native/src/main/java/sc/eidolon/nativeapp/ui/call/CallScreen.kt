package sc.eidolon.nativeapp.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.data.api.CallEvent
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.Outfit
import sc.eidolon.nativeapp.model.OutfitCard
import sc.eidolon.nativeapp.model.joinedSegments
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.theme.herColors

@Composable
fun CallScreen(
    container: AppContainer,
    characterId: String,
    conversationId: String,
    onEnd: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    // 通话页打开期间让屏幕常亮；离开页面或挂断后恢复原来的设置。
    DisposableEffect(view) {
        val wasKeptOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = wasKeptOn }
    }
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
    var reconnecting by remember { mutableStateOf(false) }
    var connectedAt by remember { mutableStateOf<Long?>(null) }
    var callEnded by remember { mutableStateOf(false) }
    var callError by remember { mutableStateOf<String?>(null) }
    var maxSeconds by remember { mutableStateOf<Int?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }
    var hearing by remember { mutableStateOf("") }
    val jaSegments = remember { mutableStateMapOf<Int, String>() }
    val zhSegments = remember { mutableStateMapOf<Int, String>() }
    var speaking by remember { mutableStateOf(false) }
    var mood by remember { mutableStateOf("calm") }
    var nod by remember { mutableStateOf<String?>(null) }
    var nodId by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    val stand = person?.stand
    // 不是每位角色都配了通话开场白；没有时用她的招呼，至少通话页有一句她的台词。
    val openerJa = person?.callOpenerJa ?: person?.greetingJa
    val openerZh = person?.callOpenerZh ?: person?.greetingZh
    val openerAudioUrl = person?.callOpenerAudioUrl ?: person?.greetingAudioUrl
    var openerPlayed by remember(person?.id) { mutableStateOf(false) }
    val openerPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    // 接通后先放服务器预先合成好的开场白；没有音频时仍然显示文字。
    LaunchedEffect(connected, openerAudioUrl) {
        if (!connected || openerPlayed || openerAudioUrl.isNullOrBlank()) return@LaunchedEffect
        openerPlayed = true
        val player = MediaPlayer()
        openerPlayer.value = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(openerAudioUrl)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                it.release()
                if (openerPlayer.value === it) openerPlayer.value = null
            }
            player.setOnErrorListener { failed, what, extra ->
                Log.w("EidolonCall", "opener audio failed: $what/$extra $openerAudioUrl")
                failed.release()
                if (openerPlayer.value === failed) openerPlayer.value = null
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            Log.w("EidolonCall", "opener audio failed", error)
            player.release()
            openerPlayer.value = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            openerPlayer.value?.release()
            openerPlayer.value = null
        }
    }
    var outfitCards by remember(person?.id) { mutableStateOf<List<OutfitCard>>(emptyList()) }
    var outfitsLoading by remember(person?.id) { mutableStateOf(false) }
    var outfitsError by remember(person?.id) { mutableStateOf<String?>(null) }
    var selectedOutfit by remember(person?.id) { mutableStateOf<Outfit?>(null) }
    var showOutfits by remember { mutableStateOf(false) }
    var outfitReload by remember { mutableIntStateOf(0) }
    val activeOutfit = selectedOutfit ?: stand?.defaultOutfit
    var standPhase by remember(activeOutfit?.url) {
        mutableStateOf(StandPhase.Loading)
    }
    val standLayout = stand != null && standPhase != StandPhase.Failed
    val scope = rememberCoroutineScope()

    val controller = remember { container.newCallController() }
    val listener = remember {
        object : CallListener {
            override fun onDialing() {
                reconnecting = false
                callError = null
                callEnded = false
            }

            override fun onReconnecting() {
                connected = false
                reconnecting = true
                callError = null
                callEnded = false
            }

            override fun onConnected(callMaxSeconds: Int?, exempt: Boolean) {
                connected = true
                reconnecting = false
                connectedAt = connectedAt ?: SystemClock.elapsedRealtime()
                maxSeconds = callMaxSeconds
                callError = null
                callEnded = false
                controller.setSpeaker(speaker)
                controller.setMuted(muted)
            }

            override fun onSpeaking(value: Boolean) {
                speaking = value
            }

            override fun onHearing(text: String) {
                hearing = text
            }

            override fun onEvent(event: CallEvent) {
                when (event) {
                    is CallEvent.Delta -> {
                        val target = if (event.lang == "ja") jaSegments else zhSegments
                        target[event.segment] = (target[event.segment] ?: "") + event.text
                    }
                    is CallEvent.User -> {
                        hearing = ""
                        jaSegments.clear()
                        zhSegments.clear()
                    }
                    is CallEvent.Ended -> {
                        connected = false
                        speaking = false
                        callEnded = true
                        if (event.message.isNotBlank()) callError = event.message
                    }
                    is CallEvent.Error -> {
                        connected = false
                        speaking = false
                        callEnded = true
                        callError = event.message
                    }
                    is CallEvent.Emotion -> {
                        mood = event.emotion
                        if (event.nod != null) {
                            nod = event.nod
                            nodId += 1
                        } else {
                            nod = null
                        }
                    }
                }
            }

            override fun onEnded(message: String?) {
                connected = false
                reconnecting = false
                speaking = false
                callEnded = true
                if (!message.isNullOrBlank()) callError = message
            }

            override fun onError(message: String) {
                connected = false
                reconnecting = false
                speaking = false
                callEnded = true
                callError = message
            }
        }
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (!started) {
                started = true
                controller.start(conversationId, listener)
            }
        } else {
            callError = "需要麦克风权限才能通话。请在系统设置里允许麦克风后重试。"
            callEnded = true
        }
    }
    LaunchedEffect(conversationId) {
        if (conversationId.isBlank()) {
            callError = "没有找到这段对话"
            callEnded = true
            return@LaunchedEffect
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (!started) {
                started = true
                controller.start(conversationId, listener)
            }
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(Unit) {
        onDispose { controller.close() }
    }

    val done = {
        scope.launch { controller.hangUp() }
        onEnd()
    }

    val replyJa = joinedSegments(jaSegments)
    val replyZh = joinedSegments(zhSegments)
    val shownJa = replyJa.ifBlank { openerJa }
    val shownZh = if (replyJa.isNotBlank()) replyZh else openerZh
    val statusLabel = when {
        reconnecting -> "正在重新连接…"
        connected -> "通话中"
        callEnded && connectedAt != null -> "通话已结束"
        callEnded -> "未能接通"
        else -> "正在呼叫…"
    }
    val statusDetail = when {
        reconnecting -> "保持通话页，稍候"
        connected -> buildString {
            append(formatCallTime(seconds))
            maxSeconds?.let { append(" / ").append(formatCallTime(it)) }
        }
        callEnded -> "已结束"
        else -> "正在接通……"
    }

    LaunchedEffect(person?.id, stand?.outfitsUrl, outfitReload) {
        val current = person ?: return@LaunchedEffect
        val myStand = current.stand ?: return@LaunchedEffect
        selectedOutfit = myStand.defaultOutfit
        if (myStand.outfitsUrl == null) return@LaunchedEffect
        outfitsLoading = true
        outfitsError = null
        try {
            val cards = container.outfits.list(myStand)
            outfitCards = cards
            val userId = container.preferences.userId()
            val saved = userId?.let { container.outfitStore.load(it, current.id) }
            selectedOutfit = cards.asSequence()
                .flatMap { it.outfits }
                .firstOrNull { it.id == saved }
                ?: myStand.defaultOutfit
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            outfitsError = error.message ?: "衣装列表加载失败"
        } finally {
            outfitsLoading = false
        }
    }

    BackHandler { done() }
    LaunchedEffect(connected, connectedAt) {
        if (!connected) return@LaunchedEffect
        val at = connectedAt ?: return@LaunchedEffect
        while (true) {
            seconds = ((SystemClock.elapsedRealtime() - at) / 1_000).toInt()
            delay(250)
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
        if (activeOutfit != null && standPhase != StandPhase.Failed) {
            key(activeOutfit.url) {
                SpineStand(
                    outfit = activeOutfit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.80f)
                        .align(Alignment.BottomCenter),
                    speaking = speaking,
                    mood = mood,
                    nod = nod,
                    nodId = nodId,
                    onPhase = { phase ->
                        if (phase != StandPhase.Loading) standPhase = phase
                    },
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = if (standLayout) 0.28f else 0.45f),
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
            Box(Modifier.fillMaxWidth()) {
                CallStatusPill(statusLabel)
                if (stand != null) {
                    Surface(
                        onClick = { showOutfits = true },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.28f),
                        modifier = Modifier.align(Alignment.CenterEnd).size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checkroom,
                            contentDescription = "更换衣装",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            if (standLayout) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = person?.name ?: "通话",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusDetail,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "WebRTC 语音通话",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                // 立绘还没到时先显示头像，到了之后淡入立绘、头像退场。
                if (standPhase != StandPhase.Ready) {
                    PulsingAvatar(person, her.mark, connected, baseUrl)
                }
                Spacer(Modifier.weight(1f))
                CallOpener(shownJa, shownZh, connected, hearing, callError)
                Spacer(Modifier.height(16.dp))
                CallControls(
                    muted = muted,
                    speaker = speaker,
                    onMute = { muted = !muted; controller.setMuted(muted) },
                    onSpeaker = { speaker = !speaker; controller.setSpeaker(speaker) },
                    onEnd = done,
                )
                Spacer(Modifier.height(24.dp))
            } else {
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
                    text = statusDetail,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "WebRTC 语音通话",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(20.dp))
                CallOpener(shownJa, shownZh, connected, hearing, callError)
                Spacer(Modifier.weight(1f))
                CallControls(
                    muted = muted,
                    speaker = speaker,
                    onMute = { muted = !muted; controller.setMuted(muted) },
                    onSpeaker = { speaker = !speaker; controller.setSpeaker(speaker) },
                    onEnd = done,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showOutfits) {
        OutfitSheet(
            characterName = person?.name.orEmpty(),
            cards = outfitCards,
            loading = outfitsLoading,
            error = outfitsError,
            selected = activeOutfit,
            accent = her.mark,
            onSelect = { outfit ->
                selectedOutfit = outfit
                showOutfits = false
                val currentId = person?.id
                if (currentId != null) {
                    scope.launch {
                        val userId = container.preferences.userId() ?: return@launch
                        container.outfitStore.save(userId, currentId, outfit.id)
                    }
                }
            },
            onRetry = { outfitReload += 1 },
            onDismiss = { showOutfits = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutfitSheet(
    characterName: String,
    cards: List<OutfitCard>,
    loading: Boolean,
    error: String?,
    selected: Outfit?,
    accent: Color,
    onSelect: (Outfit) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    LaunchedEffect(cards, selected?.id) {
        val index = cards.indexOfFirst { card ->
            card.outfits.any { it.id == selected?.id }
        }
        if (index > 0) listState.scrollToItem(index)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF12151C),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "衣装",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (characterName.isNotBlank()) {
                Text(
                    text = characterName,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                }

                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry) { Text("重试", color = accent) }
                }

                cards.isEmpty() -> Text(
                    text = "暂时没有更多衣装",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(cards, key = { it.id }) { card ->
                        OutfitCardRow(card, selected, accent, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutfitCardRow(
    card: OutfitCard,
    selected: Outfit?,
    accent: Color,
    onSelect: (Outfit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = card.name.ifBlank { card.id },
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            card.everyday?.let { OutfitTile(it, "私服", it.id == selected?.id, accent, onSelect) }
            card.stage?.let { OutfitTile(it, "演出服", it.id == selected?.id, accent, onSelect) }
        }
    }
}

@Composable
private fun OutfitTile(
    outfit: Outfit,
    label: String,
    isSelected: Boolean,
    accent: Color,
    onSelect: (Outfit) -> Unit,
) {
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = { onSelect(outfit) },
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else Color.White.copy(alpha = 0.12f),
            ),
        ) {
            Box(
                modifier = Modifier.width(92.dp).height(116.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (outfit.thumbnail != null) {
                    AsyncImage(
                        model = outfit.thumbnail,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = "无预览",
                        color = Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) accent else Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CallStatusPill(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = Color.Black.copy(alpha = 0.28f),
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CallOpener(
    ja: String?,
    zh: String?,
    connected: Boolean,
    hearing: String,
    error: String?,
) {
    val visible = connected || error != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.42f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (error != null) {
                    Text(
                        text = error,
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = ja.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    zh?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                hearing.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "听到：$it",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControls(
    muted: Boolean,
    speaker: Boolean,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallControl(
            icon = if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
            label = if (muted) "已静音" else "静音",
            active = muted,
            onClick = onMute,
        )
        HangUpControl(onClick = onEnd)
        CallControl(
            icon = if (speaker) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
            label = "免提",
            active = speaker,
            onClick = onSpeaker,
        )
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
