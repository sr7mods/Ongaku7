package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.player.PlaybackViewModel
import com.example.ui.components.BackgroundGlow
import com.example.ui.screens.MainScreen
import com.example.ui.screens.PlayerDetailsScreen
import com.example.ui.screens.PlaylistManagerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
    SPLASH,
    MAIN,
    SETTINGS,
    PLAYER_DETAILS,
    PLAYLISTS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val viewModel: PlaybackViewModel = viewModel()

            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                var currentScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(AppScreen.SPLASH) }

                androidx.activity.compose.BackHandler(enabled = currentScreen != AppScreen.MAIN && currentScreen != AppScreen.SPLASH) {
                    currentScreen = AppScreen.MAIN
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    BackgroundGlow(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                when {
                                    targetState == AppScreen.PLAYER_DETAILS -> {
                                        (slideInVertically(
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                            ),
                                            initialOffsetY = { fullHeight -> fullHeight }
                                        ) + fadeIn(androidx.compose.animation.core.tween(300)) + scaleIn(initialScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(300)))
                                        .togetherWith(
                                            fadeOut(androidx.compose.animation.core.tween(220)) + scaleOut(targetScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(220))
                                        )
                                    }
                                    initialState == AppScreen.PLAYER_DETAILS -> {
                                        (fadeIn(androidx.compose.animation.core.tween(250)) + scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(250)))
                                        .togetherWith(
                                            slideOutVertically(
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                                ),
                                                targetOffsetY = { fullHeight -> fullHeight }
                                            ) + fadeOut(androidx.compose.animation.core.tween(220)) + scaleOut(targetScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(220))
                                        )
                                    }
                                    targetState == AppScreen.SETTINGS || targetState == AppScreen.PLAYLISTS -> {
                                        (slideInHorizontally(
                                            animationSpec = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            initialOffsetX = { fullWidth -> fullWidth }
                                        ) + fadeIn(androidx.compose.animation.core.tween(300)))
                                        .togetherWith(
                                            slideOutHorizontally(
                                                animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                                targetOffsetX = { fullWidth -> -fullWidth / 3 }
                                            ) + fadeOut(androidx.compose.animation.core.tween(200))
                                        )
                                    }
                                    initialState == AppScreen.SETTINGS || initialState == AppScreen.PLAYLISTS -> {
                                        (slideInHorizontally(
                                            animationSpec = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            initialOffsetX = { fullWidth -> -fullWidth / 3 }
                                        ) + fadeIn(androidx.compose.animation.core.tween(300)))
                                        .togetherWith(
                                            slideOutHorizontally(
                                                animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                                targetOffsetX = { fullWidth -> fullWidth }
                                            ) + fadeOut(androidx.compose.animation.core.tween(200))
                                        )
                                    }
                                    initialState == AppScreen.SPLASH && targetState == AppScreen.MAIN -> {
                                        (fadeIn(androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                         scaleIn(initialScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)))
                                        .togetherWith(
                                            fadeOut(androidx.compose.animation.core.tween(350)) +
                                            scaleOut(targetScale = 1.08f, animationSpec = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                        )
                                    }
                                    else -> {
                                        fadeIn(androidx.compose.animation.core.tween(300)) togetherWith fadeOut(androidx.compose.animation.core.tween(250))
                                    }
                                }
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                AppScreen.SPLASH -> {
                                    com.example.ui.screens.SplashScreen(
                                        onSplashComplete = { currentScreen = AppScreen.MAIN }
                                    )
                                }
                                AppScreen.MAIN -> {
                                    MainScreen(
                                        viewModel = viewModel,
                                        onSettingsClick = { currentScreen = AppScreen.SETTINGS },
                                        onMiniPlayerClick = { currentScreen = AppScreen.PLAYER_DETAILS },
                                        onPlaylistClick = { currentScreen = AppScreen.PLAYLISTS }
                                    )
                                }
                                AppScreen.PLAYLISTS -> {
                                    PlaylistManagerScreen(
                                        viewModel = viewModel,
                                        onBack = { currentScreen = AppScreen.MAIN },
                                        onMiniPlayerClick = { currentScreen = AppScreen.PLAYER_DETAILS }
                                    )
                                }
                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onBack = { currentScreen = AppScreen.MAIN }
                                    )
                                }
                                AppScreen.PLAYER_DETAILS -> {
                                    PlayerDetailsScreen(
                                        viewModel = viewModel,
                                        onClose = { currentScreen = AppScreen.MAIN }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
