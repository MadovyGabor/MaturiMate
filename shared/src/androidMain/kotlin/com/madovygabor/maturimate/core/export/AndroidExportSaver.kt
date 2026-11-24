package com.madovygabor.maturimate.core.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Saves the exported data in the place the user picks (the system's "save as" screen, no storage permission), or hands it to
 * the share sheet. Only the share keeps a copy, in one cache folder that is emptied before every share and at every start of
 * the app ([clearExportShareCache]).
 */
class AndroidExportSaver(
    private val context: Context,
    private val picker: ExportPicker,
) : ExportSaver {

    override val canShare: Boolean get() = true

    override suspend fun save(fileName: String, text: String): String? =
        try {
            val uri = picker.pick(fileName)
            if (uri == null) {
                ExportSaver.CANCELLED
            } else {
                withContext(Dispatchers.IO) {
                    // "wt": an existing file the user chose to replace is cut to the new length
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("no output stream")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    displayName(uri) ?: fileName
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // never log what was exported (e-mail, answers)
            null
        }

    /**
     * The share sheet gets a content URI of a file in the cache (no size limit of an intent, unlike sharing the text itself);
     * the receiving app may read that one file, nothing else.
     */
    override suspend fun share(fileName: String, text: String): String? =
        try {
            withContext(Dispatchers.IO) {
                val folder = File(context.cacheDir, EXPORT_FOLDER)
                // a copy of an earlier share goes before the new one is written
                folder.deleteRecursively()
                folder.mkdirs()
                val file = File(folder, fileName)
                file.writeText(text)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.export", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                fileName
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}

private const val EXPORT_FOLDER = "export"

/**
 * Empties the export's cache folder: the copy the last share left there (and the copies of older versions) holds personal
 * data. Called at every start of the app.
 */
fun clearExportShareCache(context: Context) {
    File(context.cacheDir, EXPORT_FOLDER).deleteRecursively()
}
