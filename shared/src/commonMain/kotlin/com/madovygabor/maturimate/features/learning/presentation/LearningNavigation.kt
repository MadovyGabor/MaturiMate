package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Where to open the subjects tab: the subject and the topics from the big part down to the one that holds the
 * step, so the back stack is built as if the student had walked there.
 */
data class LearningDeepLink(val subjectId: Long, val topicIds: List<Long>)

/**
 * The navigation of the subjects tab: the subjects, the parts of a subject, and then the topics level by
 * level down to the path of a topic. Its own back stack lives inside the tab.
 */
@Composable
fun LearningNavigation(
    onOpenLesson: (Long) -> Unit,
    onOpenTest: (Long) -> Unit,
    onSignUp: () -> Unit = {},
    deepLink: LearningDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
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

    // opened from the home screen: replace the stack with the way to the step, and open nothing
    LaunchedEffect(deepLink) {
        if (deepLink == null) return@LaunchedEffect
        opened[0] = CatalogSubject.byId(deepLink.subjectId)
        backStack.clear()
        backStack.add(Route.Main.Subject.Selection)
        backStack.add(Route.Main.Subject.Parts(deepLink.subjectId))
        deepLink.topicIds.forEach { backStack.add(Route.Main.Subject.Topic(it)) }
        onDeepLinkHandled()
    }

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
                    onSignUp = onSignUp,
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
                    onSignUp = onSignUp,
                )
            }
        },
    )
}
