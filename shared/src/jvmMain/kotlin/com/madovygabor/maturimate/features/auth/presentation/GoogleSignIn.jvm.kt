package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.network.DESKTOP_AUTH_CALLBACK_PORT
import com.madovygabor.maturimate.core.network.DESKTOP_AUTH_EXCHANGE_LIMIT
import com.madovygabor.maturimate.core.network.DESKTOP_AUTH_TIMEOUT
import com.madovygabor.maturimate.core.network.signInDonePage
import com.madovygabor.maturimate.core.network.signInFailedPage
import com.madovygabor.maturimate.core.network.warmUpDesktopAuthPages
import com.madovygabor.maturimate.core.presentation.locale.LocalAppLocale
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.SignInsInFlight
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform.getKoin
import java.awt.Desktop
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop: compose-auth has no native Google flow here, and its fallback never reports a result. The library's own
 * browser flow (`signInWith(Google)`) is not used either: its callback server listens on every network interface and
 * also takes tokens straight from the query (the implicit flow), so anyone who reaches the port could plant a session.
 * The app runs the PKCE flow with its own receiver instead ([LoopbackCallback]).
 */
@Composable
actual fun rememberGoogleSignIn(onSignedIn: () -> Unit, onError: (AuthError) -> Unit, onClosed: () -> Unit, accountHint: String?): () -> Unit {
    val client = koinInject<SupabaseClient>()
    val scope = rememberCoroutineScope()
    val signedIn by rememberUpdatedState(onSignedIn)
    val failed by rememberUpdatedState(onError)
    val languageTag by rememberUpdatedState(LocalAppLocale.current)
    // the app's one tracker (AppModul), so a cancelled deletion proof can wait for a late exchange
    val signIns = remember { getKoin().getOrNull<SignInsInFlight>() ?: SignInsInFlight() }
    val flow = remember(client, accountHint) {
        DesktopGoogleSignIn(
            SupabaseBrowserAuth(client.auth, accountHint),
            LoopbackCallback(DESKTOP_AUTH_CALLBACK_PORT, language = { languageTag }),
            signIns = signIns,
        )
    }
    // leaving the screen cancels the wait (the scope ends) without a callback: the open flow then reports onClosed
    val open = rememberOpenGoogleFlow(onClosed)
    return {
        open.opened()
        flow.start(
            scope,
            onSignedIn = { open.answered(); signedIn() },
            onError = { open.answered(); failed(it) },
        )
    }
}

/** Who is signed in: compared to tell a new sign-in from the session there was before. Never shown or logged. */
internal data class SessionKey(val userId: String, val accessToken: String) {
    override fun toString() = "SessionKey(…)"
}

/** The part of Supabase Auth the desktop Google sign-in needs; a fake in the tests. */
internal interface BrowserAuth {
    /** The Google sign-in address that returns to [redirectUrl]; it keeps the PKCE verifier for [exchangeCode]. */
    fun googleSignInUrl(redirectUrl: String): String

    /** Opens [url] in the default browser. */
    suspend fun openBrowser(url: String)

    /** Exchanges the PKCE [code] for a session and saves it; the key of that session. */
    suspend fun exchangeCode(code: String): SessionKey

    /** The session saved now, or null. */
    fun currentSession(): SessionKey?
}

/** [BrowserAuth] over Supabase Auth (PKCE: `getOAuthUrl` saves the verifier, `exchangeCodeForSession` uses it). */
internal class SupabaseBrowserAuth(private val auth: Auth, private val accountHint: String? = null) : BrowserAuth {
    override fun googleSignInUrl(redirectUrl: String) = auth.getOAuthUrl(Google, redirectUrl) {
        // the deletion proof: Google shows the account choice every time, with this account offered first (GoTrue passes
        // unknown query parameters on to Google)
        accountHint?.let {
            queryParams["prompt"] = "select_account"
            queryParams["login_hint"] = it
        }
    }

    override suspend fun openBrowser(url: String) = withContext(Dispatchers.IO) { Desktop.getDesktop().browse(URI(url)) }

    override suspend fun exchangeCode(code: String): SessionKey =
        auth.exchangeCodeForSession(code).let { SessionKey(it.user?.id.orEmpty(), it.accessToken) }

    override fun currentSession(): SessionKey? = auth.currentSessionOrNull()?.let { SessionKey(it.user?.id.orEmpty(), it.accessToken) }
}

/** How a wait for the browser ended. */
internal sealed interface CallbackOutcome {
    /** The code came and was exchanged: [session] is the new sign-in. */
    data class SignedIn(val session: SessionKey) : CallbackOutcome

    /** Google or Supabase sent `?error=` (cancelled or refused). */
    data object Refused : CallbackOutcome

    /** The code came, but the exchange failed. */
    data class Failed(val cause: Exception) : CallbackOutcome

    /** Nothing came in time. */
    data object TimedOut : CallbackOutcome
}

/**
 * The receiver of the browser's redirect: a small server on 127.0.0.1 only (never the network), for one sign-in. The
 * redirect address is `http://127.0.0.1:<port>/cb/<secret>`, with a new random secret (128 bits) for every sign-in: only
 * that exact path is taken, everything else gets 404, so a page that knows the port alone can neither end the wait nor
 * race a code in. On that path it takes only `?code=` (exchanged before the page is answered, so the page tells the
 * truth) and `?error=` (ends at once); anything else, such as tokens in the query, is ignored. The secret is never logged.
 *
 * Once a code was taken, its exchange runs to its end (at most [exchangeLimit]) even when the wait runs out or the
 * screen goes away meanwhile: cutting it off half way could leave a session made on the server but not saved here, and
 * a sign-in that went through shown as an error. Only a wait that ends without any callback is a timeout.
 * The server stops in every case: success, error, timeout and cancellation. [port] 0 picks a free one (tests).
 */
internal class LoopbackCallback(
    private val port: Int,
    private val host: String = LOOPBACK,
    private val exchangeLimit: Duration = DESKTOP_AUTH_EXCHANGE_LIMIT,
    private val language: () -> String = { java.util.Locale.getDefault().toLanguageTag() },
) {

    /** The address the server listened on in the last [receive], as `host:port`; for the tests. */
    @Volatile
    var listenedOn: String? = null
        private set

    /** Starts the server, calls [open] with the redirect address, and waits up to [timeout] for the browser. */
    suspend fun receive(timeout: Duration, open: suspend (redirectUrl: String) -> Unit, exchange: suspend (code: String) -> SessionKey): CallbackOutcome {
        val outcome = CompletableDeferred<CallbackOutcome>()
        val taken = AtomicBoolean(false)
        // set when a code was taken: from then on its exchange decides the outcome, not the clock
        val exchanging = AtomicBoolean(false)
        // set when the wait is over: a code that comes later is not exchanged (each side sets its own flag first and then
        // reads the other's, so either the exchange is not started or the closing waits for it)
        val closing = AtomicBoolean(false)
        val path = "/cb/" + newSecret()
        val server = embeddedServer(CIO, host = host, port = port) {
            routing {
                get("{...}") {
                    val parameters = call.request.queryParameters
                    val code = parameters["code"]
                    when {
                        // any other path (the root, a guessed secret, the favicon): as if nothing were here
                        !sameText(call.request.path(), path) -> call.respond(HttpStatusCode.NotFound)
                        parameters["error"] != null && taken.compareAndSet(false, true) -> {
                            call.respondText(signInFailedPage(language()), ContentType.Text.Html.withCharset(Charsets.UTF_8))
                            outcome.complete(CallbackOutcome.Refused)
                        }
                        code != null && taken.compareAndSet(false, true) -> {
                            exchanging.set(true)
                            if (closing.get()) {
                                outcome.complete(CallbackOutcome.TimedOut)
                                call.respondText(signInFailedPage(language()), ContentType.Text.Html.withCharset(Charsets.UTF_8))
                                return@get
                            }
                            // bounded, and not cut off by the end of the wait or of the screen (the server stops only after it)
                            val result = withContext(NonCancellable) {
                                try {
                                    withTimeoutOrNull(exchangeLimit) { CallbackOutcome.SignedIn(exchange(code)) }
                                        ?: CallbackOutcome.Failed(IllegalStateException("the exchange took too long"))
                                } catch (e: CancellationException) {
                                    CallbackOutcome.Failed(IllegalStateException("the exchange was cancelled"))
                                } catch (e: Exception) {
                                    CallbackOutcome.Failed(e)
                                }
                            }
                            // the outcome first: the page below can fail (the browser went away) without losing it
                            outcome.complete(result)
                            val page = if (result is CallbackOutcome.SignedIn) signInDonePage(language()) else signInFailedPage(language())
                            call.respondText(page, ContentType.Text.Html.withCharset(Charsets.UTF_8))
                        }
                        // tokens in the query (the implicit flow), a second code: not taken
                        else -> call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
        // the pages embed the app's fonts and logo: read them now, not after the outcome (the server stops soon after it)
        withContext(Dispatchers.IO) { warmUpDesktopAuthPages() }
        server.startSuspend(wait = false)
        try {
            val connector = server.engine.resolvedConnectors().first()
            listenedOn = "${connector.host}:${connector.port}"
            // the address Supabase allows as the redirect (llm/supabase-setup.md: http://127.0.0.1:47613/**): the same
            // loopback address the server listens on (localhost may be ::1 first)
            open("http://$LOOPBACK:${connector.port}$path")
            withTimeoutOrNull(timeout) { outcome.await() }?.let { return it }
            // the time ran out: a code taken by then is still being exchanged, and that exchange decides
            if (!exchanging.get()) return CallbackOutcome.TimedOut
            return withTimeoutOrNull(exchangeLimit + PAGE_MARGIN) { outcome.await() } ?: CallbackOutcome.TimedOut
        } finally {
            withContext(NonCancellable) {
                closing.set(true)
                // the screen went away during the exchange: it runs to its end (the session is saved), nothing is reported
                if (exchanging.get()) withTimeoutOrNull(exchangeLimit + PAGE_MARGIN) { outcome.await() }
                // the outcome is set right before the page is answered; the short grace lets the page reach the browser
                server.stopSuspend(gracePeriodMillis = 200, timeoutMillis = 1_000)
            }
        }
    }

    companion object {
        const val LOOPBACK = "127.0.0.1"

        /** Time for the page after the exchange. */
        private val PAGE_MARGIN = 5.seconds

        private val random = SecureRandom()

        /** 128 random bits as 32 hex digits: the path secret of one sign-in. */
        private fun newSecret(): String {
            val bytes = ByteArray(16).also { random.nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** A comparison whose time does not tell how much of the secret was right. */
        private fun sameText(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}

/**
 * The desktop Google sign-in: the browser goes to Google, comes back to [callback] with a PKCE code, and the code is
 * exchanged for the session. [start] reports the outcome once, on the caller's scope; leaving the screen cancels the
 * flow (the receiver stops) and reports nothing.
 */
internal class DesktopGoogleSignIn(
    private val auth: BrowserAuth,
    private val callback: LoopbackCallback,
    private val timeout: Duration = DESKTOP_AUTH_TIMEOUT,
    /** Where this sign-in counts as running, from the start of the wait until the receiver has stopped. */
    private val signIns: SignInsInFlight = SignInsInFlight(),
) {
    private var running: Job? = null

    /** Starts the flow unless one is waiting for the browser already (a second click). */
    fun start(scope: CoroutineScope, onSignedIn: () -> Unit, onError: (AuthError) -> Unit) {
        if (running?.isActive == true) return
        running = scope.launch {
            when (val result = signIn()) {
                is OperationResult.Success -> onSignedIn()
                is OperationResult.Error -> onError(result.error)
            }
        }
    }

    suspend fun signIn(): OperationResult<Unit, AuthError> = signIns.track { signInOnce() }

    private suspend fun signInOnce(): OperationResult<Unit, AuthError> = try {
        val outcome = callback.receive(timeout, open = { auth.openBrowser(auth.googleSignInUrl(it)) }, exchange = auth::exchangeCode)
        when (outcome) {
            // a sign-in only when the saved session is the one this exchange made (not an older one, not another user's)
            is CallbackOutcome.SignedIn ->
                if (auth.currentSession() == outcome.session) OperationResult.Success(Unit) else OperationResult.Error(AuthError.UNKNOWN)
            is CallbackOutcome.Failed -> OperationResult.Error(outcome.cause.toAuthError())
            // cancelled at Google, or the browser left: the user should know the button works again
            CallbackOutcome.Refused, CallbackOutcome.TimedOut -> OperationResult.Error(AuthError.UNKNOWN)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // e.g. the callback port is taken, or no browser can be opened
        OperationResult.Error(e.toAuthError())
    }

    private fun Exception.toAuthError() = if (this is HttpRequestException) AuthError.NETWORK else AuthError.UNKNOWN
}
