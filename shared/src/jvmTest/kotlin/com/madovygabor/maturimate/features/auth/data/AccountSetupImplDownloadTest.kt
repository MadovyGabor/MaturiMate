package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountSetupImplDownloadTest : AccountSetupImplTestBase() {

    @Test
    fun downloadReadsTheAccountsRows() = runBlocking {
        val repository = remote { table ->
            when (table) {
                "user_profile" -> """[{"user_id":"$USER_ID","first_name":"Anna","last_name":"Kovács","teaching_language":"SK","graduation_date":"2027-03-15","onboarding_completed_at":"2026-10-31T10:00:00Z"}]"""
                "user_settings" -> """[{"user_id":"$USER_ID","ui_language":"SK","timezone":"Europe/Bratislava","daily_goal_minutes":15,"reminder_enabled":true,"reminder_time":"07:30:00"}]"""
                "user_role" -> """[{"user_id":"$USER_ID","role_id":1}]"""
                "user_subject" -> """[{"user_id":"$USER_ID","subject_id":2,"source":"AUTO","level":null}]"""
                else -> "[]"
            }
        }

        val remote = (repository.downloadProfile() as OperationResult.Success).data!!

        assertEquals("Anna", remote.firstName)
        assertEquals(TeachingLanguage.SK, remote.profile.teachingLanguage)
        assertEquals(UserRole.STUDENT, remote.profile.role)
        assertEquals(15, remote.profile.dailyGoalMinutes)
        assertEquals(LocalTime(7, 30), remote.profile.reminderTime)
        assertEquals(listOf(UserSubject(2, true)), remote.profile.subjects)
        assertEquals(Instant.parse("2026-10-31T10:00:00Z"), remote.profile.onboardingCompletedAt)
    }

    @Test
    fun unknownValuesFromTheServerAreLeftOut() = runBlocking {
        val repository = remote { table ->
            when (table) {
                "user_profile" -> """[{"user_id":"$USER_ID","first_name":"Anna","last_name":"Kovács","teaching_language":"DE","graduation_date":"not a date"}]"""
                "user_settings" -> """[{"user_id":"$USER_ID","ui_language":"DE","timezone":"Europe/Bratislava","reminder_enabled":false,"reminder_time":"later"}]"""
                "user_subject" -> """[{"user_id":"$USER_ID","subject_id":3,"source":"CHOSEN","level":"C1"}]"""
                else -> "[]"
            }
        }

        val remote = (repository.downloadProfile() as OperationResult.Success).data!!

        assertEquals(null, remote.profile.teachingLanguage)
        assertEquals(null, remote.profile.graduationDate)
        assertEquals(TeachingLanguage.SK, remote.profile.uiLanguage)
        assertEquals(null, remote.profile.reminderTime)
        assertEquals(listOf(UserSubject(3, false, level = null)), remote.profile.subjects)
    }

    @Test
    fun anAnswerThatCannotBeReadIsAnErrorNotACrash() = runBlocking {
        val repository = remote { table -> if (table == "user_profile") """{"unexpected": true}""" else "[]" }

        assertEquals(OperationResult.Error(AuthError.UNKNOWN), repository.downloadProfile())
    }

    @Test
    fun accountWithoutAProfileDownloadsNothing() = runBlocking {
        assertEquals(OperationResult.Success(null), remote().downloadProfile())
    }

    @Test
    fun theDownloadReadsWhetherTheTermsWereAccepted() = runBlocking {
        val repository = remote { table ->
            when (table) {
                "user_profile" -> """[{"user_id":"$USER_ID","first_name":"Anna","last_name":"Kovács","onboarding_completed_at":"2026-10-31T10:00:00Z"}]"""
                "user_consent" -> """[{"consent_type":"TERMS"}]"""
                else -> "[]"
            }
        }

        val remote = (repository.downloadProfile() as OperationResult.Success).data!!

        assertTrue(remote.acceptedTerms)
        assertTrue(remote.isSetUp)
        assertTrue(requests.any { it.first == "GET user_consent" }, requests.toString())
    }
}
