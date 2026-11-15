package com.madovygabor.maturimate.features.auth.domain

/**
 * Where the exported data goes: a file the user picks, on both platforms (the system's "save as" picker on Android, a save
 * dialog on the desktop; each platform's `core/export`), and on Android also the system's share sheet. Kept apart from
 * [AccountSettings], so a platform can change the place without touching it.
 */
fun interface ExportSaver {
    /**
     * Returns where the file went (shown to the user: a file name or a path), [CANCELLED] when the user closed the picker
     * (nothing to show), or null when it could not be saved.
     */
    suspend fun save(fileName: String, text: String): String?

    /** Whether [share] works on this platform (Android only); the settings offer "share" only then. */
    val canShare: Boolean get() = false

    /**
     * Hands the file to the system's share sheet; returns the file name once the sheet is open, or null when it could not be
     * opened. What the user does there is not known, so the caller says "shared", never "saved". Without [canShare]: null.
     */
    suspend fun share(fileName: String, text: String): String? = null

    companion object {
        /** The user closed the picker without choosing a place. */
        const val CANCELLED = ""
    }
}
