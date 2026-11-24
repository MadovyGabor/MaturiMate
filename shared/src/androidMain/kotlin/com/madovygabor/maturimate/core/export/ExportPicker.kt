package com.madovygabor.maturimate.core.export

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred

/**
 * The bridge between the export (which runs in a ViewModel) and the system's "save as" picker (which only an activity can
 * open): the activity calls [register] once, in `onCreate`; [pick] then opens the picker and waits for the answer.
 * Survives a rotation while the picker is open: the new activity registers again and gets the answer.
 */
class ExportPicker {
    private var launcher: ActivityResultLauncher<String>? = null
    private var waiting: CompletableDeferred<Uri?>? = null

    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            waiting?.complete(uri)
            waiting = null
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                launcher = null
            }
        })
    }

    /** The place the user chose, or null when the picker was closed. Throws when no activity is there to show it. */
    suspend fun pick(fileName: String): Uri? {
        val open = launcher ?: error("no activity to show the picker")
        val answer = CompletableDeferred<Uri?>()
        waiting = answer
        open.launch(fileName)
        return answer.await()
    }
}
