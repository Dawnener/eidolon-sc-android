package sc.eidolon.nativeapp.ui.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.Achievement
import sc.eidolon.nativeapp.model.BackpackItem
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.DailyTasks
import sc.eidolon.nativeapp.model.DrawItem
import sc.eidolon.nativeapp.model.GiftCategory
import sc.eidolon.nativeapp.model.GiftCharacter
import sc.eidolon.nativeapp.model.GiftInfo
import sc.eidolon.nativeapp.model.GiftState
import sc.eidolon.nativeapp.model.ResettableCharacter
import sc.eidolon.nativeapp.model.achievementRewardText
import sc.eidolon.nativeapp.model.dailyRenewText
import sc.eidolon.nativeapp.model.dailyTaskName
import sc.eidolon.nativeapp.model.label
import sc.eidolon.nativeapp.model.nextDrawText
import sc.eidolon.nativeapp.model.tierLabel
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.components.LoadingState
import sc.eidolon.nativeapp.ui.components.MessageState
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftsScreen(
    container: AppContainer,
    preselectedCharacterId: String?,
    onOpenConversations: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val vm: GiftsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GiftsViewModel(container.gifts, container.characters) }
        },
    )
    LaunchedEffect(Unit) { vm.start(preselectedCharacterId) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val state = vm.state
    val baseUrl = container.session.serverUrl.orEmpty()
    val zone = remember { Time.zone() }
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    var showReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 用 Unit 做 key：consumeNotice 把 state.notice 清空时不会取消正在显示的 Snackbar；
        // 页面离开时通知已经消费，返回后不会再弹一次。
        snapshotFlow { vm.state.notice }.filterNotNull().collect { notice ->
            vm.consumeNotice()
            snackbar.showSnackbar(notice)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("礼物") },
                actions = {
                    IconButton(onClick = { vm.refresh(quiet = false) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新礼物")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading && state.giftState == null -> Box(
                Modifier.fillMaxSize().padding(padding),
            ) { LoadingState("正在读取礼物") }
            state.giftState == null -> Box(Modifier.fillMaxSize().padding(padding)) {
                MessageState("礼物没能读取", state.error, "重试" to { vm.load() })
            }
            state.closed -> ClosedGifts(Modifier.fillMaxSize().padding(padding))
            else -> GiftsBody(
                state = state,
                baseUrl = baseUrl,
                now = now,
                zone = zone,
                onSelectCategory = vm::selectCategory,
                onClearPicked = vm::clearPicked,
                onDraw = vm::draw,
                onOpenGive = vm::openGive,
                onUseReset = { showReset = true },
                onOpenConversations = onOpenConversations,
                onPickGiftForTask = {
                    state.giftState?.backpack?.firstOrNull()?.giftId?.let(vm::openGive)
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    state.selectedGift?.let { gift ->
        ModalBottomSheet(onDismissRequest = vm::closeGive) {
            GivePanel(
                gift = gift,
                count = state.countOf(gift.id),
                status = state.giftState,
                characters = state.characters,
                baseUrl = baseUrl,
                pickedCharacterId = state.pickedCharacterId,
                sending = state.giving,
                onSend = { characterId -> vm.give(characterId) },
            )
        }
    }

    if (state.drawing || state.drawResult != null) {
        DrawResultDialog(
            state = state,
            baseUrl = baseUrl,
            onClose = vm::closeDrawResult,
            onDrawAgain = { vm.draw(false) },
            onGive = { giftId ->
                vm.closeDrawResult()
                vm.openGive(giftId)
            },
        )
    }

    if (showReset) {
        val resettable = state.giftState?.resettable.orEmpty()
        ResetDialog(
            characters = state.characters,
            resettable = resettable,
            resetting = state.resetting,
            onDismiss = { showReset = false },
            onConfirm = {
                showReset = false
                vm.useResetCard()
            },
        )
    }
}

@Composable
private fun GiftsBody(
    state: GiftsUiState,
    baseUrl: String,
    now: Instant,
    zone: ZoneId,
    onSelectCategory: (GiftCategory?) -> Unit,
    onClearPicked: () -> Unit,
    onDraw: (Boolean) -> Unit,
    onOpenGive: (String) -> Unit,
    onUseReset: () -> Unit,
    onOpenConversations: () -> Unit,
    onPickGiftForTask: () -> Unit,
    modifier: Modifier,
) {
    val gifts = state.giftState ?: return
    val picked = state.pickedCharacter
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (picked != null) {
            item("picked") {
                PickedBanner(
                    character = picked,
                    baseUrl = baseUrl,
                    onClear = onClearPicked,
                )
            }
        }
        item("draw") {
            DrawCard(
                state = gifts,
                now = now,
                zone = zone,
                drawing = state.drawing,
                onDraw = onDraw,
            )
        }
        gifts.dailyTasks?.let { tasks ->
            item("daily") {
                DailyTasksCard(
                    tasks = tasks,
                    now = now,
                    zone = zone,
                    justDone = state.justDoneTasks,
                    onOpenConversations = onOpenConversations,
                    onPickGift = onPickGiftForTask,
                )
            }
        }
        gifts.achievements?.takeIf { it.isNotEmpty() }?.let { achievements ->
            item("achievements") {
                AchievementsCard(achievements)
            }
        }
        if (gifts.resetCards > 0) {
            item("reset") {
                ResetCardStrip(
                    count = gifts.resetCards,
                    resettable = gifts.resettable,
                    characters = state.characters,
                    baseUrl = baseUrl,
                    resetting = state.resetting,
                    onUse = onUseReset,
                )
            }
        }
        item("backpack-title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "我的礼物",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${gifts.backpack.sumOf { it.count }} 件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("filters") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("全部") },
                )
                GiftCategory.entries.filter { it != GiftCategory.Unknown }.forEach { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.label()) },
                    )
                }
            }
        }
        if (state.filteredGifts.isEmpty()) {
            item("backpack-empty") {
                Text(
                    if (gifts.backpack.isEmpty()) "还没有礼物。明天会发新的抽奖次数。"
                    else "这一类还没有礼物。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.filteredGifts.chunked(3), key = { row -> row.joinToString(",") { it.id } }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { gift ->
                        GiftTile(
                            gift = gift,
                            count = state.countOf(gift.id),
                            baseUrl = baseUrl,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenGive(gift.id) },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item("bottom-space") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PickedBanner(character: Character, baseUrl: String, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CharacterAvatar(
                name = character.name,
                color = character.color,
                url = character.avatarUrl?.let { baseUrl + it },
                size = 36.dp,
            )
            Text(
                text = "给${character.name}挑一件礼物",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("取消") }
        }
    }
}

@Composable
private fun DrawCard(
    state: GiftState,
    now: Instant,
    zone: ZoneId,
    drawing: Boolean,
    onDraw: (Boolean) -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("抽奖", style = MaterialTheme.typography.titleMedium)
                    Text(
                        nextDrawText(state.nextDrawAt, now, zone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "还有 ${state.draws} 次",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(state.maxDraws.coerceAtLeast(0)) { index ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(
                                if (index < state.draws) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                CircleShape,
                            ),
                    )
                }
                if (state.draws > state.maxDraws) {
                    Text(
                        "+${state.draws - state.maxDraws}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDraw(false) },
                    enabled = state.draws > 0 && !drawing,
                    modifier = Modifier.weight(1f),
                ) { Text("抽一次") }
                if (state.draws >= 2) {
                    Button(
                        onClick = { onDraw(true) },
                        enabled = !drawing,
                        modifier = Modifier.weight(1f),
                    ) { Text("全部抽完") }
                }
            }
        }
    }
}

@Composable
private fun DailyTasksCard(
    tasks: DailyTasks,
    now: Instant,
    zone: ZoneId,
    justDone: Set<String>,
    onOpenConversations: () -> Unit,
    onPickGift: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "每日任务",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${tasks.doneCount}/${tasks.tasks.size} · ${dailyRenewText(tasks.renewsAt, now, zone)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tasks.allDone) {
                Text(
                    "今天的每日任务都做完了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tasks.tasks.forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (task.done) Icons.Outlined.CheckCircle
                            else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (task.done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                dailyTaskName(task.id),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!task.done && task.goal > 1) {
                                Text(
                                    "${task.progress}/${task.goal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "抽奖 ×${task.draws}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!task.done && (task.id == "one_turn" || task.id == "ten_turns")) {
                            TextButton(onClick = onOpenConversations) { Text("去对话") }
                        } else if (!task.done && task.id == "one_gift") {
                            TextButton(onClick = onPickGift) { Text("挑一件") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsCard(achievements: List<Achievement>) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("成就", style = MaterialTheme.typography.titleMedium)
            achievements.forEach { AchievementRow(it) }
        }
    }
}

@Composable
private fun ResetCardStrip(
    count: Int,
    resettable: List<ResettableCharacter>,
    characters: List<Character>,
    baseUrl: String,
    resetting: Boolean,
    onUse: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GiftArt(baseUrl = baseUrl, gift = null, resetCard = true, modifier = Modifier.size(52.dp))
            Column(Modifier.weight(1f)) {
                Text("重置卡 ×$count", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (resettable.isEmpty()) {
                        "现在没有低于信任的角色，留着以后用。"
                    } else {
                        "会把 ${resettable.size} 位角色拉回信任。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onUse, enabled = resettable.isNotEmpty() && !resetting) {
                Text("使用")
            }
        }
    }
}

@Composable
private fun GiftTile(
    gift: GiftInfo,
    count: Int,
    baseUrl: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            GiftArt(
                baseUrl = baseUrl,
                gift = gift,
                modifier = Modifier.fillMaxWidth().height(92.dp),
            )
            if (count > 1) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Text(
                        "×$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(gift.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            gift.rarity.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GivePanel(
    gift: GiftInfo,
    count: Int,
    status: GiftState?,
    characters: List<Character>,
    baseUrl: String,
    pickedCharacterId: String?,
    sending: Boolean,
    onSend: (String) -> Unit,
) {
    val ordered = remember(characters, pickedCharacterId) {
        characters.sortedByDescending { it.id == pickedCharacterId }
    }
    var selected by remember(gift.id) { mutableStateOf(pickedCharacterId) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GiftArt(baseUrl = baseUrl, gift = gift, modifier = Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                Text(gift.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${gift.rarity.label()} · 还有 $count 件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("送给谁？", style = MaterialTheme.typography.titleSmall)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ordered, key = { it.id }) { character ->
                val info = status?.characters?.firstOrNull { it.id == character.id }
                RecipientTile(
                    character = character,
                    baseUrl = baseUrl,
                    info = info,
                    selected = selected == character.id,
                    onClick = {
                        selected = character.id
                    },
                )
            }
        }
        Button(
            onClick = { selected?.let(onSend) },
            enabled = selected != null && count > 0 && !sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(selected?.let { id -> "送给${characters.firstOrNull { it.id == id }?.name ?: id}" } ?: "选一位")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RecipientTile(
    character: Character,
    baseUrl: String,
    info: GiftCharacter?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cooling = info?.cooling == true
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp),
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CharacterAvatar(
            name = character.name,
            color = character.color,
            url = character.avatarUrl?.let { baseUrl + it },
            size = 42.dp,
        )
        Text(
            character.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (cooling) "冷却中" else tierLabel(info?.tier).ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = if (cooling) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun DrawResultDialog(
    state: GiftsUiState,
    baseUrl: String,
    onClose: () -> Unit,
    onDrawAgain: () -> Unit,
    onGive: (String) -> Unit,
) {
    val result = state.drawResult
    AlertDialog(
        onDismissRequest = { if (!state.drawing) onClose() },
        title = { Text(if (result == null) "正在打开礼物盒" else "抽奖结果") },
        text = {
            if (result == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (result.drawn.isEmpty()) {
                        Text("这次什么也没有抽到。")
                    } else if (result.drawn.size == 1) {
                        val item = result.drawn.single()
                        DrawItemCard(item, state.catalog, baseUrl)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.heightIn(max = 320.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(result.drawn) { item ->
                                DrawItemCard(item, state.catalog, baseUrl)
                            }
                        }
                    }
                    state.drawError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (result != null && !state.drawing) {
                val singleGift = result.drawn.singleOrNull() as? DrawItem.Gift
                if (singleGift != null) {
                    TextButton(onClick = { onGive(singleGift.giftId) }) { Text("送给谁…") }
                }
                if (singleGift != null && result.draws > 0) {
                    TextButton(onClick = onDrawAgain) { Text("再抽一次") }
                }
                TextButton(onClick = onClose) { Text("收下") }
            }
        },
    )
}

@Composable
private fun DrawItemCard(item: DrawItem, catalog: List<GiftInfo>, baseUrl: String) {
    when (item) {
        is DrawItem.Gift -> {
            val gift = catalog.firstOrNull { it.id == item.giftId }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GiftArt(baseUrl = baseUrl, gift = gift, modifier = Modifier.size(88.dp))
                Text(
                    gift?.name ?: item.giftId,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DrawItem.ResetCard -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GiftArt(baseUrl = baseUrl, gift = null, resetCard = true, modifier = Modifier.size(88.dp))
                Text("重置卡", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ResetDialog(
    characters: List<Character>,
    resettable: List<ResettableCharacter>,
    resetting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!resetting) onDismiss() },
        title = { Text("使用一张重置卡？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("以下角色会回到「信任」，冷却也会解除：")
                resettable.forEach { item ->
                    val name = characters.firstOrNull { it.id == item.id }?.name ?: item.id
                    Row {
                        Text(name, modifier = Modifier.weight(1f))
                        Text(
                            "${if (item.cooling) "冷却中" else tierLabel(item.tier)} → 信任",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !resetting) { Text("使用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !resetting) { Text("取消") }
        },
    )
}

@Composable
private fun ClosedGifts(modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CardGiftcard,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("礼物暂时没有开放", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "好感度、礼物和每日任务现在没有开启。已经拥有的礼物和重置卡会保留。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
