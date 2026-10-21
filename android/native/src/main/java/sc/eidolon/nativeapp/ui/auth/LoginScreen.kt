package sc.eidolon.nativeapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.ui.onboarding.OnboardingPage
import sc.eidolon.nativeapp.data.api.AuthProvidersDto
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.session.SessionController

class LoginViewModel(
    private val session: SessionController,
    private val api: EidolonApi,
) : ViewModel() {
    var providers by mutableStateOf<AuthProvidersDto?>(null)
    var loading by mutableStateOf(true)
    var name by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            runCatching { api.providers() }
                .onSuccess { providers = it }
                .onFailure { error = it.message ?: "拿不到登录方式" }
            loading = false
        }
    }

    fun signIn() {
        if (busy || name.isBlank()) return
        viewModelScope.launch {
            busy = true
            error = null
            session.signInDev(name)
                .onFailure { error = it.message ?: "登录失败" }
            busy = false
        }
    }
}

@Composable
fun LoginScreen(container: AppContainer, onChangeServer: () -> Unit) {
    val vm: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(container.session, container.api) }
        },
    )
    OnboardingPage(
        title = "登录",
        subtitle = "和 283 事务所的偶像说说话。",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "服务器：${container.session.serverUrl.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                vm.loading -> CircularProgressIndicator()
                vm.providers?.devLogin == true -> {
                    OutlinedTextField(
                        value = vm.name,
                        onValueChange = { vm.name = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.signIn() }),
                    )
                    Button(
                        onClick = { vm.signIn() },
                        enabled = !vm.busy && vm.name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (vm.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(if (vm.busy) "正在登录…" else "登录")
                    }
                }
                vm.providers != null -> {
                    Text(
                        "这个部署没有开启开发登录。此版本暂不支持 GitHub/Google 登录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            vm.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onChangeServer) { Text("更换服务器") }
        }
    }
}
