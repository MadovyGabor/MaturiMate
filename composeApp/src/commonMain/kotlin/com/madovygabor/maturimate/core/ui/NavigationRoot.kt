package com.madovygabor.maturimate.core.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.features.subjects.presentation.ExamSelectionScreen
import com.madovygabor.maturimate.features.subjects.presentation.SubjectListTabScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Composable
fun NavigationRoute(){


    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration{
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.SubjectListTabScreen::class, Route.SubjectListTabScreen.serializer())
                    subclass(Route.SubjectDetailList::class, Route.SubjectDetailList.serializer())

                }
            }
        },
        Route.SubjectListTabScreen
    )

    NavDisplay(
        backStack = backStack,
        entryProvider = {key ->
            when(key){
                is Route.SubjectListTabScreen -> {
                    NavEntry(key){
                        SubjectListTabScreen(
                            onSubjectClick = {
                                backStack.add(Route.SubjectDetailList(it))
                            }
                        )
                    }
                }
                is Route.SubjectDetailList -> {
                    NavEntry(key){
                        ExamSelectionScreen(
                            subjectId = key.subjectId,
                        )
                    }
                }
                else -> error("Unknown nav key: $key")

            }
        }
    )
}