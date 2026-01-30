package com.madovygabor.maturimate.core.presentation.navigation.MainFrame

import androidx.navigation3.runtime.NavKey

// Navigator class to handle navigation within the MainFrame.
class MainFrameNavigator(val state: MainFrameNavigationState) {

    // Navigate to a specified route.
    fun navigate(route: NavKey) {
        // If the route is a top-level route, switch to it. Otherwise, add it to the current back stack.
        if(route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    // Handle back navigation.
    fun goBack() {
        // Get the current back stack and route.
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Back stack for ${state.topLevelRoute} doesn't exist")
        val currentRoute = currentStack.last()

        // If at the top-level route, go back to the start route. Otherwise, pop the last route from the stack.
        if(currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}