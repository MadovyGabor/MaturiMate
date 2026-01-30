package com.madovygabor.maturimate.core.presentation.navigation.MainFrame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.Route
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

// Class to manage the navigation state of the main frame with multiple top-level routes.
class MainFrameNavigationState(
    val startRoute: NavKey, // The initial route of the navigation
    topLevelRoute: MutableState<NavKey>, // The currently selected top-level route
    val backStacks: Map<NavKey, NavBackStack<NavKey>> // Back stacks for each top-level route
) {
    //
    var topLevelRoute by topLevelRoute

    // List of navigation stacks currently in use based on the selected top-level route
    val stacksInUse: List<NavKey>
        get() = if(topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

// Composable function to remember and create the navigation state for the main frame.
@Composable
fun rememberNavigationState(
    startRoute: NavKey, // The initial route of the navigation
    topLevelRoutes: Set<NavKey> // The set of top-level routes available
): MainFrameNavigationState {

    // Remember the currently selected top-level route with serialization support
    val topLevelRoute = rememberSerializable(
        startRoute,
        topLevelRoutes,
        configuration = serializersConfig,
        serializer = MutableStateSerializer(PolymorphicSerializer(NavKey::class))
    ) {
        mutableStateOf(startRoute)
    }

    // Create back stacks for each top-level route
    val backStacks = topLevelRoutes.associateWith { key ->
        rememberNavBackStack(
            configuration = serializersConfig,
            key
        )
    }

    // Return the remembered navigation state
    return remember(startRoute, topLevelRoutes) {
        MainFrameNavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

// Configuration for saving and restoring navigation state with polymorphic serialization
val serializersConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Main.Subject::class, Route.Main.Subject.serializer())
            subclass(Route.Main.History::class, Route.Main.History.serializer())
            subclass(Route.Main.Profile::class, Route.Main.Profile.serializer())

        }
    }
}

// Extension function to convert the navigation state to a list of navigation entries.
@Composable
fun MainFrameNavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): SnapshotStateList<NavEntry<NavKey>> {

    // Decorate the navigation entries for each back stack
    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator()
        )
        // Remember the decorated navigation entries
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )
    }

    // Combine the entries from the stacks currently in use into a single list
    return stacksInUse
        .flatMap { decoratedEntries[it] ?: emptyList() }
        .toMutableStateList()
}