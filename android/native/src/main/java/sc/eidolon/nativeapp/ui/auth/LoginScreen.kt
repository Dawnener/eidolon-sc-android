package sc.eidolon.nativeapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val context = LocalContext.current
    val authError by container.authError.collectAsStateWithLifecycle()

    fun openProvider(provider: String) {
        val base = container.session.serverUrl ?: return
        container.authError.value = null
        vm.error = null
        openOAuth(context, base, provider)
    }

    OnboardingPage(
        title = "登录",
        subtitle = "和 283 事务所的偶像说说话。",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                vm.loading -> CircularProgressIndicator()
                vm.providers != null -> {
                    val options = requireNotNull(vm.providers)
                    if (options.providers.isNotEmpty()) {
                        options.providers.forEachIndexed { index, provider ->
                            val label = oauthProviderLabel(provider)
                            if (index == 0) {
                                Button(
                                    onClick = { openProvider(provider) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("使用 $label 登录") }
                            } else {
                                OutlinedButton(
                                    onClick = { openProvider(provider) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("使用 $label 登录") }
                            }
                        }
                        Text(
                            "会打开浏览器完成 GitHub / Google 授权，完成后自动回到 App。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (options.devLogin) {
                        if (options.providers.isNotEmpty()) {
                            Text(
                                "或用开发登录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = vm.name,
                            onValueChange = { vm.name = it },
                            label = { Text("用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    container.authError.value = null
                                    vm.signIn()
                                },
                            ),
                        )
                        Button(
                            onClick = {
                                container.authError.value = null
                                vm.signIn()
                            },
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
                    if (options.providers.isEmpty() && !options.devLogin) {
                        Text(
                            "这个部署没有配置可用的登录方式。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            (authError ?: vm.error)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = {
                    container.pendingAuthCode.value = null
                    container.authError.value = null
                    onChangeServer()
                },
            ) { Text("更换服务器") }
        }
    }
}
