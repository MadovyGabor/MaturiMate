package com.madovygabor.maturimate.core.export

import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Saves the exported data where the user says: [chooseFile] shows the save dialog and returns the chosen file, or null when
 * the dialog was closed. The text is written only after a choice, so nothing lands in the home folder unasked.
 */
class DesktopExportSaver(
    private val chooseFile: (suggestedName: String) -> File? = ::showSaveDialog,
) : ExportSaver {

    override suspend fun save(fileName: String, text: String): String? =
        try {
            withContext(Dispatchers.IO) {
                val file = chooseFile(fileName) ?: return@withContext ExportSaver.CANCELLED
                file.writeText(text)
                file.absolutePath
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // no file, no right to write there, no display: the user is told it could not be saved (never what was in it)
            null
        }
}

/** The AWT save dialog; blocks until it is closed. Adds ".json" when the user typed a name without an extension. */
private fun showSaveDialog(suggestedName: String): File? {
    val dialog = FileDialog(null as Frame?, "", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val directory = dialog.directory ?: return null
    return File(directory, if (name.contains('.')) name else "$name.json")
}
