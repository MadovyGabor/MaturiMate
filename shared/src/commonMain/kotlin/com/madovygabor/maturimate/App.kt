package com.madovygabor.maturimate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import kotlinx.serialization.PolymorphicSerializer
import com.madovygabor.maturimate.core.presentation.navigation.MainFrame.serializersConfig
import com.madovygabor.maturimate.features.appOnboarding.presentation.rememberOnboardingNavState
import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.AppLoadErrorScreen
import com.madovygabor.maturimate.core.presentation.AppViewModel
import com.madovygabor.maturimate.core.presentation.locale.LocalAppLocale
import com.madovygabor.maturimate.core.presentation.navigation.NavigationRoute
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.systemPadding
import com.madovygabor.maturimate.core.presentation.systemPaddingExceptBottom
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.MainScaffoldWrapper
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.koin.compose.viewmodel.koinViewModel

private val navKeyState = MutableStateSerializer(PolymorphicSerializer(NavKey::class))

/**
 * The root Composable of the MaturiMate application.
 * Sets up the global theme, scaffold, the app language and the navigation entry point.
 *
 * The language and the start screen come from the saved profile. On platforms where
 * [LocalAppLocale.rebuildsTreeOnChange] is true (desktop), a language change rebuilds the
 * screens via `key(...)`; on Android, `LocalConfiguration` updates them in place without
 * tearing down the navigation stack or interrupting transitions.
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val failed by viewModel.failed.collectAsState()

    // the open tab outlives the rebuild (so a language changed in the profile stays there), and the process: saved like the
    // tabs of the main frame
    val mainTab = rememberSerializable(configuration = serializersConfig, serializer = navKeyState) { mutableStateOf<NavKey>(Route.Main.Home) }
    // and so does the open page of the profile tab (the settings, where the language is changed)
    val profilePage = rememberSerializable(configuration = serializersConfig, serializer = navKeyState) { mutableStateOf<NavKey>(Route.Main.Profile.Overview) }
    // the onboarding's back stack outlives the rebuild too: a language changed on the way back does not rebuild it
    val onboardingState = rememberOnboardingNavState()

    // the sync runs when the app opens and when it comes back to the front (at most every five minutes)
    LifecycleResumeEffect(Unit) {
        viewModel.onForeground()
        onPauseOrDispose { }
    }

    // Apply the application's Material 3 theme
    AppTheme {
        // Global scaffold wrapper that manages Snackbars and system-level UI
        MainScaffoldWrapper {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
            ) {
                // nothing to show until the profile has been read once
                val appState = state ?: run {
                    if (failed) AppLoadErrorScreen(onRetry = viewModel::retry, modifier = Modifier.systemPadding())
                    return@Box
                }
                // another user on this phone (an in-app sign-in, a sign-out): the app opens on the home screen
                var knownUser by rememberSaveable { mutableStateOf<String?>(null) }
                remember(appState.userId) {
                    if (knownUser != null && knownUser != appState.userId) {
                        mainTab.value = Route.Main.Home
                        profilePage.value = Route.Main.Profile.Overview
                        onboardingState.seeded = false
                        onboardingState.backStack.clear()
                        onboardingState.backStack.add(Route.AppOnboarding.Welcome)
                    }
                    knownUser = appState.userId
                    appState.userId
                }
                CompositionLocalProvider(LocalAppLocale provides appState.language?.name?.lowercase()) {
                    key(
                        if (LocalAppLocale.rebuildsTreeOnChange) appState.language else null,
                        appState.onboardingCompleted,
                        appState.userId,
                    ) {
                        // Main navigation host that handles transitions between routes
                        NavigationRoute(
                            modifier = Modifier.systemPaddingExceptBottom(),
                            startRoute = if (appState.onboardingCompleted) Route.Main else Route.AppOnboarding,
                            mainTab = mainTab,
                            profilePage = profilePage,
                            onboardingState = onboardingState,
                            appLanguage = appState.language,
                            onReadingChanged = viewModel::onReadingChanged,
                            onStartReady = viewModel::onStartReady,
                        )
                    }
                }
            }
        }
    }
}
