package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.auth.presentation.AccountScreen
import com.madovygabor.maturimate.features.auth.presentation.SignInScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The navigation of the profile tab: the profile, the full history, a guest's sign-up and sign-in, and the settings behind the gear (until the
 * settings screen of 9.4 they are the onboarding answers). Its own back stack lives inside the tab.
 */
@Composable
fun ProfileNavigation(openPage: MutableState<NavKey> = remember { mutableStateOf(Route.Main.Profile.Overview) }) {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.Main.Profile.Overview::class, Route.Main.Profile.Overview.serializer())
                    subclass(Route.Main.Profile.History::class, Route.Main.Profile.History.serializer())
                    subclass(Route.Main.Profile.Answers::class, Route.Main.Profile.Answers.serializer())
                    subclass(Route.Main.Profile.SignUp::class, Route.Main.Profile.SignUp.serializer())
                    subclass(Route.Main.Profile.SignIn::class, Route.Main.Profile.SignIn.serializer())
                }
            }
        },
        Route.Main.Profile.Overview,
    )
    // a rebuild (a language changed in the answers) reopens the page that was open; read once
    remember(backStack) {
        // only the answers and the history: a sign-in that brings another language must not open the sign-in again
        val page = openPage.value
        val reopen = page == Route.Main.Profile.Answers || page == Route.Main.Profile.History
        if (reopen && backStack.lastOrNull() == Route.Main.Profile.Overview) backStack.add(page)
    }
    LaunchedEffect(backStack.lastOrNull()) { backStack.lastOrNull()?.let { openPage.value = it } }
    // the sign-up step of the path asks for the account screen
    LaunchedEffect(openPage.value) {
        if (openPage.value == Route.Main.Profile.SignUp && backStack.lastOrNull() != Route.Main.Profile.SignUp) backStack.add(Route.Main.Profile.SignUp)
    }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.Main.Profile.Overview> {
                ProfileOverviewScreen(
                    onSettings = { backStack.add(Route.Main.Profile.Answers) },
                    onHistory = { backStack.add(Route.Main.Profile.History) },
                    onSignUp = { backStack.add(Route.Main.Profile.SignUp) },
                    onSignIn = { backStack.add(Route.Main.Profile.SignIn) },
                )
            }
            entry<Route.Main.Profile.History> { HistoryScreen(onBack = back) }
            entry<Route.Main.Profile.Answers> {
                SettingsScreen(onBack = back, onSignUp = { backStack.add(Route.Main.Profile.SignUp) }, onSignIn = { backStack.add(Route.Main.Profile.SignIn) })
            }
            // the account screens of the onboarding, without its steps and "Körülnézek"; the guest's rows move
            // to the account (LocalUserProvider.adoptAccount), and the profile then shows the name
            entry<Route.Main.Profile.SignUp> { AccountScreen(role = null, onBack = back, inApp = true, onDone = back) }
            entry<Route.Main.Profile.SignIn> { SignInScreen(onBack = back, onSignedIn = { back() }) }
        },
    )
}
