package cross.stick.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cross.stick.ui.screens.*
import cross.stick.viewmodel.ImportPhase
import cross.stick.viewmodel.MainViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val MY_PACKS = "my_packs"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState(initial = false)
    val isReady by viewModel.isReady.collectAsState(initial = false)
    val phase by viewModel.phase.collectAsState()
    val currentPackId by viewModel.currentPackId.collectAsState()
    val startDestination = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING

    if (!isReady) return

    Scaffold(
        bottomBar = {
            if (onboardingComplete) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                if (currentRoute in listOf(Routes.HOME, Routes.MY_PACKS, Routes.SETTINGS)) {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selected = currentRoute == Routes.HOME,
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Inventory2, contentDescription = "My Packs") },
                            label = { Text("My Packs") },
                            selected = currentRoute == Routes.MY_PACKS,
                            onClick = {
                                navController.navigate(Routes.MY_PACKS) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            selected = currentRoute == Routes.SETTINGS,
                            onClick = {
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = { token, author ->
                        viewModel.completeOnboarding(token, author)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    phase = phase,
                    onFetchPack = { link -> viewModel.fetchStickerSet(link) },
                    onNavigateToProgress = {
                        navController.navigate(Routes.PROGRESS)
                    },
                    onImportFromWhatsApp = { uris, emojis -> viewModel.importToTelegram(uris, emojis) }
                )

                LaunchedEffect(phase) {
                    if (phase is ImportPhase.Downloading || phase is ImportPhase.Converting) {
                        navController.navigate(Routes.PROGRESS)
                    }
                }
            }

            composable(Routes.PROGRESS) {
                ProgressScreen(
                    phase = phase,
                    packName = currentPackId ?: "Unknown",
                    onDone = {
                        viewModel.resetPhase()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PROGRESS) { inclusive = true }
                        }
                    },
                    onRetry = {
                        viewModel.resetPhase()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PROGRESS) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.MY_PACKS) {
                MyPacksScreen(viewModel)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel)
            }
        }
    }
}
