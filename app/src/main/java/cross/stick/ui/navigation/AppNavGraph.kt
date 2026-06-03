package cross.stick.ui.navigation

import androidx.compose.animation.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cross.stick.ui.screens.*
import cross.stick.viewmodel.MainViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DOWNLOADING = "downloading"
    const val PREVIEW = "preview"
    const val CONVERTING = "converting"
    const val MY_PACKS = "my_packs"
}

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = androidx.navigation.compose.rememberNavController()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState(initial = false)
    val isReady by viewModel.isReady.collectAsState(initial = false)
    val startDestination = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING

    if (isReady) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it/3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it/3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
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
                    onFetchPack = { link -> viewModel.fetchStickerSet(link) },
                    navigateToDownloading = { navController.navigate(Routes.DOWNLOADING) },
                    onImportFromWhatsApp = { uris, emojis -> viewModel.importToTelegram(uris, emojis) }
                )
            }

            composable(Routes.DOWNLOADING) {
                val stickerSet = viewModel.stickerSet.collectAsState().value
                val packName = stickerSet?.name ?: "Unknown pack"
                DownloadingScreen(packName = packName, progress = 50)
                LaunchedEffect(viewModel.isDownloading) {
                    if (!viewModel.isDownloading.value) {
                        navController.navigate(Routes.PREVIEW) {
                            popUpTo(Routes.DOWNLOADING) { inclusive = true }
                        }
                    }
                }
            }

            composable(Routes.PREVIEW) {
                val files = viewModel.downloadedFilePaths
                PreviewScreen(
                    files = files,
                    onDelete = { /* TODO */ },
                    onAddFiles = { /* TODO */ },
                    onConvert = {
                        viewModel.convertAllStickers()
                        navController.navigate(Routes.CONVERTING) {
                            popUpTo(Routes.PREVIEW) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.CONVERTING) {
                val packId = viewModel.currentPackId.collectAsState().value ?: "unknown"
                ConvertingScreen(packName = packId, progress = 70)
                LaunchedEffect(viewModel.isConverting) {
                    if (!viewModel.isConverting.value) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                }
            }

            composable(Routes.MY_PACKS) {
                Text("My Packs - coming soon")
            }
        }
    }
}
