package sc.eidolon.nativeapp.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.model.Account
import sc.eidolon.nativeapp.ui.chat.ChatScreen
import sc.eidolon.nativeapp.ui.conversations.ConversationListScreen
import sc.eidolon.nativeapp.ui.datapackage.DataPackageScreen
import sc.eidolon.nativeapp.ui.gifts.GiftsScreen
import sc.eidolon.nativeapp.ui.memories.MemoriesScreen
import sc.eidolon.nativeapp.ui.ownkeys.OwnKeysScreen
import sc.eidolon.nativeapp.ui.profile.ProfileScreen
import sc.eidolon.nativeapp.ui.reachout.ReachOutModelScreen
import sc.eidolon.nativeapp.ui.settings.SettingsScreen
import sc.eidolon.nativeapp.ui.setup.ServerSetupScreen
import sc.eidolon.nativeapp.ui.stats.StatsScreen

private const val TAB_FADE_MS = 180
private const val SHEET_MS = 300
private const val SHEET_FADE_MS = 300
private const val BAR_MS = 300

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScaffold(container: AppContainer, account: Account) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Routes.baseRoute(route)
    val context = LocalContext.current
    val showBar = currentTab in setOf(Routes.Conversations, Routes.Gifts, Routes.Settings)

    // 从浏览器授权回来时，浏览器任务可能还在 App 任务下面。根页面按返回时
    // 直接回桌面并移除 App 任务，避免一下退回 GitHub 授权页。
    BackHandler(enabled = currentTab == Routes.Settings) {
        nav.navigateToRoot(Routes.Conversations)
    }
    BackHandler(enabled = currentTab == Routes.Gifts) {
        if (nav.previousBackStackEntry?.destination?.route == Routes.Chat) {
            nav.popBackStack()
        } else {
            nav.navigateToRoot(Routes.Conversations)
        }
    }
    BackHandler(enabled = currentTab == Routes.Conversations) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        // 只把任务移到后台，不 finish：这样 App 仍会留在最近任务里，
        // 同时不会露出下面的浏览器授权页。
        context.findActivity()?.moveTaskToBack(true)
    }
    val pendingChat by container.pendingChat.collectAsState()
    LaunchedEffect(pendingChat) {
        val characterId = pendingChat ?: return@LaunchedEffect
        container.pendingChat.value = null
        nav.navigate(Routes.chat(characterId))
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // 进出聊天时让底栏滑出/滑入，而不是瞬间消失
            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn(tween(BAR_MS)) +
                    expandVertically(tween(BAR_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(BAR_MS - 60)) +
                    shrinkVertically(tween(BAR_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom),
            ) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == Routes.Conversations,
                        onClick = { nav.navigateToRoot(Routes.Conversations) },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                        label = { Text("对话") },
                    )
                    NavigationBarItem(
                        selected = currentTab == Routes.Gifts,
                        onClick = { nav.navigateToRoot(Routes.Gifts) },
                        icon = { Icon(Icons.Outlined.CardGiftcard, contentDescription = null) },
                        label = { Text("礼物") },
                    )
                    NavigationBarItem(
                        selected = currentTab == Routes.Settings,
                        onClick = { nav.navigateToRoot(Routes.Settings) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
    ) { padding ->
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            NavHost(
                navController = nav,
                startDestination = Routes.Conversations,
                // padding 已包含状态栏和导航栏；先声明这些 inset 已消费，
                // imePadding 就只补「键盘高度减去导航栏」那一段。
                // 这样键盘抬起时只压缩内容高度，不会像 adjustPan 那样把整页（连同标题栏）顶出屏幕。
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
                // 根页面之间用很短的淡入淡出，别用默认的长动画；
                // 返回时放慢到和聊天退出同样的 300ms，避免主页“啪”地一下出现
                enterTransition = { fadeIn(tween(TAB_FADE_MS)) },
                exitTransition = { fadeOut(tween(TAB_FADE_MS)) },
                popEnterTransition = { fadeIn(tween(SHEET_MS)) },
                popExitTransition = { fadeOut(tween(SHEET_MS)) },
            ) {
                composable(
                    route = Routes.Conversations,
                    enterTransition = {
                        tabEnter(initialState.destination.route, targetState.destination.route)
                    },
                    exitTransition = {
                        tabExit(initialState.destination.route, targetState.destination.route)
                    },
                    popEnterTransition = {
                        // 从聊天返回时用较大的位移；从其他标签页回来按方向滑入
                        if (initialState.destination.route == Routes.Chat) {
                            slideInHorizontally(
                                animationSpec = tween(SHEET_MS, easing = FastOutSlowInEasing),
                                initialOffsetX = { -it / 3 },
                            ) + fadeIn(tween(SHEET_MS))
                        } else {
                            tabEnter(initialState.destination.route, targetState.destination.route)
                        }
                    },
                    popExitTransition = { fadeOut(tween(TAB_FADE_MS)) },
                ) {
                    val visibility = this
                    ConversationListScreen(
                        container = container,
                        avatarModifier = { id ->
                            with(sharedScope) {
                                Modifier.sharedElement(
                                    rememberSharedContentState("avatar-$id"),
                                    animatedVisibilityScope = visibility,
                                    boundsTransform = { _, _ ->
                                        tween(SHEET_MS, easing = FastOutSlowInEasing)
                                    },
                                )
                            }
                        },
                    ) { characterId ->
                        nav.navigate(Routes.chat(characterId))
                    }
                }
                composable(
                    route = Routes.Chat,
                    // 头像用共享元素飞到顶栏；其余内容只位移 1/5 屏并淡入淡出
                    enterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(SHEET_MS, easing = FastOutSlowInEasing),
                            initialOffsetX = { it / 5 },
                        ) + fadeIn(tween(SHEET_FADE_MS))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(SHEET_MS, easing = FastOutSlowInEasing),
                            targetOffsetX = { -it / 5 },
                        ) + fadeOut(tween(SHEET_FADE_MS))
                    },
                    popEnterTransition = { fadeIn(tween(SHEET_MS)) },
                    popExitTransition = { fadeOut(tween(SHEET_MS)) },
                ) { entry ->
                    val visibility = this
                    val characterId = entry.arguments?.getString("characterId").orEmpty()
                    ChatScreen(
                        container = container,
                        characterId = characterId,
                        avatarModifier = { id ->
                            with(sharedScope) {
                                Modifier.sharedElement(
                                    rememberSharedContentState("avatar-$id"),
                                    animatedVisibilityScope = visibility,
                                    boundsTransform = { _, _ ->
                                        tween(SHEET_MS, easing = FastOutSlowInEasing)
                                    },
                                )
                            }
                        },
                        onBack = { nav.popBackStack() },
                        onOpenSettings = { nav.navigate(Routes.OwnKeys) },
                        onOpenGifts = { id -> nav.navigate(Routes.gifts(id)) },
                    )
                }
                composable(
                    route = Routes.GiftsPattern,
                    arguments = listOf(
                        navArgument("to") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                    enterTransition = {
                        tabEnter(initialState.destination.route, targetState.destination.route)
                    },
                    exitTransition = {
                        tabExit(initialState.destination.route, targetState.destination.route)
                    },
                    popEnterTransition = { fadeIn(tween(SHEET_MS)) },
                    popExitTransition = { fadeOut(tween(TAB_FADE_MS)) },
                ) { entry ->
                    GiftsScreen(
                        container = container,
                        preselectedCharacterId = entry.arguments?.getString("to"),
                        onOpenConversations = { nav.navigateToRoot(Routes.Conversations) },
                        onOpenChat = { characterId -> nav.navigate(Routes.chat(characterId)) },
                    )
                }
                composable(
                    route = Routes.Settings,
                    enterTransition = {
                        tabEnter(initialState.destination.route, targetState.destination.route)
                    },
                    exitTransition = {
                        tabExit(initialState.destination.route, targetState.destination.route)
                    },
                    popEnterTransition = { fadeIn(tween(SHEET_MS)) },
                    popExitTransition = { fadeOut(tween(TAB_FADE_MS)) },
                ) {
                    SettingsScreen(
                        container = container,
                        account = account,
                        onOpenProfile = { nav.navigate(Routes.Profile) },
                        onOpenOwnKeys = { nav.navigate(Routes.OwnKeys) },
                        onOpenReachOutModel = { nav.navigate(Routes.ReachOutModel) },
                        onOpenMemories = { nav.navigate(Routes.Memories) },
                        onOpenStats = { nav.navigate(Routes.Stats) },
                        onOpenDataPackage = { nav.navigate(Routes.DataPackage) },
                        onChangeServer = { nav.navigate(Routes.Server) },
                    )
                }
                composable(Routes.Profile) {
                    ProfileScreen(container) { nav.popBackStack() }
                }
                composable(Routes.OwnKeys) {
                    OwnKeysScreen(container) { nav.popBackStack() }
                }
                composable(Routes.ReachOutModel) {
                    ReachOutModelScreen(container) { nav.popBackStack() }
                }
                composable(Routes.Memories) {
                    MemoriesScreen(container) { nav.popBackStack() }
                }
                composable(Routes.Stats) {
                    StatsScreen(
                        container = container,
                        onBack = { nav.popBackStack() },
                        onOpenConversations = { nav.navigateToRoot(Routes.Conversations) },
                    )
                }
                composable(Routes.DataPackage) {
                    DataPackageScreen(container) { nav.popBackStack() }
                }
                composable(Routes.Server) {
                    ServerSetupScreen(container, applySystemBarsPadding = false) { nav.popBackStack() }
                }
            }
        }
    }
}

/** A tab page sliding in while fading in; `fromLeft` is the side it comes from. */
private fun tabSlideIn(fromLeft: Boolean) =
    slideInHorizontally(
        animationSpec = tween(TAB_FADE_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { width -> if (fromLeft) -width / 5 else width / 5 },
    ) + fadeIn(tween(TAB_FADE_MS))

/** A tab page sliding out while fading out; `toLeft` is the side it leaves towards. */
private fun tabSlideOut(toLeft: Boolean) =
    slideOutHorizontally(
        animationSpec = tween(TAB_FADE_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { width -> if (toLeft) -width / 5 else width / 5 },
    ) + fadeOut(tween(TAB_FADE_MS))

private val TAB_ROUTES = listOf(Routes.Conversations, Routes.Gifts, Routes.Settings)

private fun tabIndex(route: String?): Int = TAB_ROUTES.indexOf(Routes.baseRoute(route))

private fun tabEnter(initial: String?, target: String?): EnterTransition {
    val from = tabIndex(initial)
    val to = tabIndex(target)
    return if (from >= 0 && to >= 0 && from != to) {
        tabSlideIn(fromLeft = to < from)
    } else {
        fadeIn(tween(TAB_FADE_MS))
    }
}

private fun tabExit(initial: String?, target: String?): ExitTransition {
    val from = tabIndex(initial)
    val to = tabIndex(target)
    return if (from >= 0 && to >= 0 && from != to) {
        tabSlideOut(toLeft = to > from)
    } else {
        fadeOut(tween(TAB_FADE_MS))
    }
}

private fun androidx.navigation.NavHostController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
