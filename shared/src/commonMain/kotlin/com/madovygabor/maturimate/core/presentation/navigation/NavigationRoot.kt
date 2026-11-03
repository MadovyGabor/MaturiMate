package com.madovygabor.maturimate.core.presentation.navigation

import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.MutableState
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.MainFrame.MainFrameNavigation
import com.madovygabor.maturimate.features.appOnboarding.presentation.AppOnboardingNavigation
import com.madovygabor.maturimate.features.examTaking.ExamWriteScreen
import com.madovygabor.maturimate.features.learning.presentation.LessonScreen
import com.madovygabor.maturimate.features.learning.presentation.TestScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

// Root navigation composable that manages top-level navigation routes.
@Composable
fun NavigationRoute(
    modifier: Modifier,
    startRoute: Route,
    /** The tab the main screen opens on; it follows the user's choice. */
    mainTab: MutableState<NavKey>,
    /** The language the app is built with (`AppState.language`). */
    appLanguage: TeachingLanguage?,
){

    val rootBackStack = rememberNavBackStack(
        configuration = SavedStateConfiguration{
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    // Root level routes
                    subclass(Route.Main::class, Route.Main.serializer())
                    subclass(Route.AppOnboarding::class, Route.AppOnboarding.serializer())
                    subclass(Route.ExamWrite::class, Route.ExamWrite.serializer())
                    subclass(Route.Lesson::class, Route.Lesson.serializer())
                    subclass(Route.Test::class, Route.Test.serializer())
                }
            }
        },

        startRoute,
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
                MainFrameNavigation(mainTab = mainTab, onOpenLesson = { rootBackStack.add(Route.Lesson(it)) },
                    onOpenTest = { rootBackStack.add(Route.Test(it)) },
                )
            }

            //Initial Setup Screen for first-time users
            entry<Route.AppOnboarding> {
                // finishing it saves the onboarding end; App then starts on Main
                AppOnboardingNavigation(appLanguage = appLanguage)
            }
            // A test, one task at a time, without the navigation bar
            entry<Route.Test> { route ->
                TestScreen(unitId = route.unitId, onClose = { rootBackStack.removeLastOrNull() })
            }
            // A lesson, one page at a time, without the navigation bar
            entry<Route.Lesson> { route ->
                LessonScreen(
                    unitId = route.unitId,
                    onClose = { rootBackStack.removeLastOrNull() },
                    onOpenTest = { rootBackStack.add(Route.Test(it)) },
                )
            }
            //Fullscreen exam writing screen
            entry<Route.ExamWrite> {
                ExamWriteScreen()
            }
        }
    )
}