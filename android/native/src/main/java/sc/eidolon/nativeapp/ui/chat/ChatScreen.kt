package sc.eidolon.nativeapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.ChatLine
import sc.eidolon.nativeapp.model.Entry
import sc.eidolon.nativeapp.model.EventKind
import sc.eidolon.nativeapp.model.Failure
import sc.eidolon.nativeapp.model.MessageLines
import sc.eidolon.nativeapp.model.PhotoStatus
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.components.LoadingState
import sc.eidolon.nativeapp.ui.components.MessageState
import sc.eidolon.nativeapp.ui.components.SharedAvatarModifier
import sc.eidolon.nativeapp.ui.theme.HerBubbleDark
import sc.eidolon.nativeapp.ui.theme.ProducerInk
import sc.eidolon.nativeapp.ui.theme.ProducerInkDark
import sc.eidolon.nativeapp.ui.theme.ProducerInkText
import sc.eidolon.nativeapp.ui.theme.ProducerInkTextDark
import sc.eidolon.nativeapp.ui.theme.herColors
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    characterId: String,
    avatarModifier: SharedAvatarModifier = { Modifier },
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: ChatViewModel = viewModel(
        key = characterId,
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    characterId = characterId,
                    charactersRepo = container.characters,
                    conversationsRepo = container.conversations,
                    ownKeysStore = container.ownKeysStore,
                    accountId = { container.session.account?.id },
                )
            }
        },
    )
    val state = vm.state
    val baseUrl = container.session.serverUrl.orEmpty()
    val her = herColors(state.character?.color ?: "#7FB4FF")
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }
    val zone = remember { Time.zone() }
    val cooling = state.coolingUntil?.isAfter(now) == true

    Scaffold(
        // 外层 MainScaffold 已经为系统状态栏/导航栏留过边距，并在根上处理 IME 抬起
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回对话列表")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.character?.let {
                            CharacterAvatar(
                                name = it.name,
                                color = it.color,
                                url = it.avatarUrl?.let { path -> baseUrl + path },
                                size = 32.dp,
                                modifier = avatarModifier(characterId),
                            )
                        }
                        Text(
                            text = state.character?.name ?: "对话",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> Box(Modifier.weight(1f)) { LoadingState("正在打开对话") }
                state.openError != null -> Box(Modifier.weight(1f)) {
                    MessageState(
                        "对话没能打开",
                        state.openError,
                        "重试" to { vm.retryOpen() },
                    )
                }
                else -> {
                    MessageList(
                        state = state,
                        her = her,
                        baseUrl = baseUrl,
                        now = now,
                        zone = zone,
                        modifier = Modifier.weight(1f),
                    )
                    Composer(
                        state = state,
                        name = state.character?.name ?: "她",
                        cooling = cooling,
                        zone = zone,
                        onDraft = vm::onDraftChange,
                        onSend = vm::send,
                        onNewConversation = vm::newConversation,
                        onOpenSettings = onOpenSettings,
                        onDismissFailure = vm::dismissFailure,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageList(
    state: ChatState,
    her: sc.eidolon.nativeapp.ui.theme.HerColors,
    baseUrl: String,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier,
) {
    val lines = MessageLines.linesOf(state.log.entries, now, zone)
    // 一进来就定位到最后一条，避免长对话先显示最上面再跳到底部
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = lines.lastIndex.coerceAtLeast(0),
    )
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.conversationId) {
        initialScrollDone = false
    }
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(lines.size, state.log.entries, initialScrollDone) {
        if (lines.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone || nearBottom) {
            // 等列表完成一次布局再滚，避免首次进来停在最上面
            withFrameNanos { }
            listState.scrollToItem(lines.lastIndex)
            initialScrollDone = true
        }
    }
    // 输入法弹出时内容区是从底部压缩的，列表的滚动位置不会自己跟着变，
    // 最新几条就会落到键盘后面。可视区域高度一变就重新贴到底部。
    val viewportHeight by remember {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }
    LaunchedEffect(viewportHeight) {
        if (viewportHeight > 0 && lines.isNotEmpty()) {
            // requestScrollToItem 在下一轮测量时直接生效，不再等一帧，
            // 这样列表和输入法抬起的动画同步，不会慢半拍
            listState.requestScrollToItem(lines.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(her.wall)) {
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "和${state.character?.name ?: "她"}打个招呼吧",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Box
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = lines,
                key = { line ->
                    when (line) {
                        is ChatLine.Day -> "day-${line.date}"
                        is ChatLine.Item -> line.entry.key
                    }
                },
            ) { line ->
                when (line) {
                    is ChatLine.Day -> DayPill(line.label)
                    is ChatLine.Item -> when (val entry = line.entry) {
                        is Entry.User -> UserBubble(entry, line.last, zone)
                        is Entry.Character -> CharacterBubble(
                            entry = entry,
                            character = state.character,
                            first = line.first,
                            last = line.last,
                            baseUrl = baseUrl,
                            zone = zone,
                        )
                        is Entry.Event -> EventLine(
                            entry = entry,
                            name = state.character?.name ?: "她",
                            last = line.last,
                            zone = zone,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPill(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserBubble(entry: Entry.User, last: Boolean, zone: ZoneId) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = if (dark) ProducerInkDark else ProducerInk
    val foreground = if (dark) ProducerInkTextDark else ProducerInkText
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(background)
                    .padding(12.dp),
            ) {
                Text(
                    text = entry.text,
                    color = foreground,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (last) {
                Text(
                    text = Time.formatClock(entry.at, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CharacterBubble(
    entry: Entry.Character,
    character: sc.eidolon.nativeapp.model.Character?,
    first: Boolean,
    last: Boolean,
    baseUrl: String,
    zone: ZoneId,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (first && character != null) {
            CharacterAvatar(
                name = character.name,
                color = character.color,
                url = character.avatarUrl?.let { baseUrl + it },
                size = 36.dp,
            )
        } else {
            Spacer(Modifier.width(44.dp))
        }
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            if (first && character != null) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (dark) HerBubbleDark else MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.segments.isEmpty()) {
                        Text(
                            text = "…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        entry.segments.forEach { segment ->
                            if (segment.ja.isNotBlank()) {
                                Text(
                                    text = segment.ja,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                    ),
                                )
                            }
                            if (segment.zh.isNotBlank()) {
                                Text(
                                    text = segment.zh,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    entry.photo?.let { photo ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (photo.status) {
                                    PhotoStatus.Pending -> "照片正在生成"
                                    PhotoStatus.Failed -> "照片没发出来"
                                    PhotoStatus.Ready -> "照片"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (last) {
                Text(
                    text = Time.formatClock(entry.at, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EventLine(entry: Entry.Event, name: String, last: Boolean, zone: ZoneId) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (entry.kind) {
                EventKind.HungUp -> "$name 挂断了电话"
                EventKind.Gift -> "你送给 $name 一份${entry.giftName ?: "礼物"}"
                EventKind.Ended -> "$name 结束了对话"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (last) {
            Text(
                text = Time.formatClock(entry.at, zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Composer(
    state: ChatState,
    name: String,
    cooling: Boolean,
    zone: ZoneId,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissFailure: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.failure?.let { failure ->
                FailureBanner(failure, zone, onOpenSettings, onDismissFailure)
            }
            when {
                state.ended -> EndedCard(
                    name = name,
                    coolingUntil = state.coolingUntil,
                    zone = zone,
                    onNewConversation = onNewConversation,
                )
                cooling -> Text(
                    text = "$name 现在不想说话。${
                        state.coolingUntil?.let { Time.formatClock(it, zone) }.orEmpty()
                    } 以后可以再找她",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
                else -> {
                    var input by remember { mutableStateOf(TextFieldValue(state.draft)) }
                    LaunchedEffect(state.draft) {
                        if (input.text != state.draft) {
                            input = TextFieldValue(state.draft, selection = TextRange(state.draft.length))
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                onDraft(it.text)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        if (event.isShiftPressed) {
                                            false
                                        } else {
                                            if (input.composition == null) onSend()
                                            true
                                        }
                                    } else {
                                        false
                                    }
                                },
                            placeholder = { Text("用中文或日语说点什么") },
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { if (input.composition == null) onSend() },
                            ),
                        )
                        IconButton(
                            onClick = onSend,
                            enabled = !state.busy && input.text.isNotBlank() && !state.ended && !cooling,
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.Send, contentDescription = "发送")
                            }
                        }
                    }
                    val count = input.text.codePointCount(0, input.text.length)
                    if (count > 1800) {
                        Text(
                            text = "$count / 2000",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureBanner(
    failure: Failure,
    zone: ZoneId,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(failure.text)
                    failure.until?.let { append("（${Time.formatClock(it, zone)} 之后可以再试）") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (failure.toOwnKeys) {
                TextButton(onClick = onOpenSettings) { Text("去设置自带密钥") }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
    }
}

@Composable
private fun EndedCard(
    name: String,
    coolingUntil: Instant?,
    zone: ZoneId,
    onNewConversation: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$name 结束了对话",
            style = MaterialTheme.typography.bodyMedium,
        )
        coolingUntil?.let {
            Text(
                text = "${Time.formatClock(it, zone)} 以后可以再找她",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onNewConversation) { Text("开个新对话") }
    }
}

private fun Color.luminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
