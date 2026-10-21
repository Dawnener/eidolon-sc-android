package sc.eidolon.nativeapp.ui.setup

import android.util.Log
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import sc.eidolon.nativeapp.BuildConfig
import sc.eidolon.nativeapp.session.SessionController
import sc.eidolon.nativeapp.ui.onboarding.OnboardingPage

class ServerSetupViewModel(private val session: SessionController) : ViewModel() {
    var url by mutableStateOf(BuildConfig.DEFAULT_SERVER_URL)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var connected by mutableStateOf(false)

    fun connect() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
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
        factory = viewModelFactory { initializer { ServerSetupViewModel(container.session) } },
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
