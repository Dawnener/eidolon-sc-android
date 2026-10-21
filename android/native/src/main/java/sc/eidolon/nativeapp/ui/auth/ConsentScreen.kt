package sc.eidolon.nativeapp.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.session.SessionController

class ConsentViewModel(private val session: SessionController) : ViewModel() {
    var agree by mutableStateOf(false)
    var atLeast13 by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun submit() {
        if (busy || !agree || !atLeast13) return
        viewModelScope.launch {
            busy = true
            error = null
            session.consent().onFailure { error = it.message ?: "提交失败" }
            busy = false
        }
    }
}

@Composable
fun ConsentScreen(container: AppContainer) {
    val vm: ConsentViewModel = viewModel(
        factory = viewModelFactory { initializer { ConsentViewModel(container.session) } },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val base = container.session.serverUrl.orEmpty()
    fun open(path: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base$path"))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("使用前请确认", style = MaterialTheme.typography.headlineSmall)
        Text(
            "第一次使用需要同意隐私政策和使用条款，并确认年满 13 岁。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = vm.agree, onCheckedChange = { vm.agree = it })
            Text(
                "我已阅读并同意隐私政策和使用条款",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = vm.atLeast13, onCheckedChange = { vm.atLeast13 = it })
            Text("我已年满 13 岁", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { open("/privacy") }) { Text("隐私政策") }
            TextButton(onClick = { open("/terms") }) { Text("使用条款") }
        }
        vm.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { vm.submit() },
            enabled = !vm.busy && vm.agree && vm.atLeast13,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.busy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text(if (vm.busy) "正在提交…" else "同意并开始")
        }
        TextButton(onClick = { scope.launch { container.session.logout() } }) {
            Text("退出登录")
        }
    }
}
