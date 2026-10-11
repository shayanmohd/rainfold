package com.mohdshayan.rainfold.ui.nav

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.di.ServiceLocator
import com.mohdshayan.rainfold.ui.components.LocalUnlock
import com.mohdshayan.rainfold.ui.components.UnlockAccess
import com.mohdshayan.rainfold.ui.info.BatteryHelpScreen
import com.mohdshayan.rainfold.ui.info.LicencesScreen
import com.mohdshayan.rainfold.ui.info.PrivacyScreen
import com.mohdshayan.rainfold.ui.listen.ListenScreen
import com.mohdshayan.rainfold.ui.mixes.MixesScreen
import com.mohdshayan.rainfold.ui.settings.SettingsScreen
import com.mohdshayan.rainfold.ui.unlock.UnlockScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object ListenRoute
@Serializable object MixesRoute
@Serializable object SettingsRoute
@Serializable object BatteryRoute
@Serializable object LicencesRoute
@Serializable object PrivacyRoute

/** The one unlock screen. [reason] names the locked feature that sent the user here. */
@Serializable
data class UnlockRoute(val reason: String = "")

private data class TopLevel(val route: Any, val type: KClass<*>, val label: Int, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(ListenRoute, ListenRoute::class, R.string.nav_listen, Icons.Rounded.GraphicEq),
    TopLevel(MixesRoute, MixesRoute::class, R.string.nav_mixes, Icons.AutoMirrored.Rounded.QueueMusic),
    TopLevel(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Rounded.Tune),
)

private fun NavHostController.goTop(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav(pendingUnlock: MutableStateFlow<String?>) {
    val navController = rememberNavController()
    val unlock by ServiceLocator.unlock.state.collectAsStateWithLifecycle()
    val access = remember(unlock.unlocked, navController) {
        UnlockAccess(unlock.unlocked) { reason ->
            navController.navigate(UnlockRoute(reason)) { launchSingleTop = true }
        }
    }
    val pending by pendingUnlock.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            pendingUnlock.value = null
            access.open(it)
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val current = topLevels.firstOrNull { top -> entry?.destination?.hasRoute(top.type) == true }

    // Every locked feature reads LocalUnlock: unlocked to run, open(reason) to explain and sell.
    CompositionLocalProvider(LocalUnlock provides access) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // From 600dp a rail, and Listen with Mixes side by side when there is height for both.
            val wide = maxWidth >= 600.dp
            val twoPane = wide && maxHeight >= 480.dp
            val host = @Composable { modifier: Modifier ->
                NavHost(navController = navController, startDestination = ListenRoute, modifier = modifier) {
                    composable<ListenRoute> {
                        // One tree in every width, so a rotation keeps Listen's saved state (an open sheet, a typed name).
                        Row(Modifier.fillMaxSize()) {
                            ListenScreen(onOpenBatteryHelp = { navController.navigate(BatteryRoute) }, modifier = Modifier.weight(1.25f))
                            if (twoPane) MixesScreen(onOpenListen = {}, modifier = Modifier.weight(1f), showOpenListen = false)
                        }
                    }
                    composable<MixesRoute> {
                        MixesScreen(onOpenListen = { navController.goTop(ListenRoute) })
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            onBatteryHelp = { navController.navigate(BatteryRoute) },
                            onLicences = { navController.navigate(LicencesRoute) },
                            onPrivacy = { navController.navigate(PrivacyRoute) },
                        )
                    }
                    composable<BatteryRoute> { BatteryHelpScreen(onBack = { navController.popBackStack() }) }
                    composable<LicencesRoute> { LicencesScreen(onBack = { navController.popBackStack() }) }
                    composable<PrivacyRoute> { PrivacyScreen(onBack = { navController.popBackStack() }) }
                    composable<UnlockRoute> { e ->
                        UnlockScreen(
                            reason = e.toRoute<UnlockRoute>().reason,
                            onClose = { navController.popBackStack() },
                        )
                    }
                }
            }
            val items = if (twoPane) topLevels.filter { it.type != MixesRoute::class } else topLevels
            // The rail and the bottom bar swap with the width, but the NavHost keeps one place in the tree, so
            // rotating never drops a screen's saved state.
            Row(Modifier.fillMaxSize()) {
                if (wide && current != null) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                        items.forEach { top ->
                            NavigationRailItem(
                                selected = current == top || (twoPane && current.type == MixesRoute::class && top.type == ListenRoute::class),
                                onClick = { navController.goTop(top.route) },
                                icon = { Icon(top.icon, contentDescription = null) },
                                label = { Text(stringResource(top.label)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (!wide && current != null) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
                                topLevels.forEach { top ->
                                    NavigationBarItem(
                                        selected = current == top,
                                        onClick = { navController.goTop(top.route) },
                                        icon = { Icon(top.icon, contentDescription = null) },
                                        label = { Text(stringResource(top.label)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    host(Modifier.padding(padding).consumeWindowInsets(padding))
                }
            }
        }
    }
}
