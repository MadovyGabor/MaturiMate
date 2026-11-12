package com.madovygabor.maturimate.features.content

import com.madovygabor.maturimate.core.database.ContentManifest
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.content.data.AccessTokens
import com.madovygabor.maturimate.features.content.data.WorkerPackSource
import com.madovygabor.maturimate.features.content.data.packManifest
import com.madovygabor.maturimate.features.content.domain.ContentError
import com.madovygabor.maturimate.features.content.domain.ContentPacks
import com.madovygabor.maturimate.features.content.domain.ContentUpdater
import com.madovygabor.maturimate.features.content.domain.NetworkState
import com.madovygabor.maturimate.features.content.domain.PackSaveResult
import com.madovygabor.maturimate.features.content.domain.PackSource
import com.madovygabor.maturimate.features.content.domain.SubjectContentInfo
import com.madovygabor.maturimate.features.sync.domain.ContentRelease
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentUpdaterTest {

    private class FakePacks(val installed: MutableMap<Long, SubjectContentInfo> = mutableMapOf()) : ContentPacks {
        override val schemaVersion = 7L
        val saved = mutableListOf<ContentRelease>()
        /** Saved but not swapped in (a lesson is open). */
        val waiting = mutableMapOf<Long, SubjectContentInfo>()
        var swapAllowed = true
        var swaps = 0
        var reject = false
        override fun installedSubject(subjectId: Long) = installed[subjectId]
        override fun availableSubject(subjectId: Long) = waiting[subjectId] ?: installed[subjectId]
        override suspend fun savePack(release: ContentRelease, bytes: ByteArray): PackSaveResult {
            if (reject) return PackSaveResult.REJECTED
            saved += release
            waiting[release.subjectId] = SubjectContentInfo(isStarter = false, contentVersion = release.contentVersion)
            return PackSaveResult.SAVED
        }
        override suspend fun applyPending(): Boolean {
            if (!swapAllowed) return false
            swaps++
            installed.putAll(waiting)
            waiting.clear()
            return true
        }
    }

    private fun release(subject: Long, version: Long = 2026111201, schema: Long = 7, size: Long = 1000) =
        ContentRelease(subject, schema, version, size, "a".repeat(64))

    private val downloads = mutableListOf<Long>()
    private var failWith: ContentError? = null
    private val source = PackSource { r ->
        downloads += r.subjectId
        failWith?.let { OperationResult.Error(it) } ?: OperationResult.Success(ByteArray(r.sizeBytes.toInt()))
    }

    private fun updater(packs: FakePacks, subjects: List<Long> = listOf(1, 2), metered: Boolean = false) =
        ContentUpdater(packs, source, NetworkState { metered }, chosenSubjects = { subjects }, mobileDataAskBytes = 1500)

    @Test
    fun theOnlyPacksPlannedAreNewerOrReplaceAStarterOfAChosenSubjectAndThisSchema() = runBlocking {
        val packs = FakePacks(mutableMapOf(
            1L to SubjectContentInfo(isStarter = false, contentVersion = 2026111201),
            2L to SubjectContentInfo(isStarter = true, contentVersion = 2026111201),
            3L to SubjectContentInfo(isStarter = true, contentVersion = 2026100101),
        ))
        val plan = updater(packs, subjects = listOf(1, 2, 4)).plan(listOf(
            release(1),                 // same version, full already: nothing
            release(2),                 // same version, a starter: the full one replaces it
            release(3, 2026120101),     // not chosen
            release(4, schema = 8),     // a later app version reads it
        ))
        assertEquals(listOf(2L), plan.map { it.subjectId })
        val newer = updater(packs).plan(listOf(release(1, 2026120101)))
        assertEquals(listOf(1L), newer.map { it.subjectId })
    }

    @Test
    fun aDownloadKeepsThePackWithTheHashOfTheReleaseAndSwapsItIn() = runBlocking {
        val packs = FakePacks()
        val updater = updater(packs)

        updater.onReleases(listOf(release(1), release(2)))

        assertEquals(listOf(1L, 2L), downloads)
        assertEquals(listOf(1L, 2L), packs.saved.map { it.subjectId })
        // the pack is checked against the hash and the size of the release
        val manifest = packManifest(release(1))
        assertEquals("a".repeat(64), manifest.sha256)
        assertEquals(1000, manifest.sizeBytes)
        assertEquals(listOf(1L), manifest.subjectIds)
        assertEquals(ContentManifest.KIND_FULL, manifest.kind)
        assertEquals(1, packs.swaps)
        assertTrue(updater.state.value.pending.isEmpty())
        assertEquals(2026111201, updater.state.value.installedVersion)
    }

    @Test
    fun aPackThatWaitsForItsSwapIsNotDownloadedAgain() = runBlocking {
        val packs = FakePacks()
        // a lesson is open: the pack is kept but not swapped in
        packs.swapAllowed = false
        val updater = updater(packs)

        updater.onReleases(listOf(release(1)))
        // the next sync brings the same release while the lesson is still open
        updater.onReleases(listOf(release(1)))

        assertEquals(listOf(1L), downloads)
        packs.swapAllowed = true
        updater.onReleases(listOf(release(1)))
        assertEquals(listOf(1L), downloads)
        assertEquals(1, packs.swaps)
    }

    @Test
    fun aLargeDownloadOnMobileDataWaitsForTheUsersYes() = runBlocking {
        val packs = FakePacks()
        val updater = updater(packs, metered = true)

        updater.onReleases(listOf(release(1), release(2)))
        assertTrue(downloads.isEmpty())
        assertTrue(updater.state.value.needsMobileData)
        assertEquals(2000, updater.state.value.pendingBytes)

        updater.updateNow()
        assertEquals(listOf(1L, 2L), downloads)
        assertFalse(updater.state.value.needsMobileData)
    }

    @Test
    fun aSmallDownloadOnMobileDataDoesNotAsk() = runBlocking {
        updater(FakePacks(), metered = true).onReleases(listOf(release(1)))
        assertEquals(listOf(1L), downloads)
    }

    @Test
    fun aFailureStopsAndKeepsTheRestWaiting() = runBlocking {
        val packs = FakePacks()
        val updater = updater(packs)
        failWith = ContentError.NETWORK

        updater.onReleases(listOf(release(1), release(2)))

        assertEquals(ContentError.NETWORK, updater.state.value.lastError)
        assertEquals(2, updater.state.value.pending.size)
        assertTrue(packs.saved.isEmpty())
        // a pack the installer refuses (wrong hash) is not kept either
        failWith = null
        packs.reject = true
        updater.updateNow()
        assertEquals(ContentError.CORRUPT, updater.state.value.lastError)
    }

    // ---- the Worker client -------------------------------------------------------------------------------------------

    private class Tokens(var token: String? = "t1") : AccessTokens {
        var refreshes = 0
        override suspend fun current() = token
        override suspend fun refreshed(): String? { refreshes++; token = "t2"; return token }
    }

    private fun worker(link: (String?) -> Pair<HttpStatusCode, String>, file: ByteArray = ByteArray(1000)): Pair<WorkerPackSource, Tokens> {
        val tokens = Tokens()
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/link") {
                val (status, body) = link(request.headers["authorization"])
                respond(body, status, headersOf("content-type", "application/json"))
            } else respond(file, HttpStatusCode.OK)
        }
        return WorkerPackSource("https://worker.example/link", tokens, HttpClient(engine)) to tokens
    }

    @Test
    fun theWorkerGivesALinkAndTheFileComes() = runBlocking {
        val (source, _) = worker({ HttpStatusCode.OK to """{"url":"https://r2.example/packs/7/1/x/content.db","sha256":"ignored"}""" })
        val result = source.download(release(1))
        assertIs<OperationResult.Success<ByteArray, ContentError>>(result)
        assertEquals(1000, result.data.size)
    }

    @Test
    fun anExpiredSessionIsRefreshedOnce() = runBlocking {
        val (source, tokens) = worker({ auth ->
            if (auth == "Bearer t1") HttpStatusCode.Unauthorized to "{}" else HttpStatusCode.OK to """{"url":"https://r2.example/x"}"""
        })
        assertIs<OperationResult.Success<ByteArray, ContentError>>(source.download(release(1)))
        assertEquals(1, tokens.refreshes)
    }

    @Test
    fun theWorkersRefusalsAndAWrongSizeBecomeErrors() = runBlocking {
        assertEquals(OperationResult.Error<ByteArray, ContentError>(ContentError.LIMIT), worker({ HttpStatusCode.TooManyRequests to "{}" }).first.download(release(1)))
        assertEquals(OperationResult.Error<ByteArray, ContentError>(ContentError.FORBIDDEN), worker({ HttpStatusCode.Forbidden to "{}" }).first.download(release(1)))
        val short = worker({ HttpStatusCode.OK to """{"url":"https://r2.example/x"}""" }, file = ByteArray(10)).first
        assertEquals(OperationResult.Error<ByteArray, ContentError>(ContentError.CORRUPT), short.download(release(1)))
        val unset = WorkerPackSource("", Tokens(), HttpClient(MockEngine { respond("") }))
        assertEquals(OperationResult.Error<ByteArray, ContentError>(ContentError.NOT_CONFIGURED), unset.download(release(1)))
    }
}
