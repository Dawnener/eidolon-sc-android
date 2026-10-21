package sc.eidolon.nativeapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.model.Account

@Composable
fun SettingsScreen(
    container: AppContainer,
    account: Account,
    onOpenOwnKeys: () -> Unit,
    onOpenMemories: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        SettingsRow(
            title = account.name,
            subtitle = "账号 ${account.id} · ${
                account.signInMethods.joinToString("、") { it.provider }.ifBlank { "开发登录" }
            }",
        )
        HorizontalDivider()
        SettingsRow(
            title = "服务器",
            subtitle = container.session.serverUrl.orEmpty(),
            onClick = onChangeServer,
        )
        HorizontalDivider()
        SettingsRow(
            title = "自带密钥",
            subtitle = "用你自己的 API key，费用由你承担",
            onClick = onOpenOwnKeys,
        )
        HorizontalDivider()
        SettingsRow(
            title = "记忆",
            subtitle = "查看和删除角色们记住的事",
            onClick = onOpenMemories,
        )
        HorizontalDivider()
        SettingsRow(
            title = "关于",
            subtitle = "Eidolon 原生 0.1.0 · 非官方粉丝作品，角色由 AI 扮演",
        )
        HorizontalDivider()
        SettingsRow(
            title = "退出登录",
            subtitle = "会清除这台设备上保存的自带密钥",
            onClick = { confirmLogout = true },
        )
        SettingsRow(
            title = "删除账号",
            subtitle = "删除全部对话、记忆和密钥，无法恢复",
            destructive = true,
            onClick = { confirmDelete = true },
            showChevron = false,
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = { Text("这台设备上保存的自带密钥会被清除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        scope.launch { container.session.logout() }
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除账号？") },
            text = { Text("所有对话、记忆、制作人档案和自带密钥都会被删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch { container.session.deleteAccount() }
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null && showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
