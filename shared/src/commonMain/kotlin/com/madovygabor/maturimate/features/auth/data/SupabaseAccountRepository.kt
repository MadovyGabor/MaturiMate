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

    override suspend fun uploadProfile(profile: UserProfile, firstName: String, lastName: String) = call {
        val userId = userId()
        postgrest.from("user_profile").upsert(
            ProfileRow(
                userId = userId,
                firstName = firstName,
                lastName = lastName,
                teachingLanguage = profile.teachingLanguage?.name,
                graduationDate = profile.graduationDate?.toString(),
                onboardingCompletedAt = null,
            )
        )
        postgrest.from("user_settings").upsert(
            SettingsRow(
                userId = userId,
                uiLanguage = profile.uiLanguage.name,
                timezone = profile.timezone,
                dailyGoalMinutes = profile.dailyGoalMinutes,
                reminderEnabled = profile.reminderEnabled,
                reminderTime = profile.reminderTime?.toString(),
            )
        )
        // the onboarding roles are replaced; roles the server gives (curator, admin) stay
        postgrest.from("user_role").delete {
            filter {
                eq("user_id", userId)
                isIn("role_id", UserRole.entries.map { it.id })
            }
        }
        profile.role?.let { postgrest.from("user_role").insert(RoleRow(userId, it.id)) }
        postgrest.from("user_subject").delete { filter { eq("user_id", userId) } }
        if (profile.subjects.isNotEmpty()) {
            postgrest.from("user_subject").insert(profile.subjects.map { it.toRow(userId) })
        }
        Unit
    }

    override suspend fun completeOnboarding(finishedAt: Instant) = call {
        val userId = userId()
        postgrest.from("user_profile").update(mapOf("onboarding_completed_at" to finishedAt.toString())) {
            filter { eq("user_id", userId) }
        }
        Unit
    }

    override suspend fun recordConsents(consents: Set<ConsentType>) = call {
        val userId = userId()
        postgrest.from("user_consent").insert(consents.map { ConsentRow(userId, it.name, DOCUMENT_VERSION) })
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
            profile = UserProfile(
                teachingLanguage = profile.teachingLanguage?.let { TeachingLanguage.valueOf(it) },
                role = roles.firstNotNullOfOrNull { UserRole.fromId(it.roleId) },
                graduationDate = profile.graduationDate?.let { LocalDate.parse(it) },
                uiLanguage = settings?.uiLanguage?.let { TeachingLanguage.valueOf(it) } ?: TeachingLanguage.SK,
                dailyGoalMinutes = settings?.dailyGoalMinutes,
                timezone = settings?.timezone ?: "Europe/Bratislava",
                onboardingCompletedAt = profile.onboardingCompletedAt?.let { Instant.parse(it) },
                subjects = subjects.map { UserSubject(it.subjectId, it.source == "AUTO", it.level?.let { level -> SubjectLevel.valueOf(level) }) },
                reminderEnabled = settings?.reminderEnabled ?: false,
                // Postgres returns "18:00:00"
                reminderTime = settings?.reminderTime?.let { LocalTime.parse(it) },
            ),
        )
    }

    private suspend fun <T> call(block: suspend () -> T): OperationResult<T, AuthError> =
        try {
            OperationResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestException) {
            OperationResult.Error(AuthError.NETWORK)
        } catch (e: RestException) {
            OperationResult.Error(AuthError.UNKNOWN)
        } catch (e: NotSignedIn) {
            OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        }

    private class NotSignedIn : Exception()

    private fun UserSubject.toRow(userId: String) =
        SubjectRow(userId, subjectId, if (compulsory) "AUTO" else "CHOSEN", level?.name)

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

@Serializable
private data class ConsentRow(
    @SerialName("user_id") val userId: String,
    @SerialName("consent_type") val consentType: String,
    @SerialName("document_version") val documentVersion: String,
)
