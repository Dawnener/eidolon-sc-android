package sc.eidolon.nativeapp.ui.ownkeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.serialization.json.jsonObject
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.OwnKeysRules
import sc.eidolon.nativeapp.model.OwnLlmRequest
import sc.eidolon.nativeapp.model.OwnMemoryLlmRequest
import sc.eidolon.nativeapp.model.OwnSttRequest
import sc.eidolon.nativeapp.model.OwnTtsRequest
import sc.eidolon.nativeapp.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnKeysScreen(container: AppContainer, onBack: () -> Unit) {
    val accountId = container.session.account?.id.orEmpty()
    val vm: OwnKeysViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OwnKeysViewModel(accountId, container.ownKeysStore, container.api) }
        },
    )
    val state = vm.state

    Scaffold(
        // 外层导航已经处理系统栏和 IME
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
                    }
                },
                title = { Text("自带密钥") },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                LoadingState("正在读取密钥")
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
                    text = "密钥只保存在这台设备上，用 Android Keystore 加密；每次聊天或通话时随请求发给服务器，" +
                        "只在处理期间使用，服务器不保存。界面上只显示末 4 位。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.exemption?.let {
                if (it.calls) {
                    Text(
                        text = "通话带上自己的语音密钥不计入限额。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            state.notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            KeySection(
                title = "对话 LLM",
                summary = state.keys.llm?.let {
                    "${it.model} · ${OwnKeysRules.masked(it.apiKey)}"
                } ?: "未填写",
                configured = state.keys.llm != null,
                editing = state.editing == OwnKeySection.Llm,
                busy = state.busy,
                onEdit = { vm.edit(OwnKeySection.Llm) },
                onDelete = { vm.delete(OwnKeySection.Llm) },
            ) {
                LlmForm(
                    existingLlm = state.keys.llm,
                    existingMemory = null,
                    memory = false,
                    busy = state.busy,
                    onCancel = vm::cancelEdit,
                    onSaveLlm = { vm.saveLlm(it) },
                    onSaveMemory = {},
                )
            }

            if (state.keys.llm != null || state.editing == OwnKeySection.MemoryLlm) {
                KeySection(
                    title = "后台模型",
                    summary = state.keys.memoryLlm?.let {
                        "${it.model} · ${OwnKeysRules.masked(it.apiKey)}"
                    } ?: "跟随对话 LLM",
                    configured = state.keys.memoryLlm != null,
                    editing = state.editing == OwnKeySection.MemoryLlm,
                    busy = state.busy,
                    onEdit = { vm.edit(OwnKeySection.MemoryLlm) },
                    onDelete = { vm.delete(OwnKeySection.MemoryLlm) },
                ) {
                    LlmForm(
                        existingLlm = null,
                        existingMemory = state.keys.memoryLlm,
                        memory = true,
                        busy = state.busy,
                        onCancel = vm::cancelEdit,
                        onSaveLlm = {},
                        onSaveMemory = { vm.saveMemoryLlm(it) },
                    )
                }
            } else {
                Text(
                    "先填写对话 LLM，才能设置单独的后台模型。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            KeySection(
                title = "语音合成（Fish Audio）",
                summary = state.keys.tts?.let {
                    "${it.model ?: "默认模型"} · ${OwnKeysRules.masked(it.apiKey)}"
                } ?: "未填写",
                configured = state.keys.tts != null,
                editing = state.editing == OwnKeySection.Tts,
                busy = state.busy,
                onEdit = { vm.edit(OwnKeySection.Tts) },
                onDelete = { vm.delete(OwnKeySection.Tts) },
            ) {
                TtsForm(
                    existing = state.keys.tts,
                    busy = state.busy,
                    onCancel = vm::cancelEdit,
                    onSave = { vm.saveTts(it) },
                )
            }

            KeySection(
                title = "语音识别",
                summary = state.keys.stt?.let {
                    "${it.provider} · ${OwnKeysRules.masked(it.apiKey)}"
                } ?: "未填写",
                configured = state.keys.stt != null,
                editing = state.editing == OwnKeySection.Stt,
                busy = state.busy,
                onEdit = { vm.edit(OwnKeySection.Stt) },
                onDelete = { vm.delete(OwnKeySection.Stt) },
            ) {
                SttForm(
                    existing = state.keys.stt,
                    busy = state.busy,
                    onCancel = vm::cancelEdit,
                    onSave = { vm.saveStt(it) },
                )
            }

            Text(
                "保存前会向对应服务发一次很小的检查请求。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeySection(
    title: String,
    summary: String,
    configured: Boolean,
    editing: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    form: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!editing) {
                    TextButton(onClick = onEdit, enabled = !busy) {
                        Text(if (configured) "修改" else "填写")
                    }
                    if (configured) {
                        TextButton(onClick = onDelete, enabled = !busy) { Text("删除") }
                    }
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing) form()
        }
    }
}

@Composable
private fun LlmForm(
    existingLlm: OwnLlmRequest?,
    existingMemory: OwnMemoryLlmRequest?,
    memory: Boolean,
    busy: Boolean,
    onCancel: () -> Unit,
    onSaveLlm: (OwnLlmRequest) -> Unit,
    onSaveMemory: (OwnMemoryLlmRequest) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(existingLlm?.baseUrl ?: existingMemory?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(existingLlm?.model ?: existingMemory?.model ?: "") }
    var protocol by remember {
        mutableStateOf(existingLlm?.protocol ?: existingMemory?.protocol ?: "chat-completions")
    }
    var effort by remember {
        mutableStateOf(existingLlm?.reasoningEffort ?: existingMemory?.reasoningEffort ?: "")
    }
    var callEffort by remember { mutableStateOf(existingLlm?.callReasoningEffort ?: "") }
    var extraBody by remember {
        mutableStateOf(
            (existingLlm?.extraBody ?: existingMemory?.extraBody)?.toString().orEmpty().let {
                if (it == "null") "" else it
            },
        )
    }
    var localError by remember { mutableStateOf<String?>(null) }
    val existingKey = existingLlm?.apiKey ?: existingMemory?.apiKey
    val keyHint = if (existingKey != null) {
        "留空沿用已保存的密钥（末 4 位 ${OwnKeysRules.last4(existingKey)}）"
    } else {
        "API Key"
    }

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
            placeholder = { Text(keyHint) },
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
        OutlinedTextField(
            value = effort,
            onValueChange = { effort = it },
            label = { Text("思考强度（可空：none / low / medium / high …）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!memory) {
            OutlinedTextField(
                value = callEffort,
                onValueChange = { callEffort = it },
                label = { Text("通话时的思考强度（可空）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = extraBody,
            onValueChange = { extraBody = it },
            label = { Text("额外参数 JSON（可空）") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        localError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("取消") }
            Button(
                onClick = {
                    localError = null
                    val extra = if (extraBody.isBlank()) {
                        null
                    } else {
                        runCatching { EidolonJson.parseToJsonElement(extraBody).jsonObject }
                            .getOrElse {
                                localError = "额外参数不是 JSON 对象"
                                return@Button
                            }
                    }
                    val request = if (memory) {
                        OwnMemoryLlmRequest(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            protocol = protocol,
                            reasoningEffort = effort.ifBlank { null },
                            extraBody = extra,
                        )
                    } else {
                        OwnLlmRequest(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            protocol = protocol,
                            reasoningEffort = effort.ifBlank { null },
                            callReasoningEffort = callEffort.ifBlank { null },
                            extraBody = extra,
                        )
                    }
                    if (memory) {
                        onSaveMemory(request as OwnMemoryLlmRequest)
                    } else {
                        onSaveLlm(request as OwnLlmRequest)
                    }
                },
                enabled = !busy,
            ) { Text(if (busy) "正在验证…" else "保存并验证") }
        }
    }
}

@Composable
private fun TtsForm(
    existing: OwnTtsRequest?,
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: (OwnTtsRequest) -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(existing?.model ?: "s2.1-pro-free") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Fish Audio API Key") },
            placeholder = { Text(existing?.let { "留空沿用（末 4 位 ${OwnKeysRules.last4(it.apiKey)}）" } ?: "") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型（s2.1-pro-free / s2.1-pro / drama-3 / s2-pro / s1）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("取消") }
            Button(
                onClick = { onSave(OwnTtsRequest(apiKey, model.ifBlank { null })) },
                enabled = !busy,
            ) { Text(if (busy) "正在验证…" else "保存并验证") }
        }
    }
}

@Composable
private fun SttForm(
    existing: OwnSttRequest?,
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: (OwnSttRequest) -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(existing?.provider ?: "soniox") }
    var region by remember { mutableStateOf(existing?.region.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("语音识别 API Key") },
            placeholder = { Text(existing?.let { "留空沿用（末 4 位 ${OwnKeysRules.last4(it.apiKey)}）" } ?: "") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = provider,
            onValueChange = { provider = it },
            label = { Text("服务商（soniox / bailian）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text("地域（可空：beijing / singapore，仅百炼）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("取消") }
            Button(
                onClick = {
                    onSave(
                        OwnSttRequest(
                            apiKey = apiKey,
                            provider = provider,
                            region = region.ifBlank { null },
                        ),
                    )
                },
                enabled = !busy,
            ) { Text(if (busy) "正在验证…" else "保存并验证") }
        }
    }
}

