package com.madovygabor.maturimate.features.subjectsAndExams.presentation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.Route
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Composable
fun SubjectNavigation() {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration{
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.Main.Subject.Selection::class, Route.Main.Subject.Selection.serializer())
                    subclass(Route.Main.Subject.ExamList::class, Route.Main.Subject.ExamList.serializer())

                }
            }
        },
        Route.Main.Subject.Selection
    )

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<Route.Main.Subject.Selection>{
                SubjectListTabScreen(
                    onSubjectClick = {
                        backStack.add(Route.Main.Subject.ExamList(it))
                    }
                )
            }
            entry<Route.Main.Subject.ExamList>{
                ExamSelectionScreen(
                    subjectId = it.subjectId,
                )
            }

        }

    )
}