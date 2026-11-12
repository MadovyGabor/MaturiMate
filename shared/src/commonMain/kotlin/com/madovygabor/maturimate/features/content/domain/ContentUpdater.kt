package com.madovygabor.maturimate.features.content.domain

import com.madovygabor.maturimate.core.domain.error.Error
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.sync.domain.ContentRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** What the phone holds of one subject: only its first lesson (a starter), or the full pack, and its version. */
data class SubjectContentInfo(val isStarter: Boolean, val contentVersion: Long)

/** What happened to a downloaded pack. */
enum class PackSaveResult {
    /** Kept; it is swapped in when no lesson is open. */
    SAVED,
    /** The phone holds this version or a newer one already. */
    UP_TO_DATE,
    /** Not the file the server announced (size, hash) or not a pack of this subject. */
    REJECTED,
}

/** The installed content as the updater sees it. */
interface ContentPacks {
    /** The schema version of the content database this app reads; a pack of another one is for another app version. */
    val schemaVersion: Long

    /** What the installed content file holds of the subject; null when nothing. */
    fun installedSubject(subjectId: Long): SubjectContentInfo?

    /**
     * The better of the installed content and a downloaded pack that waits for its swap: what the phone has already, so a
     * pack that waits is not downloaded again.
     */
    fun availableSubject(subjectId: Long): SubjectContentInfo?

    /** Keeps the bytes of the release, checked against the size and the hash of the release. */
    suspend fun savePack(release: ContentRelease, bytes: ByteArray): PackSaveResult

    /** Swaps the downloaded packs in, when no lesson, test or round is open; true when it did. */
    suspend fun applyPending(): Boolean
}

/**
 * The navigation tells it whether a lesson, a test or a practice round is open: the content file is not swapped under an
 * open one, and the packs that waited are swapped in when it closes.
 */
fun interface ContentSwapGate {
    suspend fun setReading(reading: Boolean)
}

enum class ContentError : Error {
    NETWORK,
    NOT_SIGNED_IN,
    /** The subject is not the user's. */
    FORBIDDEN,
    /** Three downloads of the subject today already. */
    LIMIT,
    /** The file that came is not the one the server announced (size or hash). */
    CORRUPT,
    /** The link service is not set up in this build. */
    NOT_CONFIGURED,
    UNKNOWN,
}

/** Where the bytes of a pack come from: a short-lived link from the Worker, then the file. */
fun interface PackSource {
    suspend fun download(release: ContentRelease): OperationResult<ByteArray, ContentError>
}

/** A pack that waits: its release, and whether it waits for the user's yes to mobile data. */
data class PendingPack(val release: ContentRelease, val needsMobileData: Boolean = false)

/** What the settings row "Tartalom frissítése" shows. */
data class ContentUpdateState(
    val pending: List<PendingPack> = emptyList(),
    val downloading: Boolean = false,
    val lastError: ContentError? = null,
    /** The highest installed content version, for the row. */
    val installedVersion: Long? = null,
) {
    val pendingBytes: Long get() = pending.sumOf { it.release.sizeBytes }
    val needsMobileData: Boolean get() = pending.any { it.needsMobileData }
}

/** Whether the phone is on a network that may cost money (mobile data). */
fun interface NetworkState {
    fun isMetered(): Boolean
}

/**
 * Keeps the content of the chosen subjects up to date (roadmap 12.4, plan-12 section 6.4): after a sync it compares the
 * releases of the server with what is installed, downloads what is newer (or replaces a starter) one pack at a time, checks
 * the size and the hash against the release the server announced (not against the link: a link cannot change the
 * expected hash), keeps the pack, and has it swapped in when no lesson is open. On a metered network a total above
 * [mobileDataAskBytes] waits for the user's yes; below it nothing is asked. A failure leaves the installed content as it was.
 */
class ContentUpdater(
    private val packs: ContentPacks,
    private val source: PackSource,
    private val network: NetworkState,
    private val chosenSubjects: suspend () -> List<Long>,
    private val mobileDataAskBytes: Long = MOBILE_DATA_ASK_BYTES,
    /** Where a download asked from a screen runs, so it goes on when the screen closes. */
    private val scope: CoroutineScope? = null,
) {
    private val mutex = Mutex()
    private var releases: List<ContentRelease> = emptyList()

    private val _state = MutableStateFlow(ContentUpdateState())
    val state: StateFlow<ContentUpdateState> = _state.asStateFlow()

    /** The packs that should be downloaded, of the releases the server sent. */
    suspend fun plan(releases: List<ContentRelease>): List<ContentRelease> {
        val subjects = chosenSubjects().toSet()
        return releases.filter { release ->
            release.subjectId in subjects && release.schemaVersion == packs.schemaVersion && needs(release)
        }.sortedBy { it.subjectId }
    }

    private fun needs(release: ContentRelease): Boolean {
        // a pack downloaded before but not swapped in yet counts: it is not downloaded again
        val held = packs.availableSubject(release.subjectId) ?: return true
        // a full pack of the same version replaces a starter; a newer version replaces anything
        return held.isStarter || held.contentVersion < release.contentVersion
    }

    /** After a sync: plans and downloads (without asking, unless mobile data and a large total). */
    suspend fun onReleases(releases: List<ContentRelease>) {
        this.releases = releases
        update(allowMobileData = false)
    }

    /** The settings row: the same, and the user said yes to mobile data. */
    suspend fun updateNow() = update(allowMobileData = true)

    /** [updateNow] in the background: a screen can close meanwhile. */
    fun requestUpdate() {
        scope?.launch { updateNow() }
    }

    private suspend fun update(allowMobileData: Boolean) {
        if (!mutex.tryLock()) return
        try {
            val todo = plan(releases)
            val total = todo.sumOf { it.sizeBytes }
            if (todo.isEmpty()) {
                // nothing to download, but a pack downloaded earlier may still wait for its swap
                packs.applyPending()
                publish(emptyList(), downloading = false, error = null)
                return
            }
            if (!allowMobileData && total > mobileDataAskBytes && network.isMetered()) {
                publish(todo.map { PendingPack(it, needsMobileData = true) }, downloading = false, error = null)
                return
            }
            var error: ContentError? = null
            val left = todo.toMutableList()
            publish(left.map { PendingPack(it) }, downloading = true, error = null)
            for (release in todo) {
                when (val result = source.download(release)) {
                    is OperationResult.Error -> {
                        error = result.error
                        break
                    }
                    is OperationResult.Success -> {
                        if (packs.savePack(release, result.data) == PackSaveResult.REJECTED) {
                            error = ContentError.CORRUPT
                            break
                        }
                        left.remove(release)
                        publish(left.map { PendingPack(it) }, downloading = true, error = null)
                    }
                }
            }
            // whatever came is swapped in now, or when the open lesson closes
            packs.applyPending()
            publish(left.map { PendingPack(it) }, downloading = false, error = error)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun publish(pending: List<PendingPack>, downloading: Boolean, error: ContentError?) {
        val subjects = chosenSubjects()
        _state.value = ContentUpdateState(
            pending = pending,
            downloading = downloading,
            lastError = error,
            installedVersion = subjects.mapNotNull { packs.installedSubject(it)?.contentVersion }.maxOrNull(),
        )
    }

    companion object {
        /** Above this total a download over mobile data waits for the user's yes. */
        const val MOBILE_DATA_ASK_BYTES = 100L * 1024 * 1024
    }
}
