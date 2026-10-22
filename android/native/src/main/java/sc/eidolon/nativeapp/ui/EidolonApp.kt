package sc.eidolon.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.BuildConfig
import sc.eidolon.nativeapp.session.SessionState
import sc.eidolon.nativeapp.ui.auth.ConsentScreen
import sc.eidolon.nativeapp.ui.auth.LoginScreen
import sc.eidolon.nativeapp.ui.auth.openOAuth
import sc.eidolon.nativeapp.ui.navigation.MainScaffold
import sc.eidolon.nativeapp.ui.onboarding.WelcomeScreen
import sc.eidolon.nativeapp.ui.setup.ServerSetupScreen
import androidx.compose.material3.Button

@Composable
fun EidolonApp(container: AppContainer) {
    val state by container.session.state.collectAsStateWithLifecycle()
    var serverSetup by remember { mutableStateOf(false) }
    var welcomeDone by rememberSaveable { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var authenticating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun start() {
        if (welcomeDone || starting) return
        starting = true
        container.authError.value = null
        scope.launch {
            try {
                val base = BuildConfig.DEFAULT_SERVER_URL.trim()
                if (base.isEmpty() || !container.session.prepareServer(base)) {
                    container.authError.value = "服务器地址无效，请在设置里更换服务器"
                    welcomeDone = true
                    return@launch
                }
                // 不把 welcomeDone 置 true：浏览器打开期间后面仍然是欢迎页，
                // 不会闪出带「登录 / 使用 GitHub 登录 / 更换服务器」的登录页。
                openOAuth(context, base, "github")
            } finally {
                starting = false
            }
        }
    }

    // 收集一次即可：不能把 code 当 LaunchedEffect 的 key，否则把它清空或 bootstrap
    // 改变会话状态时，正在交换的协程会被取消。
    LaunchedEffect(Unit) {
        container.pendingAuthCode.filterNotNull().collect { code ->
            authenticating = true
            welcomeDone = true
            container.pendingAuthCode.value = null
            try {
                // 等服务器就绪；交换本身改变会话状态时，这个 collector 不会被取消。
                container.session.state.first {
                    it !is SessionState.Loading && it !is SessionState.NoServer
                }
                container.session.exchangeMobileSignIn(code).onFailure {
                    container.authError.value = it.message ?: "登录失败，请重新登录"
                }
            } finally {
                authenticating = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            serverSetup -> ServerSetupScreen(container, onDone = { serverSetup = false })
            authenticating -> LoadingView()
            (state is SessionState.NoServer || state is SessionState.SignedOut) && !welcomeDone ->
                WelcomeScreen(onStart = ::start, startLabel = "使用 GitHub 登录")
            state is SessionState.NoServer ->
                LoginScreen(container, onChangeServer = { serverSetup = true })
            state is SessionState.Loading -> LoadingView()
            state is SessionState.SignedOut -> LoginScreen(container, onChangeServer = { serverSetup = true })
            state is SessionState.NeedsConsent -> ConsentScreen(container)
            state is SessionState.SignedIn -> MainScaffold(
                container = container,
                account = (state as SessionState.SignedIn).account,
            )
            state is SessionState.Banned -> MessageView(
                title = "这位用户已被封禁",
                message = (state as SessionState.Banned).message,
                primary = "更换服务器" to { serverSetup = true },
                secondary = "退出登录" to { scope.launch { container.session.logout() } },
            )
            state is SessionState.Unavailable -> MessageView(
                title = "连不上服务器",
                message = (state as SessionState.Unavailable).message,
                primary = "重试" to { scope.launch { container.session.bootstrap() } },
                secondary = "更换服务器" to { serverSetup = true },
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "正在载入…",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageView(
    title: String,
    message: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = primary.second) { Text(primary.first) }
        Button(onClick = secondary.second, modifier = Modifier.padding(top = 8.dp)) {
            Text(secondary.first)
        }
    }
}
