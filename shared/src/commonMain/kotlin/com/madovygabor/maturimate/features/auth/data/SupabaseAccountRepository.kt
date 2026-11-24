package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.RemoteProfile
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [AccountRepository] over Postgrest. Row level security lets an account read and write only
 * its own rows (supabase/migrations), so the user id comes from the signed-in session.
 */
@OptIn(ExperimentalTime::class)
class SupabaseAccountRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
) : AccountRepository {

    private fun userId(): String = auth.currentUserOrNull()?.id ?: throw NotSignedIn()

    override suspend fun saveSignUp(
        profile: UserProfile,
        firstName: String,
        lastName: String,
        consents: Set<ConsentType>,
        finishedAt: Instant,
    ) = call {
        // the server takes the user from the session (auth.uid()); this only checks there is one
        userId()
        postgrest.rpc(
            "save_sign_up",
            SignUpParams(
                firstName = firstName,
                lastName = lastName,
                teachingLanguage = profile.teachingLanguage?.name,
                graduationDate = profile.graduationDate?.toString(),
                uiLanguage = profile.uiLanguage.name,
                timezone = profile.timezone,
                dailyGoalMinutes = profile.dailyGoalMinutes,
                reminderEnabled = profile.reminderEnabled,
                reminderTime = profile.reminderTime?.toString(),
                roleId = profile.role?.id,
                subjects = profile.subjects.map { SubjectParam(it.subjectId, if (it.compulsory) "AUTO" else "CHOSEN", it.level?.name) },
                consents = consents.map { it.name },
                documentVersion = DOCUMENT_VERSION,
                onboardingCompletedAt = finishedAt.toString(),
            ),
        )
        Unit
    }

    override suspend fun saveNames(firstName: String, lastName: String) = call {
        val userId = userId()
        postgrest.from("user_profile").update(NamesRow(firstName, lastName)) { filter { eq("user_id", userId) } }
        Unit
    }

    override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> = call {
        val userId = userId()
        val profile = postgrest.from("user_profile").select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<ProfileRow>() ?: return@call null
        val settings = postgrest.from("user_settings").select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<SettingsRow>()
        val roles = postgrest.from("user_role").select { filter { eq("user_id", userId) } }.decodeList<RoleRow>()
        val subjects = postgrest.from("user_subject").select { filter { eq("user_id", userId) } }.decodeList<SubjectRow>()
        // an active TERMS consent: only save_sign_up stores it, so it tells a finished sign-up from a synced guest profile
        val terms = postgrest.from("user_consent").select(Columns.list("consent_type")) {
            filter {
                eq("user_id", userId)
                eq("consent_type", "TERMS")
                exact("withdrawn_at", null)
            }
        }.decodeList<ConsentRow>()
        RemoteProfile(
            firstName = profile.firstName,
            lastName = profile.lastName,
            // a value this app version does not know is left out instead of failing the sign-in
            profile = UserProfile(
                teachingLanguage = enumOrNull<TeachingLanguage>(profile.teachingLanguage),
                role = roles.firstNotNullOfOrNull { UserRole.fromId(it.roleId) },
                graduationDate = parseOrNull(profile.graduationDate, LocalDate::parse),
                uiLanguage = enumOrNull<TeachingLanguage>(settings?.uiLanguage) ?: TeachingLanguage.SK,
                dailyGoalMinutes = settings?.dailyGoalMinutes,
                timezone = settings?.timezone ?: "Europe/Bratislava",
                onboardingCompletedAt = parseOrNull(profile.onboardingCompletedAt, Instant::parse),
                subjects = subjects.map { UserSubject(it.subjectId, it.source == "AUTO", enumOrNull<SubjectLevel>(it.level)) },
                reminderEnabled = settings?.reminderEnabled ?: false,
                // Postgres returns "18:00:00"
                reminderTime = parseOrNull(settings?.reminderTime, LocalTime::parse),
            ),
            acceptedTerms = terms.isNotEmpty(),
        )
    }

    override suspend fun deleteAccount(accessToken: String) = call {
        userId()
        // A plain POST to the function, not postgrest.rpc: that always sends the phone's session, and the server wants the
        // proof's own fresh one (a sign-in of the last 5 minutes in its token, migration 20261128120000). The plain client
        // adds the project's API key; the token is set here and never logged.
        val client = auth.supabaseClient
        val response = client.httpClient.request("${client.supabaseHttpUrl}/rest/v1/rpc/delete_my_account") {
            method = HttpMethod.Post
            bearerAuth(accessToken)
            setBody(TextContent("{}", ContentType.Application.Json))
        }
        if (!response.status.isSuccess()) {
            val message = runCatching {
                (Json.parseToJsonElement(response.bodyAsText()).jsonObject["message"] as? JsonPrimitive)?.contentOrNull
            }.getOrNull()
            throw DeletionRefused(response.status.value, message)
        }
        Unit
    }

    override suspend fun exportMyData() = call {
        userId()
        postgrest.rpc("export_my_data").data
    }

    /** Runs the requests; every failure becomes an [AuthError], so a bad answer cannot crash the app. */
    private suspend fun <T> call(block: suspend () -> T): OperationResult<T, AuthError> =
        try {
            OperationResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestException) {
            OperationResult.Error(AuthError.NETWORK)
        } catch (e: DeletionRefused) {
            OperationResult.Error(
                when {
                    // the server's own check of the proof: no sign-in of the last minutes in the token
                    e.serverMessage == RECENT_LOGIN_REQUIRED -> AuthError.PROOF_NEEDED
                    e.status == 401 -> AuthError.SESSION_EXPIRED
                    else -> AuthError.UNKNOWN
                }
            )
        } catch (e: NotSignedIn) {
            // no session on this phone (nothing was typed, so never "wrong password"): the user has to sign in again
            OperationResult.Error(AuthError.SESSION_EXPIRED)
        } catch (e: PostgrestRestException) {
            OperationResult.Error(
                when {
                    // the guard of save_sign_up (migration 20261127120000): the account is set up already, nothing written
                    e.code == ACCOUNT_SET_UP_CODE || (e.statusCode == 409 && e.error == ACCOUNT_SET_UP_MESSAGE) -> AuthError.ACCOUNT_ALREADY_SET_UP
                    e.statusCode == 401 -> AuthError.SESSION_EXPIRED
                    else -> AuthError.UNKNOWN
                }
            )
        } catch (e: RestException) {
            // 401: the session's token is expired or revoked; the user has to sign in again
            OperationResult.Error(if (e.statusCode == 401) AuthError.SESSION_EXPIRED else AuthError.UNKNOWN)
        } catch (e: Exception) {
            // rows this version cannot decode
            OperationResult.Error(AuthError.UNKNOWN)
        }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun <T> parseOrNull(value: String?, parse: (String) -> T): T? =
        value?.let { runCatching { parse(it) }.getOrNull() }

    private class NotSignedIn : Exception()

    /** The server refused the deletion ([status], PostgREST's message); carries no token. */
    private class DeletionRefused(val status: Int, val serverMessage: String?) : Exception()

    companion object {
        /** Version of the terms and the privacy notice the consents refer to. */
        const val DOCUMENT_VERSION = "1.0"

        /** The SQLSTATE and the message `save_sign_up` refuses a set-up account with (PostgREST answers 409). */
        private const val ACCOUNT_SET_UP_CODE = "PT409"
        private const val ACCOUNT_SET_UP_MESSAGE = "account_already_set_up"

        /** The message `delete_my_account` refuses a token without a recent sign-in with (migration 20261128120000). */
        private const val RECENT_LOGIN_REQUIRED = "recent_login_required"
    }
}

@Serializable
private data class ProfileRow(
    @SerialName("user_id") val userId: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("teaching_language") val teachingLanguage: String? = null,
    @SerialName("graduation_date") val graduationDate: String? = null,
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
)

@Serializable
private data class SettingsRow(
    @SerialName("user_id") val userId: String,
    @SerialName("ui_language") val uiLanguage: String,
    val timezone: String,
    @SerialName("daily_goal_minutes") val dailyGoalMinutes: Int? = null,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean = false,
    @SerialName("reminder_time") val reminderTime: String? = null,
)

@Serializable
private data class ConsentRow(
    @SerialName("consent_type") val consentType: String,
)

@Serializable
private data class RoleRow(
    @SerialName("user_id") val userId: String,
    @SerialName("role_id") val roleId: Long,
)

@Serializable
private data class SubjectRow(
    @SerialName("user_id") val userId: String,
    @SerialName("subject_id") val subjectId: Long,
    val source: String,
    val level: String? = null,
)

/** The parameters of the `save_sign_up` function (supabase/migrations). */
@Serializable
private data class SignUpParams(
    @SerialName("p_first_name") val firstName: String,
    @SerialName("p_last_name") val lastName: String,
    @SerialName("p_teaching_language") val teachingLanguage: String?,
    @SerialName("p_graduation_date") val graduationDate: String?,
    @SerialName("p_ui_language") val uiLanguage: String,
    @SerialName("p_timezone") val timezone: String,
    @SerialName("p_daily_goal_minutes") val dailyGoalMinutes: Int?,
    @SerialName("p_reminder_enabled") val reminderEnabled: Boolean,
    @SerialName("p_reminder_time") val reminderTime: String?,
    @SerialName("p_role_id") val roleId: Long?,
    @SerialName("p_subjects") val subjects: List<SubjectParam>,
    @SerialName("p_consents") val consents: List<String>,
    @SerialName("p_document_version") val documentVersion: String,
    @SerialName("p_onboarding_completed_at") val onboardingCompletedAt: String,
)

@Serializable
private data class SubjectParam(
    @SerialName("subject_id") val subjectId: Long,
    val source: String,
    val level: String?,
)

/** The names of `user_profile`, for a change from the settings. */
@Serializable
private data class NamesRow(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
)
