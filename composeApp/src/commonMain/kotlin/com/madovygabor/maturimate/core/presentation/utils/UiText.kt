package com.madovygabor.maturimate.core.presentation.utils

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.getString

/**
 * A sealed class representing text that can be either a raw [String] or a localized [StringResource].
 * This allows ViewModels to provide UI-ready text without having a reference to the Android Context
 * or the Compose Resource Manager.
 */
sealed class UiText {

    /**
     * Represents a hardcoded or dynamic string.
     */
    data class DynamicString(val value: String) : UiText()

    /**
     * Represents a localized string resource with optional formatting arguments.
     */
    class StringRes(
        val res: StringResource,
        val args: Array<Any> = arrayOf()
    ) : UiText() {
        // Custom equals/hashCode because Array doesn't have structural equality by default
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StringRes) return false
            if (res != other.res) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int {
            var result = res.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    /**
     * Resolves the string in a Composable context.
     * Uses the standard [stringResource] function.
     */
    @Composable
    fun asString(): String {

        val rawText = when (this) {
            is DynamicString -> value
            is StringRes -> stringResource(res, *args)
        }
        // Trimming quotes ensures clean text display on all platforms
        return rawText.trim('\"')
    }

    /**
     * Resolves the string in a non-Composable (suspend) context.
     * Useful for Snackbars, Notifications, or Logging within a CoroutineScope.
     * Also handles potential accidental quote wrapping from certain resource exports.
     */
    suspend fun asStringSuspend(): String {
        val rawText = when (this) {
            is DynamicString -> value
            is StringRes -> getString(res, *args)
        }
        // Trimming quotes ensures clean text display on all platforms
        return rawText.trim('\"')
    }
}