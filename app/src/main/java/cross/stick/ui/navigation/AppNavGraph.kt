package cross.stick.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cross.stick.ui.screens.*
import cross.stick.viewmodel.ImportPhase
import cross.stick.viewmodel.MainViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROGRESS = "progress"
}

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = androidx.navigation.compose.rememberNavController()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState(initial = false)
    val isReady by viewModel.isReady.collectAsState(initial = false)
    val phase by viewModel.phase.collectAsState()
    val currentPackId by viewModel.currentPackId.collectAsState()
    val startDestination = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING

    if (isReady) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
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
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onRetry = {
                        viewModel.resetPhase()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
