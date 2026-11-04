package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The navigation of the subjects tab: the subjects, the parts of a subject, and then the topics level by
 * level down to the path of a topic. Its own back stack lives inside the tab.
 */
@Composable
fun LearningNavigation(onOpenLesson: (Long) -> Unit, onOpenTest: (Long) -> Unit) {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.Main.Subject.Selection::class, Route.Main.Subject.Selection.serializer())
                    subclass(Route.Main.Subject.Parts::class, Route.Main.Subject.Parts.serializer())
                    subclass(Route.Main.Subject.Topic::class, Route.Main.Subject.Topic.serializer())
                }
            }
        },
        Route.Main.Subject.Selection,
    )
    // the subject the user opened: its name is the label above the parts and topics
    val opened = remember { arrayOfNulls<CatalogSubject>(1) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.Main.Subject.Selection> {
                SubjectListScreen(onSubjectClick = { subject ->
                    opened[0] = subject
                    backStack.add(Route.Main.Subject.Parts(subject.id))
                })
            }
            entry<Route.Main.Subject.Parts> { route ->
                TopicScreen(
                    target = TopicTarget.SubjectParts(route.subjectId),
                    subject = opened[0] ?: CatalogSubject.byId(route.subjectId),
                    onBack = { backStack.removeLastOrNull() },
                    onTopicClick = { backStack.add(Route.Main.Subject.Topic(it.topic.id)) },
                    onLessonClick = onOpenLesson,
                    onTestClick = onOpenTest,
                )
            }
            entry<Route.Main.Subject.Topic> { route ->
                TopicScreen(
                    target = TopicTarget.Topic(route.topicId),
                    subject = opened[0],
                    onBack = { backStack.removeLastOrNull() },
                    onTopicClick = { backStack.add(Route.Main.Subject.Topic(it.topic.id)) },
                    onLessonClick = onOpenLesson,
                    onTestClick = onOpenTest,
                )
            }
        },
    )
}
