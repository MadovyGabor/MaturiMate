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
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
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

    override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> = call {
        val userId = userId()
        val profile = postgrest.from("user_profile").select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<ProfileRow>() ?: return@call null
        val settings = postgrest.from("user_settings").select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<SettingsRow>()
        val roles = postgrest.from("user_role").select { filter { eq("user_id", userId) } }.decodeList<RoleRow>()
        val subjects = postgrest.from("user_subject").select { filter { eq("user_id", userId) } }.decodeList<SubjectRow>()
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
        )
    }

    /** Runs the requests; every failure becomes an [AuthError], so a bad answer cannot crash the app. */
    private suspend fun <T> call(block: suspend () -> T): OperationResult<T, AuthError> =
        try {
            OperationResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestException) {
            OperationResult.Error(AuthError.NETWORK)
        } catch (e: NotSignedIn) {
            OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        } catch (e: Exception) {
            // RestException, and rows this version cannot decode
            OperationResult.Error(AuthError.UNKNOWN)
        }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun <T> parseOrNull(value: String?, parse: (String) -> T): T? =
        value?.let { runCatching { parse(it) }.getOrNull() }

    private class NotSignedIn : Exception()

    companion object {
        /** Version of the terms and the privacy notice the consents refer to. */
        const val DOCUMENT_VERSION = "1.0"
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
