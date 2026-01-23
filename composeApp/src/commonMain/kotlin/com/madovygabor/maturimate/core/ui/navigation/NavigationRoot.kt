package com.madovygabor.maturimate.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.features.appOnboarding.presentation.AppOnboardingNavigation
import com.madovygabor.maturimate.features.examTaking.ExamWriteScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

// Root navigation composable that manages top-level navigation routes.
@Composable
fun NavigationRoute(
    modifier: Modifier
){

//    // Koin segítségével injektáljuk a repository-t
//    val repository: UserPreferencesRepository = koinInject()
//
//    // Figyeljük a preferenciákat. A null kezdeti érték jelzi, hogy még töltünk.
//    val prefs by repository.preferences.collectAsState(initial = null)
//
//    // Amíg tölt az adatbázis/DataStore, ne mutassunk semmit vagy mutassunk Splash-t
//    if (prefs == null) {
//        // Opcionális: LoadingScreen()
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(color = MaterialTheme.colorScheme.background)
//        )
//        return
//    }
//
//    // Itt dől el, mi az alapértelmezett útvonal
//    val startRoute = if (prefs!!.isCompleted) Route.Main else Route.InitialSetup

    val rootBackStack = rememberNavBackStack(
        configuration = SavedStateConfiguration{
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    // Root level routes
                    subclass(Route.Main::class, Route.Main.serializer())
                    subclass(Route.AppOnboarding::class, Route.AppOnboarding.serializer())
                    subclass(Route.ExamWrite::class, Route.ExamWrite.serializer())
                }
            }
        },

        Route.AppOnboarding, // TODO: Change to Route.InitialSetup to test the onboarding flow first
    )

    NavDisplay(
        modifier = modifier,
        backStack = rootBackStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            //The main application content with bottom navigation
            entry<Route.Main> {
                MainFrameNavigation()
            }

            //Initial Setup Screen for first-time users
            entry<Route.AppOnboarding> {
                AppOnboardingNavigation(
                    onAppOnboardingComplete = {
                        // After onboarding is complete, navigate to the main app
                        rootBackStack.add(Route.Main)
                        rootBackStack.remove(Route.AppOnboarding)
                    }
                )
            }
            //Fullscreen exam writing screen
            entry<Route.ExamWrite> {
                ExamWriteScreen()
            }
        }
    )
}