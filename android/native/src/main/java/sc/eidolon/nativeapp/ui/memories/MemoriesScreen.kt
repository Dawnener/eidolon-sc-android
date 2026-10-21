package sc.eidolon.nativeapp.ui.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.Memory
import sc.eidolon.nativeapp.ui.components.CharacterAvatar
import sc.eidolon.nativeapp.ui.components.LoadingState
import sc.eidolon.nativeapp.ui.components.MessageState
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: MemoriesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MemoriesViewModel(container.memories, container.characters) }
        },
    )
    val state = vm.state
    val baseUrl = container.session.serverUrl.orEmpty()
    val zone = remember { Time.zone() }
    var pendingDelete by remember { mutableStateOf<Memory?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

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
                title = { Text("记忆") },
                actions = {
                    TextButton(
                        onClick = { confirmClear = true },
                        enabled = state.memories.isNotEmpty() && !state.clearing && state.busyId == null,
                    ) { Text("清空") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                state.loading -> LoadingState("正在载入记忆")
                state.memories.isEmpty() -> MessageState("角色们还没有记下关于你的事。")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.memories, key = { it.id }) { memory ->
                        MemoryRow(
                            memory = memory,
                            character = memory.sourceCharacterId?.let { state.characters[it] },
                            baseUrl = baseUrl,
                            zone = zone,
                            busy = state.busyId == memory.id || state.clearing,
                            onDelete = { pendingDelete = memory },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记忆？") },
            text = { Text(memory.text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.delete(memory.id)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部记忆？") },
            text = { Text("确定清空全部 ${state.memories.size} 条记忆吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        vm.clear()
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MemoryRow(
    memory: Memory,
    character: sc.eidolon.nativeapp.model.Character?,
    baseUrl: String,
    zone: ZoneId,
    busy: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CharacterAvatar(
            name = character?.name ?: memory.sourceCharacterName ?: "?",
            color = character?.color ?: "#7FB4FF",
            url = character?.avatarUrl?.let { baseUrl + it },
            size = 36.dp,
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = memory.sourceCharacterName ?: "已不在的角色",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (memory.superseded) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    ) {
                        Text(
                            text = "已被取代",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = memory.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    textDecoration = if (memory.superseded) TextDecoration.LineThrough else null,
                ),
                color = if (memory.superseded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = Time.formatMemoryDate(memory.createdAt, zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Delete, contentDescription = "删除记忆")
            }
        }
    }
}
