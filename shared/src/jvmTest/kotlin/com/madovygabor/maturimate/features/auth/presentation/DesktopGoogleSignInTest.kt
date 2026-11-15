package com.madovygabor.maturimate.features.auth.presentation

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.SignInsInFlight
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The desktop Google sign-in (N1, F1, F5): the app's own PKCE receiver on the loopback, and a sign-in only when the
 * saved session is the one the exchange made.
 */
class DesktopGoogleSignInTest {

    /** Supabase Auth faked; [browser] plays the browser: it gets the redirect address. */
    private class FakeBrowserAuth(
        var session: SessionKey? = null,
        val exchanged: SessionKey = SessionKey("user-1", "new-token"),
        val exchangeFails: Exception? = null,
        /** How long the exchange takes on the server. */
        val exchangeTakes: kotlin.time.Duration = kotlin.time.Duration.ZERO,
        val browser: suspend (redirectUrl: String) -> Unit = {},
    ) : BrowserAuth {
        val codes = mutableListOf<String>()
        var opened = 0
        /** Completed when an exchange starts. */
        val exchangeStarted = CompletableDeferred<Unit>()
        override fun googleSignInUrl(redirectUrl: String) = redirectUrl
        override suspend fun openBrowser(url: String) {
            opened++
            browser(url)
        }
        override suspend fun exchangeCode(code: String): SessionKey {
            codes += code
            exchangeStarted.complete(Unit)
            delay(exchangeTakes)
            exchangeFails?.let { throw it }
            session = exchanged
            return exchanged
        }
        override fun currentSession() = session
    }

    private class Outcome {
        val signedIn = CompletableDeferred<Unit>()
        val error = CompletableDeferred<AuthError>()
        var calls = 0
        fun onSignedIn() { calls++; signedIn.complete(Unit) }
        fun onError(e: AuthError) { calls++; error.complete(e) }
    }

    private val crashes = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> crashes += e })

    private fun receiver() = LoopbackCallback(port = 0)

    /** The browser coming back: a GET to the receiver at the redirect address; its HTTP status. */
    private suspend fun visit(redirectUrl: String, query: String): Int = withContext(Dispatchers.IO) {
        val url = URI("$redirectUrl?$query").toURL()
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 2_000
            readTimeout = 5_000
            responseCode.also { disconnect() }
        }
    }

    private fun flow(auth: BrowserAuth, receiver: LoopbackCallback = receiver(), timeout: kotlin.time.Duration = 5.seconds) =
        DesktopGoogleSignIn(auth, receiver, timeout)

    private fun supabaseAuth() = io.github.jan.supabase.createSupabaseClient("https://test.supabase.co", "test-key") {
        install(io.github.jan.supabase.auth.Auth) {
            sessionManager = io.github.jan.supabase.auth.MemorySessionManager()
            codeVerifierCache = io.github.jan.supabase.auth.MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            alwaysAutoRefresh = false
            enableLifecycleCallbacks = false
        }
    }.auth

    // N-4: the deletion proof asks Google for the account choice, with the phone's account offered first
    @Test
    fun theDeletionProofOffersTheAccountAndAlwaysAsksWhichOne() {
        val query = URI(SupabaseBrowserAuth(supabaseAuth(), accountHint = "anna@example.com").googleSignInUrl("http://127.0.0.1:47613/cb/0123")).rawQuery
        assertTrue("prompt=select_account" in query, query)
        assertTrue("login_hint=anna%40example.com" in query, query)

        // an ordinary sign-in has neither
        val plain = URI(SupabaseBrowserAuth(supabaseAuth()).googleSignInUrl("http://127.0.0.1:47613/cb/0123")).rawQuery
        assertTrue("login_hint" !in plain && "prompt" !in plain, plain)
    }

    @Test
    fun theCodeIsExchangedAndTheNewSessionIsASignIn() = runBlocking<Unit> {
        val auth = FakeBrowserAuth { redirect -> scope.async { visit(redirect, "code=abc") } }
        val outcome = Outcome()

        flow(auth).start(scope, outcome::onSignedIn, outcome::onError)

        withTimeout(5_000) { outcome.signedIn.await() }
        assertEquals(listOf("abc"), auth.codes)
        assertEquals(1, outcome.calls)
        assertTrue(crashes.isEmpty())
    }

    // F1: the library's receiver took access and refresh tokens straight from the query (the implicit flow).
    @Test
    fun tokensInTheQueryAreIgnored() = runBlocking<Unit> {
        val statuses = mutableListOf<Int>()
        val auth = FakeBrowserAuth { redirect ->
            scope.async {
                statuses += visit(redirect, "access_token=planted&refresh_token=planted&expires_in=3600&token_type=bearer")
                statuses += visit(redirect, "code=real")
            }
        }

        assertEquals(OperationResult.Success(Unit), flow(auth).signIn())
        assertEquals(listOf(404, 200), statuses)
        assertEquals(listOf("real"), auth.codes)
    }

    // F1: a refusal at Google ended only when the library's 3 minutes were up.
    @Test
    fun anErrorFromGoogleEndsAtOnce() = runBlocking<Unit> {
        val auth = FakeBrowserAuth { redirect -> scope.async { visit(redirect, "error=access_denied&error_description=cancelled") } }

        val result = withTimeout(5_000) { flow(auth, timeout = 60.seconds).signIn() }

        assertEquals(OperationResult.Error(AuthError.UNKNOWN), result)
        assertTrue(auth.codes.isEmpty())
    }

    @Test
    fun theReceiverListensOnTheLoopbackOnlyAndStopsAfterwards() = runBlocking<Unit> {
        val receiver = receiver()
        var port = 0
        val auth = FakeBrowserAuth { redirect ->
            port = URI(redirect).port
            // reachable on the loopback
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 2_000) }
            // not on the network: any other address of this machine is refused
            otherAddressOfThisMachine()?.let { address ->
                assertFailsWith<java.io.IOException> { Socket().use { it.connect(InetSocketAddress(address, port), 2_000) } }
            }
            scope.async { visit(redirect, "code=abc") }
        }

        flow(auth, receiver).signIn()

        assertEquals("127.0.0.1", receiver.listenedOn?.substringBefore(':'))
        assertFailsWith<ConnectException> { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 2_000) } }
    }

    @Test
    fun nothingInTimeIsAnErrorAndTheReceiverStops() = runBlocking<Unit> {
        var port = 0
        val auth = FakeBrowserAuth { redirect -> port = URI(redirect).port }

        assertEquals(OperationResult.Error(AuthError.UNKNOWN), flow(auth, timeout = 200.milliseconds).signIn())
        assertFailsWith<ConnectException> { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 2_000) } }
    }

    @Test
    fun aFailedExchangeIsShownAndNoNetworkIsANetworkError() = runBlocking<Unit> {
        val offline = FakeBrowserAuth(exchangeFails = HttpRequestException("offline", HttpRequestBuilder())) { redirect -> scope.async { visit(redirect, "code=abc") } }
        assertEquals(OperationResult.Error(AuthError.NETWORK), flow(offline).signIn())

        val refused = FakeBrowserAuth(exchangeFails = IllegalStateException("bad code")) { redirect -> scope.async { visit(redirect, "code=abc") } }
        assertEquals(OperationResult.Error(AuthError.UNKNOWN), flow(refused).signIn())
    }

    // F5: the saved session must be the one this exchange made, not an older one or another user's.
    @Test
    fun aSessionOtherThanTheExchangedOneIsNotASignIn() = runBlocking<Unit> {
        val auth = object : BrowserAuth {
            override fun googleSignInUrl(redirectUrl: String) = redirectUrl
            override suspend fun openBrowser(url: String) { scope.async { visit(url, "code=abc") } }
            override suspend fun exchangeCode(code: String) = SessionKey("user-2", "token-2")
            // the device still holds the earlier sign-in
            override fun currentSession() = SessionKey("user-1", "token-1")
        }

        assertEquals(OperationResult.Error(AuthError.UNKNOWN), flow(auth).signIn())
    }

    @Test
    fun aTakenPortOrNoBrowserDoesNotCrash() = runBlocking<Unit> {
        val outcome = Outcome()
        flow(FakeBrowserAuth { throw UnsupportedOperationException("no browser") }).start(scope, outcome::onSignedIn, outcome::onError)

        assertEquals(AuthError.UNKNOWN, withTimeout(5_000) { outcome.error.await() })
        assertTrue(crashes.isEmpty())
    }

    // Leaving the screen while the browser is open cancels the flow: nothing is reported, nothing crashes, the receiver stops.
    @Test
    fun leavingTheScreenCancelsQuietlyAndStopsTheReceiver() = runBlocking<Unit> {
        val waiting = CompletableDeferred<Int>()
        val auth = FakeBrowserAuth { redirect -> waiting.complete(URI(redirect).port) }
        val outcome = Outcome()
        flow(auth).start(scope, outcome::onSignedIn, outcome::onError)
        val port = withTimeout(5_000) { waiting.await() }

        scope.coroutineContext[Job]!!.cancelAndJoin()

        assertEquals(0, outcome.calls)
        assertTrue(crashes.isEmpty())
        assertFailsWith<ConnectException> { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 2_000) } }
    }

    @Test
    fun aSecondClickWhileTheBrowserIsOpenDoesNotOpenItAgain() = runBlocking<Unit> {
        val redirect = CompletableDeferred<String>()
        val auth = FakeBrowserAuth { redirect.complete(it) }
        val flow = flow(auth)
        val outcome = Outcome()
        flow.start(scope, outcome::onSignedIn, outcome::onError)
        val url = withTimeout(5_000) { redirect.await() }

        flow.start(scope, outcome::onSignedIn, outcome::onError)
        visit(url, "code=abc")

        withTimeout(5_000) { outcome.signedIn.await() }
        assertEquals(1, auth.opened)
        assertEquals(1, outcome.calls)
    }

    // ---- card 12: the redirect on 127.0.0.1 with a random path, and an exchange that outlives the wait ----

    // N4: the redirect said localhost while the receiver listens on 127.0.0.1 only (localhost may be ::1 first)
    @Test
    fun theRedirectIsTheLoopbackAddressWithARandomPathPerSignIn() = runBlocking<Unit> {
        val redirects = mutableListOf<String>()
        repeat(2) {
            val auth = FakeBrowserAuth { redirect -> redirects += redirect; scope.async { visit(redirect, "code=abc") } }
            assertEquals(OperationResult.Success(Unit), flow(auth).signIn())
        }

        redirects.forEach { redirect ->
            val uri = URI(redirect)
            assertEquals("http", uri.scheme)
            assertEquals("127.0.0.1", uri.host)
            assertTrue(Regex("/cb/[0-9a-f]{32}").matches(uri.path), "path of the redirect")
            assertEquals(null, uri.query)
        }
        // a new secret for every sign-in
        assertTrue(redirects[0] != redirects[1])
    }

    // N5: a page that knew the port alone could end the 3-minute wait with ?error= (or race a code in)
    @Test
    fun onlyTheRandomPathIsTakenEverythingElseIs404() = runBlocking<Unit> {
        val statuses = mutableListOf<Int>()
        val auth = FakeBrowserAuth { redirect ->
            val port = URI(redirect).port
            val base = "http://127.0.0.1:$port"
            scope.async {
                statuses += visit("$base/", "error=access_denied")
                statuses += visit("$base/", "code=planted")
                statuses += visit("$base/cb/${"0".repeat(32)}", "code=planted")
                statuses += visit("$base/cb", "error=access_denied")
                statuses += visit("$redirect/extra", "code=planted")
                statuses += visit(redirect, "code=real")
            }
        }

        assertEquals(OperationResult.Success(Unit), flow(auth).signIn())
        assertEquals(listOf(404, 404, 404, 404, 404, 200), statuses)
        assertEquals(listOf("real"), auth.codes)
    }

    // N6: the 3 minutes ran out while the code was being exchanged; the session was saved, but the screen showed an error
    @Test
    fun anExchangeThatFinishesAfterTheWaitIsStillASignIn() = runBlocking<Unit> {
        val auth = FakeBrowserAuth(exchangeTakes = 1.seconds) { redirect -> scope.async { visit(redirect, "code=abc") } }

        val result = withTimeout(10_000) { flow(auth, timeout = 300.milliseconds).signIn() }

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals(SessionKey("user-1", "new-token"), auth.session)
    }

    // ... and a failed exchange after the wait is still the exchange's error, not a timeout
    @Test
    fun anExchangeThatFailsAfterTheWaitShowsItsError() = runBlocking<Unit> {
        val auth = FakeBrowserAuth(exchangeTakes = 1.seconds, exchangeFails = HttpRequestException("offline", HttpRequestBuilder())) { redirect ->
            scope.async { visit(redirect, "code=abc") }
        }

        assertEquals(OperationResult.Error(AuthError.NETWORK), withTimeout(10_000) { flow(auth, timeout = 300.milliseconds).signIn() })
    }

    // N6: leaving the screen while the code is exchanged cut the exchange off half way
    @Test
    fun leavingTheScreenDuringTheExchangeLetsItFinishQuietly() = runBlocking<Unit> {
        // the browser is not the screen's: its request goes on when the screen goes
        val browser = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = FakeBrowserAuth(exchangeTakes = 1.seconds) { redirect -> browser.async { runCatching { visit(redirect, "code=abc") } } }
        val outcome = Outcome()
        flow(auth).start(scope, outcome::onSignedIn, outcome::onError)
        withTimeout(5_000) { auth.exchangeStarted.await() }

        withTimeout(10_000) { scope.coroutineContext[Job]!!.cancelAndJoin() }

        assertEquals(SessionKey("user-1", "new-token"), auth.session)
        assertEquals(0, outcome.calls)
        assertTrue(crashes.isEmpty())
        browser.coroutineContext[Job]!!.cancel()
    }

    // Card 12 review F2: the cancel of the deletion proof compared the sessions while a late exchange was still running
    @Test
    fun theSignInIsInFlightUntilALateExchangeHasFinished() = runBlocking<Unit> {
        val signIns = SignInsInFlight()
        val browser = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = FakeBrowserAuth(exchangeTakes = 1.seconds) { redirect -> browser.async { runCatching { visit(redirect, "code=abc") } } }
        DesktopGoogleSignIn(auth, receiver(), 5.seconds, signIns).start(scope, {}, {})
        withTimeout(5_000) { auth.exchangeStarted.await() }

        // the screen goes; nobody waits for the flow itself
        scope.coroutineContext[Job]!!.cancel()

        assertTrue(signIns.awaitNone(10.seconds))
        assertEquals(SessionKey("user-1", "new-token"), auth.session)
        browser.coroutineContext[Job]!!.cancel()
    }

    /** A non-loopback IPv4 address of this machine, or null when it has none (then that half is skipped). */
    private fun otherAddressOfThisMachine(): String? =
        NetworkInterface.networkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
}
