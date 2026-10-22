package sc.eidolon.nativeapp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.api.ApiErrorBus
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.api.HttpFactory
import sc.eidolon.nativeapp.data.local.AccessStore
import sc.eidolon.nativeapp.data.local.CookieStore
import sc.eidolon.nativeapp.data.local.OwnKeysStore
import sc.eidolon.nativeapp.data.local.PreferencesStore
import sc.eidolon.nativeapp.data.local.SecureStore
import sc.eidolon.nativeapp.data.local.ServerState
import sc.eidolon.nativeapp.data.local.ServerStore
import sc.eidolon.nativeapp.data.repo.CharacterRepository
import sc.eidolon.nativeapp.data.repo.ConversationRepository
import sc.eidolon.nativeapp.data.repo.GiftsRepository
import sc.eidolon.nativeapp.data.repo.MemoryRepository
import sc.eidolon.nativeapp.push.PushController
import sc.eidolon.nativeapp.push.PushNotifier
import sc.eidolon.nativeapp.session.SessionController

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val secureStore = SecureStore()
    val preferences = PreferencesStore(appContext, secureStore)
    val accessStore = AccessStore(preferences)
    val serverState = ServerState()
    val serverStore = ServerStore(preferences)
    val cookieStore = CookieStore(
        appContext.getSharedPreferences("eidolon_cookies", Context.MODE_PRIVATE),
        secureStore,
    )
    val errorBus = ApiErrorBus()
    val http = HttpFactory(
        appContext,
        { serverState.url },
        cookieStore,
        errorBus,
        accessCredentials = accessStore::current,
        basicAuthCredentials = accessStore::currentBasic,
    )
    val api = EidolonApi(http, { serverState.url })
    val ownKeysStore = OwnKeysStore(preferences, serverState)
    val characters = CharacterRepository(api)
    val conversations = ConversationRepository(api)
    val memories = MemoryRepository(api)
    val gifts = GiftsRepository(api)
    val session = SessionController(api, serverStore, preferences, cookieStore, ownKeysStore, serverState)
    val pushNotifier = PushNotifier(appContext)
    val push = PushController(appContext, session, preferences)
    val pendingChat = MutableStateFlow<String?>(null)
    val pendingAuthCode = MutableStateFlow<String?>(null)
    val authError = MutableStateFlow<String?>(null)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        errorBus.listener = session::onError
    }

    fun start() {
        appScope.launch {
            accessStore.load()
            session.bootstrap()
        }
    }
}
