package sc.eidolon.nativeapp.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.ConversationRow
import sc.eidolon.nativeapp.model.EventKind
import sc.eidolon.nativeapp.model.Preview
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.components.LoadingState
import sc.eidolon.nativeapp.ui.components.MessageState
import sc.eidolon.nativeapp.ui.components.SharedAvatarModifier
import sc.eidolon.nativeapp.ui.theme.herColors
import java.time.Instant
import java.time.ZoneId

@Composable
fun ConversationListScreen(
    container: AppContainer,
    avatarModifier: SharedAvatarModifier = { Modifier },
    onOpenCharacter: (String) -> Unit,
) {
    val vm: ConversationListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConversationListViewModel(container.characters, container.conversations) }
        },
    )
    // 从聊天返回时重新拉一次，列表里刚发过的消息才会更新排序
    LifecycleResumeEffect(Unit) {
        vm.load(quiet = true)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000)
            vm.load(quiet = true)
        }
    }
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000)
        }
    }
    val zone = remember { Time.zone() }
    val state = vm.state
    val baseUrl = container.session.serverUrl.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "对话",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = { Text("搜索名字或组合") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        state.error?.let {
            if (state.rows.isNotEmpty()) {
                Text(
                    text = "刷新失败：$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        val body = Modifier.weight(1f)
        when {
            state.loading && state.rows.isEmpty() -> Box(body) { LoadingState("正在载入对话列表") }
            state.error != null && state.rows.isEmpty() -> Box(body) {
                MessageState("对话列表没能载入", state.error, "重试" to { vm.load() })
            }
            state.rows.isEmpty() && state.query.isNotBlank() -> Box(body) {
                MessageState("没有找到这位偶像")
            }
            state.rows.isEmpty() -> Box(body) {
                MessageState("还没有可以对话的角色")
            }
            else -> LazyColumn(
                modifier = body,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.rows, key = { it.character.id }) { row ->
                    ConversationRowItem(
                        row = row,
                        baseUrl = baseUrl,
                        now = now,
                        zone = zone,
                        avatarModifier = avatarModifier,
                        onClick = { onOpenCharacter(row.character.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRowItem(
    row: ConversationRow,
    baseUrl: String,
    now: Instant,
    zone: ZoneId,
    avatarModifier: SharedAvatarModifier,
    onClick: () -> Unit,
) {
    val her = herColors(row.character.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(
            name = row.character.name,
            color = row.character.color,
            url = row.character.avatarUrl?.let { baseUrl + it },
            size = 56.dp,
            modifier = avatarModifier(row.character.id),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = row.character.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = previewText(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (row.unseen) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(her.mark, CircleShape),
                )
            }
            row.displayAt?.let {
                Text(
                    text = Time.formatListTime(it, now, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun previewText(row: ConversationRow): String = when (val preview = row.preview) {
    Preview.None -> if (row.hasConversation) "新的对话，还没有说话" else "还没有对话"
    is Preview.You -> "你：${preview.text}"
    is Preview.Her -> {
        val text = preview.text.ifBlank { "新的对话，还没有说话" }
        if (preview.photo) "照片：$text" else text
    }
    is Preview.Event -> when (preview.kind) {
        EventKind.HungUp -> "${row.character.name}挂断了电话"
        EventKind.Gift -> "你送给${row.character.name}一份${preview.giftName ?: "礼物"}"
        EventKind.Ended -> "${row.character.name}结束了对话"
    }
}
