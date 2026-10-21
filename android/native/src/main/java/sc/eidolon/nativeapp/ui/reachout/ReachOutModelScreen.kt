package sc.eidolon.nativeapp.ui.reachout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.model.OwnMemoryLlmRequest
import sc.eidolon.nativeapp.model.ReachOutModel
import sc.eidolon.nativeapp.ui.components.LoadingState

data class ReachOutModelState(
    val loading: Boolean = true,
    val info: ReachOutModel? = null,
    val editing: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class ReachOutModelViewModel(private val api: EidolonApi) : ViewModel() {
    var state by mutableStateOf(ReachOutModelState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            state = try {
                val info = api.reachOutModel()
                state.copy(loading = false, info = info, editing = !info.configured)
            } catch (error: Exception) {
                state.copy(loading = false, error = error.message ?: "读取失败")
            }
        }
    }

    fun edit() {
        state = state.copy(editing = true, error = null, notice = null)
    }

    fun cancel() {
        state = state.copy(editing = false, error = null)
    }

    fun save(request: OwnMemoryLlmRequest) {
        if (state.busy) return
        state = state.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            state = try {
                api.saveReachOutModel(request)
                val info = api.reachOutModel()
                state.copy(busy = false, info = info, editing = false, notice = "已保存")
            } catch (error: Exception) {
                state.copy(busy = false, error = error.message ?: "保存失败")
            }
        }
    }

    fun delete() {
        if (state.busy) return
        state = state.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            state = try {
                api.deleteReachOutModel()
                val info = api.reachOutModel()
                state.copy(busy = false, info = info, editing = false, notice = "已删除")
            } catch (error: Exception) {
                state.copy(busy = false, error = error.message ?: "删除失败")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReachOutModelScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: ReachOutModelViewModel = viewModel(
        factory = viewModelFactory { initializer { ReachOutModelViewModel(container.api) } },
    )
    val state = vm.state

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
                title = { Text("搭话模型") },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                LoadingState("正在读取搭话模型")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "搭话会在你不在线时由服务器生成，所以这把 key 会加密保存在服务器上，" +
                        "只用于搭话，不会用于你的文字回合。服务器需要先设置 EIDOLON_SECRET_KEY。" +
                        "界面上只显示末 4 位，服务器可以解密。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            state.notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            val info = state.info
            if (!state.editing && info?.configured == true) {
                Text(
                    text = "已配置",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOfNotNull(
                        info.model,
                        info.protocol,
                        info.baseUrl,
                        info.last4?.let { "key 末 4 位 $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::edit, enabled = !state.busy) { Text("更换") }
                    TextButton(onClick = vm::delete, enabled = !state.busy) { Text("删除") }
                }
            } else {
                ReachOutModelForm(
                    existing = info?.takeIf { it.configured },
                    busy = state.busy,
                    canCancel = info?.configured == true,
                    onCancel = vm::cancel,
                    onSave = vm::save,
                )
            }
        }
    }
}

@Composable
private fun ReachOutModelForm(
    existing: ReachOutModel?,
    busy: Boolean,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (OwnMemoryLlmRequest) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: "chat-completions") }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("接口地址") },
            placeholder = { Text("https://api.example.com/v1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            placeholder = {
                Text(
                    if (existing != null) "重新填写 API Key（已保存的不会显示）" else "API Key",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = protocol,
            onValueChange = { protocol = it },
            label = { Text("接口类型（chat-completions / responses / anthropic）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        localError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canCancel) {
                TextButton(onClick = onCancel, enabled = !busy) { Text("取消") }
            }
            Button(
                onClick = {
                    localError = when {
                        baseUrl.isBlank() -> "请填写接口地址"
                        apiKey.isBlank() -> "请填写 API Key"
                        model.isBlank() -> "请填写模型名"
                        protocol.isBlank() -> "请填写接口类型"
                        else -> null
                    } ?: run {
                        onSave(
                            OwnMemoryLlmRequest(
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                model = model.trim(),
                                protocol = protocol.trim(),
                            ),
                        )
                        return@Button
                    }
                },
                enabled = !busy,
            ) {
                Text(if (busy) "正在验证…" else "保存并验证")
            }
        }
    }
}
