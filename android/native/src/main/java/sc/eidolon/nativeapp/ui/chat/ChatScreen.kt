package sc.eidolon.nativeapp.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.ChatLine
import sc.eidolon.nativeapp.model.Conversation
import sc.eidolon.nativeapp.model.Entry
import sc.eidolon.nativeapp.model.EventKind
import sc.eidolon.nativeapp.model.Failure
import sc.eidolon.nativeapp.model.LastPreview
import sc.eidolon.nativeapp.model.MessageLines
import sc.eidolon.nativeapp.model.PhotoStatus
import sc.eidolon.nativeapp.model.cueText
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.components.LoadingState
import sc.eidolon.nativeapp.ui.components.MessageState
import sc.eidolon.nativeapp.ui.components.SharedAvatarModifier
import sc.eidolon.nativeapp.ui.gifts.GiftArt
import sc.eidolon.nativeapp.ui.gifts.GiftThumb
import sc.eidolon.nativeapp.ui.theme.HerBubbleDark
import sc.eidolon.nativeapp.ui.theme.ProducerInk
import sc.eidolon.nativeapp.ui.theme.ProducerInkDark
import sc.eidolon.nativeapp.ui.theme.ProducerInkText
import sc.eidolon.nativeapp.ui.theme.ProducerInkTextDark
import sc.eidolon.nativeapp.ui.theme.herColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ChatPhase { Loading, Error, Chat }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    characterId: String,
    avatarModifier: SharedAvatarModifier = { Modifier },
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGifts: (String) -> Unit,
) {
    val vm: ChatViewModel = viewModel(
        key = characterId,
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    characterId = characterId,
                    charactersRepo = container.characters,
                    conversationsRepo = container.conversations,
                    giftsRepo = container.gifts,
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
    var showHistory by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val viewing = state.viewing
    BackHandler(enabled = viewing != null) { vm.backToCurrent() }
    LifecycleResumeEffect(Unit) {
        vm.refreshOnResume()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.rewardNotice) {
        val notice = state.rewardNotice ?: return@LaunchedEffect
        snackbar.showSnackbar(notice)
        vm.dismissRewardNotice()
    }
    LaunchedEffect(state.affinityCue?.id) {
        if (state.affinityCue == null) return@LaunchedEffect
        delay(2_600)
        vm.clearAffinityCue()
    }
    val displayState = viewing?.let {
        state.copy(
            log = it.log,
            conversationId = it.conversation.id,
            ended = it.ended,
        )
    } ?: state

    Scaffold(
        // 外层 MainScaffold 已经为系统状态栏/导航栏留过边距，并在根上处理 IME 抬起
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(
                        onClick = { if (viewing != null) vm.backToCurrent() else onBack() },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (viewing != null) "回到当前对话" else "返回对话列表",
                        )
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
                        Column {
                            Text(
                                text = state.character?.name ?: "对话",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (viewing != null) {
                                Text(
                                    text = "历史对话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                state.affinity?.let { affinity ->
                                    AffinityBadge(
                                        name = state.character?.name ?: "她",
                                        color = state.character?.color ?: "#7FB4FF",
                                        status = affinity,
                                        change = state.affinityCue?.change,
                                        now = now,
                                        zone = zone,
                                        onGiveGift = { onOpenGifts(characterId) },
                                        onAchievements = {
                                            vm.loadHerAchievements()
                                            showAchievements = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (viewing == null && state.affinity != null) {
                        IconButton(onClick = { onOpenGifts(characterId) }) {
                            Icon(Icons.Outlined.CardGiftcard, contentDescription = "送她礼物")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("新对话") },
                                enabled = !state.busy &&
                                    state.log.entries.isNotEmpty() &&
                                    !state.loading,
                                onClick = {
                                    menuOpen = false
                                    showHistory = false
                                    if (viewing != null) vm.backToCurrent()
                                    vm.newConversation()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("历史对话") },
                                onClick = {
                                    menuOpen = false
                                    vm.loadHistory()
                                    showHistory = true
                                },
                            )
                            if (state.affinity != null) {
                                DropdownMenuItem(
                                    text = { Text("成就") },
                                    onClick = {
                                        menuOpen = false
                                        vm.loadHerAchievements()
                                        showAchievements = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = when {
                state.loading -> ChatPhase.Loading
                state.openError != null -> ChatPhase.Error
                else -> ChatPhase.Chat
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 30 }) togetherWith
                    fadeOut(tween(160))
            },
            label = "chat-content",
        ) { phase ->
            when (phase) {
                ChatPhase.Loading -> Box(Modifier.fillMaxSize()) {
                    LoadingState("正在打开对话")
                }
                ChatPhase.Error -> Box(Modifier.fillMaxSize()) {
                    MessageState(
                        "对话没能打开",
                        state.openError,
                        "重试" to { vm.retryOpen() },
                    )
                }
                ChatPhase.Chat -> Column(Modifier.fillMaxSize()) {
                    MessageList(
                        state = displayState,
                        her = her,
                        baseUrl = baseUrl,
                        now = now,
                        zone = zone,
                        affinityOn = state.affinity != null,
                        modifier = Modifier.weight(1f),
                    )
                    if (viewing != null) {
                        PastConversationFooter(
                            ended = viewing.ended,
                            onBackToCurrent = vm::backToCurrent,
                        )
                    } else {
                        Composer(
                            state = state,
                            name = state.character?.name ?: "她",
                            cooling = cooling,
                            zone = zone,
                            onDraft = vm::onDraftChange,
                            onSend = vm::send,
                            onNewConversation = vm::newConversation,
                            onOpenSettings = onOpenSettings,
                            onOpenGifts = { onOpenGifts(characterId) },
                            onDismissFailure = vm::dismissFailure,
                        )
                    }
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                Text(
                    text = "历史对话",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (state.historyLoading) {
                    Text(
                        text = "正在读取历史对话…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.history.isEmpty()) {
                    Text(
                        text = "还没有历史对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(state.history, key = { it.id }) { conversation ->
                            HistoryRow(
                                conversation = conversation,
                                current = conversation.id == state.conversationId,
                                viewing = conversation.id == viewing?.conversation?.id,
                                zone = zone,
                                onClick = {
                                    showHistory = false
                                    vm.viewPast(conversation)
                                },
                                onDelete = { pendingDelete = conversation },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showAchievements) {
        HerAchievementsSheet(
            state = state,
            onDismiss = { showAchievements = false },
            onRetry = vm::loadHerAchievements,
        )
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这段历史对话？") },
            text = { Text("删除后无法恢复。她当前正在使用的那段对话不能删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.deleteConversation(conversation)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PastConversationFooter(ended: Boolean, onBackToCurrent: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("这是你们的历史对话", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (ended) {
                        "她已经结束了这段对话，不能再回复。"
                    } else {
                        "回到当前对话才能继续聊天。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBackToCurrent) { Text("回到当前对话") }
        }
    }
}

@Composable
private fun HistoryRow(
    conversation: Conversation,
    current: Boolean,
    viewing: Boolean,
    zone: ZoneId,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = conversation.title ?: previewOf(conversation.lastMessage) ?: "新的对话",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (current) {
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (viewing) {
                    Text(
                        text = "正在看",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val at = conversation.lastMessage?.at ?: conversation.createdAt
            Text(
                text = buildString {
                    append(at.atZone(zone).format(HISTORY_TIME))
                    previewOf(conversation.lastMessage)?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!current) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除这段历史对话")
            }
        }
    }
}

private val HISTORY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun previewOf(last: LastPreview?): String? = when {
    last == null -> null
    !last.text.isNullOrBlank() -> last.text
    !last.zh.isNullOrBlank() -> last.zh
    !last.ja.isNullOrBlank() -> last.ja
    last.reachOut -> "她主动搭话了"
    last.photo -> "照片"
    last.eventKind != null -> "对话事件"
    else -> null
}

@Composable
private fun MessageList(
    state: ChatState,
    her: sc.eidolon.nativeapp.ui.theme.HerColors,
    baseUrl: String,
    now: Instant,
    zone: ZoneId,
    affinityOn: Boolean,
    modifier: Modifier,
) {
    val lines = MessageLines.linesOf(state.log.entries, now, zone)
    // 一进来就定位到最后一条，避免长对话先显示最上面再跳到底部
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = lines.lastIndex.coerceAtLeast(0),
    )
    var initialScrollDone by remember { mutableStateOf(false) }
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    // 用户最后一次停止滚动时是否在底部。输入法改变可视高度时只据此决定要不要贴底，
    // 不会把正在翻看历史的人强行拉回最新一条。
    var userAtBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                // 滚动过程中一直更新，翻到顶部后立刻点输入框也不会误判
                snapshotFlow { nearBottom }.collect { userAtBottom = it }
            } else {
                withFrameNanos { }
                userAtBottom = nearBottom
            }
        }
    }
    LaunchedEffect(state.conversationId) {
        initialScrollDone = false
    }
    // 发送后平滑滚到最新一条：按用户消息数量触发，流式回复增加行不会打断这段动画。
    val userMessages = lines.count { it is ChatLine.Item && it.entry is Entry.User }
    var seenUserMessages by remember(state.conversationId) { mutableStateOf(userMessages) }
    LaunchedEffect(userMessages) {
        if (userMessages > seenUserMessages && initialScrollDone) {
            seenUserMessages = userMessages
            withFrameNanos { }
            listState.animateScrollToItem(lines.lastIndex)
            userAtBottom = true
        }
    }
    LaunchedEffect(lines.size, state.log.entries, initialScrollDone) {
        if (lines.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone || nearBottom) {
            // 等列表完成一次布局再滚，避免首次进来停在最上面
            withFrameNanos { }
            listState.scrollToItem(lines.lastIndex)
            initialScrollDone = true
            userAtBottom = true
        }
    }
    // 输入法弹出时内容区是从底部压缩的，列表的滚动位置不会自己跟着变，
    // 最新几条就会落到键盘后面。可视区域高度一变就重新贴到底部。
    val viewportHeight by remember {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }
    LaunchedEffect(viewportHeight) {
        if (viewportHeight > 0 && lines.isNotEmpty() && userAtBottom) {
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
                            affinityOn = affinityOn,
                        )
                        is Entry.Event -> EventLine(
                            entry = entry,
                            name = state.character?.name ?: "她",
                            last = line.last,
                            baseUrl = baseUrl,
                            zone = zone,
                            affinityOn = affinityOn,
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 短消息按内容自适应；长消息最多占这一行的 78%，再受 520dp 限制。
        val bubbleMax = minOf(maxWidth * 0.78f, 520.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                modifier = Modifier.widthIn(max = bubbleMax),
                horizontalAlignment = Alignment.End,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = bubbleMax)
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
}

@Composable
private fun CharacterBubble(
    entry: Entry.Character,
    character: sc.eidolon.nativeapp.model.Character?,
    first: Boolean,
    last: Boolean,
    baseUrl: String,
    zone: ZoneId,
    affinityOn: Boolean,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 短回复按内容自适应；长回复最多占这一行的 82%，再受 520dp 限制。
        val bubbleMax = minOf(maxWidth * 0.82f, 520.dp)
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
                        .widthIn(max = bubbleMax)
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
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                        ),
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
                if (affinityOn) {
                    entry.affinity?.let { change ->
                        cueText(change, character?.name ?: "她")?.let { CueText(it) }
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
}

@Composable
private fun EventLine(
    entry: Entry.Event,
    name: String,
    last: Boolean,
    baseUrl: String,
    zone: ZoneId,
    affinityOn: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (entry.kind == EventKind.Gift && entry.giftId != null) {
                GiftThumb(baseUrl, entry.giftId, Modifier.size(32.dp))
            }
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
        }
        if (affinityOn) {
            entry.affinity?.let { change ->
                cueText(change, name)?.let { CueText(it) }
            }
        }
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
private fun CueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
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
    onOpenGifts: () -> Unit,
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
                    onOpenGifts = if (state.affinity != null) onOpenGifts else null,
                )
                cooling -> Column(Modifier.padding(8.dp)) {
                    Text(
                        text = "$name 现在不想说话。${
                            state.coolingUntil?.let { Time.formatClock(it, zone) }.orEmpty()
                        } 以后可以再找她",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.affinity != null) {
                        TextButton(onClick = onOpenGifts) { Text("送她一件礼物") }
                    }
                }
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
    onOpenGifts: (() -> Unit)?,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onNewConversation) { Text("开个新对话") }
            onOpenGifts?.let { TextButton(onClick = it) { Text("送她一件礼物") } }
        }
    }
}

private fun Color.luminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
