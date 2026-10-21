package sc.eidolon.nativeapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import sc.eidolon.nativeapp.data.api.BirthdayDto
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.api.ProfileRequest
import sc.eidolon.nativeapp.model.ProducerProfile
import sc.eidolon.nativeapp.ui.components.LoadingState

data class ProfileState(
    val loading: Boolean = true,
    val profile: ProducerProfile? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class ProfileViewModel(private val api: EidolonApi) : ViewModel() {
    var state by mutableStateOf(ProfileState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            state = try {
                state.copy(loading = false, profile = api.profile())
            } catch (error: Exception) {
                state.copy(loading = false, error = error.message ?: "档案没能读取")
            }
        }
    }

    fun save(request: ProfileRequest) {
        if (state.busy) return
        state = state.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            state = try {
                val profile = api.saveProfile(request)
                state.copy(busy = false, profile = profile, notice = "已保存")
            } catch (error: Exception) {
                state.copy(busy = false, error = error.message ?: "档案没能保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: ProfileViewModel = viewModel(
        factory = viewModelFactory { initializer { ProfileViewModel(container.api) } },
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
                title = { Text("制作人档案") },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                LoadingState("正在读取制作人档案")
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
            Text(
                text = "这些是她对你的了解：怎么称呼你、你的性别、你所在的时区，以及生日。" +
                    "生日只有月和日，不含年份，用来在当天祝福你。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            state.notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            ProfileForm(
                profile = state.profile,
                busy = state.busy,
                onSave = vm::save,
            )
        }
    }
}

@Composable
private fun ProfileForm(
    profile: ProducerProfile?,
    busy: Boolean,
    onSave: (ProfileRequest) -> Unit,
) {
    var formOfAddress by remember(profile) { mutableStateOf(profile?.formOfAddress.orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender) }
    var timezone by remember(profile) { mutableStateOf(profile?.timezone.orEmpty()) }
    var month by remember(profile) { mutableStateOf(profile?.birthdayMonth?.toString().orEmpty()) }
    var day by remember(profile) { mutableStateOf(profile?.birthdayDay?.toString().orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = formOfAddress,
            onValueChange = { formOfAddress = it },
            label = { Text("称呼") },
            placeholder = { Text("她怎么称呼你，例如「制作人」") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("性别", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = gender == null,
                onClick = { gender = null },
                label = { Text("不填") },
            )
            FilterChip(
                selected = gender == "male",
                onClick = { gender = "male" },
                label = { Text("男性") },
            )
            FilterChip(
                selected = gender == "female",
                onClick = { gender = "female" },
                label = { Text("女性") },
            )
        }
        OutlinedTextField(
            value = timezone,
            onValueChange = { timezone = it },
            label = { Text("时区") },
            placeholder = { Text("Asia/Shanghai") },
            supportingText = { Text("IANA 时区名；留空则按设备时区（搭话按北京时间）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("生日（只有月和日）", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = month,
                onValueChange = { month = it.filter(Char::isDigit) },
                label = { Text("月") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = day,
                onValueChange = { day = it.filter(Char::isDigit) },
                label = { Text("日") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        localError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                localError = null
                if (month.isBlank() != day.isBlank()) {
                    localError = "生日要同时填月和日，或者都留空"
                    return@Button
                }
                val birthday = if (month.isBlank()) {
                    null
                } else {
                    val parsedMonth = month.toIntOrNull()
                    val parsedDay = day.toIntOrNull()
                    if (parsedMonth == null || parsedDay == null ||
                        parsedMonth !in 1..12 || parsedDay !in 1..31
                    ) {
                        localError = "请填入有效的月和日"
                        return@Button
                    }
                    BirthdayDto(parsedMonth, parsedDay)
                }
                onSave(
                    ProfileRequest(
                        formOfAddress = formOfAddress.trim().ifBlank { null },
                        gender = gender,
                        timezone = timezone.trim().ifBlank { null },
                        birthday = birthday,
                    ),
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "正在保存…" else "保存")
        }
    }
}
