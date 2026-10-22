package sc.eidolon.nativeapp.ui.setup

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.BuildConfig
import sc.eidolon.nativeapp.data.local.AccessStore
import sc.eidolon.nativeapp.session.SessionController
import sc.eidolon.nativeapp.ui.onboarding.OnboardingPage

class ServerSetupViewModel(
    private val session: SessionController,
    private val accessStore: AccessStore,
) : ViewModel() {
    var url by mutableStateOf(BuildConfig.DEFAULT_SERVER_URL)
    var clientId by mutableStateOf("")
    var clientSecret by mutableStateOf("")
    var showAccess by mutableStateOf(false)
    var basicUser by mutableStateOf("")
    var basicPassword by mutableStateOf("")
    var showBasic by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var connected by mutableStateOf(false)

    init {
        viewModelScope.launch {
            accessStore.load()
            accessStore.current()?.let {
                clientId = it.clientId
                clientSecret = it.clientSecret
                showAccess = true
            }
            accessStore.currentBasic()?.let {
                basicUser = it.username
                basicPassword = it.password
                showBasic = true
            }
        }
    }

    fun connect() {
        if (busy) return
        val id = clientId.trim()
        val secret = clientSecret.trim()
        if (id.isBlank() != secret.isBlank()) {
            error = "Cloudflare Access 的 Client ID 和 Secret 要一起填"
            return
        }
        val user = basicUser.trim()
        val password = basicPassword.trim()
        if (user.isBlank() != password.isBlank()) {
            error = "HTTP 基本认证的用户名和密码要一起填"
            return
        }
        viewModelScope.launch {
            busy = true
            error = null
            try {
                if (id.isBlank()) accessStore.clear() else accessStore.save(id, secret)
                if (user.isBlank()) accessStore.clearBasic() else accessStore.saveBasic(user, password)
            } catch (e: Exception) {
                busy = false
                error = e.message ?: "访问凭证没能保存"
                return@launch
            }
            val result = session.useServer(url)
            busy = false
            result.onSuccess {
                Log.i("EidolonNative", "connected to $it")
                connected = true
            }.onFailure {
                Log.e("EidolonNative", "connect failed", it)
                error = buildString {
                    append(it::class.java.simpleName)
                    it.message?.takeIf { message -> message.isNotBlank() }?.let { message ->
                        append(": ")
                        append(message)
                    }
                }
            }
        }
    }
}

@Composable
fun ServerSetupScreen(
    container: AppContainer,
    applySystemBarsPadding: Boolean = true,
    onDone: () -> Unit,
) {
    val vm: ServerSetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ServerSetupViewModel(container.session, container.accessStore) }
        },
    )
    LaunchedEffect(vm.connected) {
        if (vm.connected) onDone()
    }
    val outer = if (applySystemBarsPadding) {
        // 作为登录前的整页时自己处理系统栏和 IME；放进设置子页时由外层导航处理
        Modifier.fillMaxSize().systemBarsPadding()
    } else {
        Modifier.fillMaxSize()
    }
    if (applySystemBarsPadding) {
        OnboardingPage(
            title = "连接服务器",
            subtitle = "Eidolon 不依赖官方服务器。填你或朋友部署的 Eidolon-SC 地址；原生 App 直接调用 /api，不加载网页。",
        ) {
            ServerFields(vm)
        }
        return
    }
    Column(
        modifier = outer
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("连接服务器", style = MaterialTheme.typography.headlineSmall)
        Text(
            "填 Eidolon-SC 后端的地址。原生 App 直接调用 /api，不需要打开网页。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ServerFields(vm)
    }
}

@Composable
private fun ServerFields(vm: ServerSetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = vm.url,
            onValueChange = { vm.url = it },
            label = { Text("服务器地址") },
            placeholder = { Text("http://192.0.2.10:8080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.connect() }),
            isError = vm.error != null,
        )
        TextButton(onClick = { vm.showBasic = !vm.showBasic }) {
            Text(if (vm.showBasic) "隐藏 HTTP 基本认证" else "使用 HTTP 基本认证（可选）")
        }
        if (vm.showBasic) {
            Text(
                "服务器入口套了用户名密码时填写；局域网直连不需要。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = vm.basicUser,
                onValueChange = { vm.basicUser = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.basicPassword,
                onValueChange = { vm.basicPassword = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = { vm.showAccess = !vm.showAccess }) {
            Text(if (vm.showAccess) "隐藏 Cloudflare Access" else "使用 Cloudflare Access（可选）")
        }
        if (vm.showAccess) {
            Text(
                "服务器套了 Cloudflare Access 时，填服务令牌；直连服务器或局域网不需要。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = vm.clientId,
                onValueChange = { vm.clientId = it },
                label = { Text("Access Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.clientSecret,
                onValueChange = { vm.clientSecret = it },
                label = { Text("Access Client Secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        vm.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { vm.connect() },
            enabled = !vm.busy && vm.url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(if (vm.busy) "正在连接…" else "连接")
        }
    }
}
