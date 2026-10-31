package com.boxverse.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boxverse.app.AppContainer
import com.boxverse.app.AppEvent
import com.boxverse.app.BoxverseApp
import com.boxverse.app.domain.AchievementDef
import com.boxverse.app.ui.blindbox.BlindBoxLobbyScreen
import com.boxverse.app.ui.blindbox.BlindBoxSeriesScreen
import com.boxverse.app.ui.blindbox.OpeningHistoryScreen
import com.boxverse.app.ui.blindbox.VirtualCabinetScreen
import com.boxverse.app.ui.cabinet.CabinetDetailScreen
import com.boxverse.app.ui.cabinet.CabinetListScreen
import com.boxverse.app.ui.editor.FigureEditorScreen
import com.boxverse.app.ui.editor.ItemEditorScreen
import com.boxverse.app.ui.editor.SeriesEditorScreen
import com.boxverse.app.ui.home.HomeScreen
import com.boxverse.app.ui.pokedex.FigureDetailScreen
import com.boxverse.app.ui.pokedex.PokedexScreen
import com.boxverse.app.ui.pokedex.SeriesDetailScreen
import com.boxverse.app.ui.profile.AboutScreen
import com.boxverse.app.ui.profile.AchievementsScreen
import com.boxverse.app.ui.profile.DataScreen
import com.boxverse.app.ui.profile.PrivacyScreen
import com.boxverse.app.ui.profile.ProfileScreen
import com.boxverse.app.ui.profile.SettingsScreen
import com.boxverse.app.ui.stats.StatsScreen
import com.boxverse.app.ui.trade.TradeScreen
import com.boxverse.app.ui.wishlist.WishlistScreen
import kotlinx.coroutines.delay

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** 创建由 AppContainer 注入依赖的 ViewModel */
@Composable
inline fun <reified VM : ViewModel> boxViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as BoxverseApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

object Routes {
    const val HOME = "home"
    const val POKEDEX = "pokedex"
    const val BLINDBOX = "blindbox"
    const val TRADE = "trade"
    const val PROFILE = "profile"
    const val SERIES = "series/{id}"
    const val FIGURE = "figure/{id}"
    const val ITEM_EDIT = "item_edit?itemId={itemId}&figureId={figureId}&wishId={wishId}"
    const val FIGURE_EDIT = "figure_edit?figureId={figureId}&seriesId={seriesId}"
    const val SERIES_EDIT = "series_edit?seriesId={seriesId}"
    const val BOX_SERIES = "box/{seriesId}"
    const val BOX_HISTORY = "box_history"
    const val VIRTUAL = "virtual"
    const val WISHLIST = "wishlist"
    const val STATS = "stats"
    const val ACHIEVEMENTS = "achievements"
    const val CABINETS = "cabinets"
    const val CABINET = "cabinet/{id}"
    const val SETTINGS = "settings"
    const val DATA = "data"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"

    val TABS = listOf(HOME, POKEDEX, BLINDBOX, TRADE, PROFILE)
}

/** 页面跳转封装 */
class AppNav(val controller: NavHostController) {
    fun back() {
        controller.popBackStack()
    }

    fun go(route: String) = controller.navigate(route) { launchSingleTop = true }

    fun tab(route: String) = controller.navigate(route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun series(id: Long) = go("series/$id")
    fun figure(id: Long) = go("figure/$id")
    fun itemEdit(itemId: Long = 0, figureId: Long = 0, wishId: Long = 0) = go("item_edit?itemId=$itemId&figureId=$figureId&wishId=$wishId")
    fun figureEdit(figureId: Long = 0, seriesId: Long = 0) = go("figure_edit?figureId=$figureId&seriesId=$seriesId")
    fun seriesEdit(seriesId: Long = 0) = go("series_edit?seriesId=$seriesId")
    fun boxSeries(seriesId: Long) = go("box/$seriesId")
    fun cabinet(id: Long) = go("cabinet/$id")
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.HOME, "首页", Icons.Rounded.Home),
    TabItem(Routes.POKEDEX, "图鉴", Icons.Rounded.CollectionsBookmark),
    TabItem(Routes.BLINDBOX, "拆盲盒", Icons.Rounded.Redeem),
    TabItem(Routes.TRADE, "交换", Icons.Rounded.SwapHoriz),
    TabItem(Routes.PROFILE, "我的", Icons.Rounded.Person),
)

@Composable
fun BoxverseRoot() {
    val controller = rememberNavController()
    val nav = remember(controller) { AppNav(controller) }
    val backStack by controller.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val container = LocalContainer.current
    val snackbar = remember { SnackbarHostState() }
    var unlocked by remember { mutableStateOf<AchievementDef?>(null) }

    LaunchedEffect(Unit) {
        container.events.collect { e ->
            when (e) {
                is AppEvent.AchievementUnlocked -> {
                    container.haptics.success()
                    unlocked = e.def
                }
                is AppEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    LaunchedEffect(unlocked) {
        if (unlocked != null) {
            delay(3200)
            unlocked = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedVisibility(
                    visible = route in Routes.TABS,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    BottomBar(route) { nav.tab(it) }
                }
            },
        ) { padding ->
            NavHost(
                navController = controller,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } },
            ) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.POKEDEX) { PokedexScreen(nav) }
                composable(Routes.BLINDBOX) { BlindBoxLobbyScreen(nav) }
                composable(Routes.TRADE) { TradeScreen(nav) }
                composable(Routes.PROFILE) { ProfileScreen(nav) }
                composable(Routes.SERIES, listOf(navArgument("id") { type = NavType.LongType })) {
                    SeriesDetailScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(Routes.FIGURE, listOf(navArgument("id") { type = NavType.LongType })) {
                    FigureDetailScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(
                    Routes.ITEM_EDIT,
                    listOf(
                        navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("figureId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("wishId") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) {
                    val a = it.arguments
                    ItemEditorScreen(nav, a?.getLong("itemId") ?: 0L, a?.getLong("figureId") ?: 0L, a?.getLong("wishId") ?: 0L)
                }
                composable(
                    Routes.FIGURE_EDIT,
                    listOf(
                        navArgument("figureId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("seriesId") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) {
                    FigureEditorScreen(nav, it.arguments?.getLong("figureId") ?: 0L, it.arguments?.getLong("seriesId") ?: 0L)
                }
                composable(Routes.SERIES_EDIT, listOf(navArgument("seriesId") { type = NavType.LongType; defaultValue = 0L })) {
                    SeriesEditorScreen(nav, it.arguments?.getLong("seriesId") ?: 0L)
                }
                composable(Routes.BOX_SERIES, listOf(navArgument("seriesId") { type = NavType.LongType })) {
                    BlindBoxSeriesScreen(nav, it.arguments?.getLong("seriesId") ?: 0L)
                }
                composable(Routes.BOX_HISTORY) { OpeningHistoryScreen(nav) }
                composable(Routes.VIRTUAL) { VirtualCabinetScreen(nav) }
                composable(Routes.WISHLIST) { WishlistScreen(nav) }
                composable(Routes.STATS) { StatsScreen(nav) }
                composable(Routes.ACHIEVEMENTS) { AchievementsScreen(nav) }
                composable(Routes.CABINETS) { CabinetListScreen(nav) }
                composable(Routes.CABINET, listOf(navArgument("id") { type = NavType.LongType })) {
                    CabinetDetailScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.DATA) { DataScreen(nav) }
                composable(Routes.PRIVACY) { PrivacyScreen(nav) }
                composable(Routes.ABOUT) { AboutScreen(nav) }
            }
        }

        // 成就解锁横幅
        AnimatedVisibility(
            visible = unlocked != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val def = unlocked
            if (def != null) AchievementBanner(def) {
                unlocked = null
                nav.go(Routes.ACHIEVEMENTS)
            }
        }
    }
}

@Composable
private fun AchievementBanner(def: AchievementDef, onClick: () -> Unit) {
    Row(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color(0xFFE2AE45), spotColor = Color(0xFFE2AE45))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFE6A6), Color(0xFFFFC9DF), Color(0xFFD9CCFF))))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFE2A23A))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("成就解锁！", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A5A00))
            Text(def.title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2A2340))
            Text("${def.description} · +${def.xp} XP", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5A4E70))
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val selected = current == t.route
                if (t.route == Routes.BLINDBOX) {
                    CenterTab(t, selected) { onSelect(t.route) }
                } else {
                    NormalTab(t, selected, Modifier.weight(1f)) { onSelect(t.route) }
                }
            }
        }
    }
}

@Composable
private fun NormalTab(t: TabItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, label = "tabScale")
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (selected) scheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(t.icon, t.label, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp).scale(scale))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            t.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun CenterTab(t: TabItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "centerScale")
    Column(
        Modifier.width(76.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).scale(scale)
                .shadow(10.dp, CircleShape, ambientColor = scheme.primary, spotColor = scheme.primary)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFB39CFF), Color(0xFFF59AC4), Color(0xFFFFD27A)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(t.icon, t.label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (selected) scheme.primary else scheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}
