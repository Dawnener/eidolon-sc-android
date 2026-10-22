package sc.eidolon.nativeapp.ui.stats

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.repo.CharacterRepository
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.CharacterStats
import sc.eidolon.nativeapp.model.Stats
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.components.LoadingState
import sc.eidolon.nativeapp.ui.components.MessageState
import kotlin.math.ceil
import kotlin.math.roundToInt

data class StatsState(
    val loading: Boolean = true,
    val stats: Stats? = null,
    val characters: Map<String, Character> = emptyMap(),
    val range: String = "all",
    val busy: Boolean = false,
    val error: String? = null,
)

class StatsViewModel(
    private val api: EidolonApi,
    private val charactersRepo: CharacterRepository,
) : ViewModel() {
    var state by mutableStateOf(StatsState())
        private set
    private var loadId = 0

    init {
        load("all")
    }

    fun load(range: String) {
        val id = ++loadId
        state = state.copy(
            range = range,
            loading = state.stats == null,
            busy = true,
            error = null,
        )
        viewModelScope.launch {
            try {
                val stats = api.stats(range)
                if (id != loadId) return@launch
                val characters = state.characters.ifEmpty {
                    runCatching { charactersRepo.all() }
                        .getOrDefault(emptyList())
                        .associateBy { it.id }
                }
                if (id != loadId) return@launch
                state = state.copy(
                    loading = false,
                    busy = false,
                    stats = stats,
                    characters = characters,
                )
            } catch (error: Exception) {
                if (id != loadId) return@launch
                state = state.copy(
                    loading = false,
                    busy = false,
                    error = error.message ?: "统计没能读取",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenConversations: () -> Unit,
) {
    val vm: StatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StatsViewModel(container.api, container.characters) }
        },
    )
    val state = vm.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val stats = state.stats
    val empty = stats != null && stats.allHours.all { it == 0 } && stats.range.characters.isEmpty()
    val shareable = stats != null &&
        !empty &&
        !state.busy &&
        stats.range.span == state.range &&
        stats.range.characters.any { it.turns > 0 }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
                    }
                },
                title = { Text("统计") },
                actions = {
                    IconButton(
                        enabled = shareable && !sharing,
                        onClick = {
                            if (stats == null) return@IconButton
                            sharing = true
                            scope.launch {
                                StatsShare.share(context, stats, state.characters)
                                    .onFailure {
                                        Toast.makeText(context, "分享图没能生成", Toast.LENGTH_SHORT).show()
                                    }
                                sharing = false
                            }
                        },
                    ) {
                        if (sharing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Share, contentDescription = "分享统计")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding)) {
                LoadingState("正在读取统计")
            }
            stats == null -> Column(Modifier.fillMaxSize().padding(padding)) {
                MessageState(
                    "统计没能读取",
                    state.error ?: "请稍后再试",
                    "重试" to { vm.load(state.range) },
                )
            }
            empty -> EmptyStats(onOpenConversations, Modifier.fillMaxSize().padding(padding))
            else -> StatsBody(
                stats = stats,
                characters = state.characters,
                baseUrl = container.session.serverUrl.orEmpty(),
                range = state.range,
                busy = state.busy,
                error = state.error,
                onRange = vm::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun StatsBody(
    stats: Stats,
    characters: Map<String, Character>,
    baseUrl: String,
    range: String,
    busy: Boolean,
    error: String?,
    onRange: (String) -> Unit,
    modifier: Modifier,
) {
    val portrait = portraitOf(stats.allHours)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure("认识", stats.daysKnown, "天", Modifier.weight(1f))
                Figure("连续来访", stats.visits.current, "天", Modifier.weight(1f))
                Figure("最长连续", stats.visits.longest, "天", Modifier.weight(1f))
            }
        }
        portrait?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        if (stats.heatmap.counts.any { it > 0 }) {
            item { HeatmapCard(stats) }
        }
        if (stats.affinity != null || stats.achievements != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stats.achievements != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Figure("成就阶段", stats.achievements.stages, "个", Modifier.weight(1f))
                            Figure("集齐角色", stats.achievements.complete, "位", Modifier.weight(1f))
                        }
                    }
                    stats.affinity
                        ?.filter { group -> group.characters.isNotEmpty() }
                        ?.reversed()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            Text("好感度", style = MaterialTheme.typography.titleMedium)
                            it.forEach { group ->
                                val names = group.characters.mapNotNull { id -> characters[id]?.name }
                                if (names.isNotEmpty()) {
                                    Text(
                                        "${tierLabel(group.tier)}：${names.joinToString("、")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEach { item ->
                    FilterChip(
                        selected = range == item.first,
                        onClick = { onRange(item.first) },
                        label = { Text(item.second) },
                    )
                }
            }
        }
        error?.let {
            item {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            val calls = stats.range.calls
            val gifts = stats.range.gifts
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure("回合", stats.range.turns, null, Modifier.weight(1f))
                Figure("通话", calls.count, "次", Modifier.weight(1f))
                Figure("通话时长", callMinutes(calls.seconds), "分", Modifier.weight(1f))
            }
            if (gifts != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Figure("送出的礼物", gifts.given, null, Modifier.weight(1f))
                    Figure("她喜欢的", gifts.liked, null, Modifier.weight(1f))
                }
            }
        }
        item {
            val ranked = stats.range.characters
                .filter { it.turns > 0 }
                .sortedByDescending { it.turns }
                .take(5)
            if (ranked.isNotEmpty()) {
                Ranking(
                    title = "聊得最多",
                    rows = ranked,
                    characters = characters,
                    baseUrl = baseUrl,
                    value = { it.turns },
                    valueText = { "${it} 回合" },
                )
            }
        }
        item {
            val ranked = stats.range.characters
                .filter { it.callSeconds > 0 }
                .sortedByDescending { it.callSeconds }
                .take(5)
            if (ranked.isNotEmpty()) {
                Ranking(
                    title = "通话最久",
                    rows = ranked,
                    characters = characters,
                    baseUrl = baseUrl,
                    value = { it.callSeconds.roundToInt() },
                    valueText = { "${callMinutes(it.toDouble())} 分" },
                )
            }
        }
        item {
            if (stats.range.byHour.any { it > 0 }) {
                HourChartCard(stats.range.byHour)
            }
        }
        stats.range.gifts?.most?.let { most ->
            item {
                val name = characters[most.characterId]?.name ?: most.characterId
                Text(
                    "礼物送得最多：$name（${most.given} 件）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (busy) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun EmptyStats(onOpenConversations: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有统计数据", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "先去和她们说说话吧。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenConversations) { Text("去对话列表") }
    }
}

@Composable
private fun Figure(label: String, value: Int, unit: String?, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (unit == null) "$value" else "$value $unit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HeatmapCard(stats: Stats) {
    val cells = stats.heatmap.counts
    val weeks = ceil(cells.size / 7.0).toInt().coerceAtLeast(1)
    val most = cells.maxOrNull() ?: 0
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("过去一年的来访", style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 2.dp
                val cell = minOf(12.dp, (maxWidth - gap * (weeks - 1)) / weeks)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(cell * 7 + gap * 6),
                ) {
                    val step = cell.toPx() + gap.toPx()
                    cells.forEachIndexed { index, count ->
                        val column = index / 7
                        val row = index % 7
                        val level = when {
                            count <= 0 || most <= 0 -> 0
                            else -> (count.toFloat() / most * 4).roundToInt().coerceIn(1, 4)
                        }
                        val color = if (level == 0) empty else primary.copy(alpha = 0.18f + level * 0.2f)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(column * step, row * step),
                            size = Size(cell.toPx(), cell.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Ranking(
    title: String,
    rows: List<CharacterStats>,
    characters: Map<String, Character>,
    baseUrl: String,
    value: (CharacterStats) -> Int,
    valueText: (Int) -> String,
) {
    val most = rows.maxOfOrNull(value)?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            val character = characters[row.characterId]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (character != null) {
                    CharacterAvatar(
                        name = character.name,
                        color = character.color,
                        url = character.avatarUrl?.let { baseUrl + it },
                        size = 30.dp,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row {
                        Text(
                            character?.name ?: row.characterId,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            valueText(value(row)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((value(row).toFloat() / most).coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourChartCard(hours: List<Int>) {
    val most = hours.maxOrNull()?.coerceAtLeast(1) ?: 1
    val primary = MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("一天里的往来", style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(92.dp)) {
                val slot = size.width / 24f
                val bar = slot * 0.62f
                hours.take(24).forEachIndexed { index, count ->
                    val height = size.height * (count.toFloat() / most)
                    drawRoundRect(
                        color = if (count == 0) primary.copy(alpha = 0.14f) else primary.copy(alpha = 0.75f),
                        topLeft = Offset(index * slot + (slot - bar) / 2f, size.height - height),
                        size = Size(bar, height.coerceAtLeast(2.dp.toPx())),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("18", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("23", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val RANGES = listOf(
    "30d" to "近 30 天",
    "1y" to "近一年",
    "all" to "全部",
)

private fun callMinutes(seconds: Double): Int =
    if (seconds <= 0) 0 else maxOf(1, (seconds / 60).roundToInt())

private fun tierLabel(tier: String): String = when (tier) {
    "intimate" -> "亲密"
    "close" -> "亲近"
    "trust" -> "信任"
    "distant" -> "疏远"
    "cold" -> "冷淡"
    else -> tier
}

private fun portraitOf(hours: List<Int>): String? {
    if (hours.none { it > 0 }) return null
    val parts = listOf(
        "夜里" to hours.take(6).sum(),
        "早上" to hours.drop(6).take(6).sum(),
        "下午" to hours.drop(12).take(6).sum(),
        "晚上" to hours.drop(18).take(6).sum(),
    )
    val most = parts.maxOf { it.second }
    val busiest = parts.filter { it.second == most }.map { it.first }
    return when {
        busiest.size > 2 -> "你似乎什么时间都会来。"
        else -> "你常在${busiest.joinToString("和")}来。"
    }
}
