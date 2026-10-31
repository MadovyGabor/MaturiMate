package com.madovygabor.maturimate.features.appOnboarding.presentation.subjects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Track
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.level_unknown
import maturimate.composeapp.generated.resources.subject_anj
import maturimate.composeapp.generated.resources.subject_mat
import maturimate.composeapp.generated.resources.subjects_level_unknown_hint
import maturimate.composeapp.generated.resources.subjects_title_student
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SubjectsTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    /** Content with only the Hungarian subject installed, like the starter pack. */
    private val content = object : ContentRepository by unsupported() {
        override suspend fun subjects(): OperationResult<List<Subject>, LearningError> =
            OperationResult.Success(listOf(Subject(1, "HU_MJL", "Magyar", "Maďarčina", Track.HU, null)))
    }

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    private suspend fun SubjectsViewModel.loaded() = withTimeout(5_000) { state.first { it.items.isNotEmpty() } }

    @Test
    fun studentStartsWithTheCompulsorySubjectsLocked() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.HU)
        repository.saveRole(UserRole.STUDENT)

        val state = SubjectsViewModel(repository, content, SnackbarController()).loaded()

        assertEquals(CatalogSubject.entries, state.items.map { it.subject })
        assertEquals(listOf(true, true, true, false), state.items.map { it.selected })
        assertEquals(listOf(true, true, true, false), state.items.map { it.locked })
        // only the Hungarian subject has content
        assertEquals(listOf(true, false, false, false), state.items.map { it.ready })
        // English is chosen, so the level is needed first
        assertFalse(state.canContinue)
    }

    @Test
    fun slovakSchoolHasNoHungarianSubject() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.SK)
        repository.saveRole(UserRole.STUDENT)

        val state = SubjectsViewModel(repository, content, SnackbarController()).loaded()

        assertEquals(listOf(CatalogSubject.SLOVAK, CatalogSubject.ENGLISH, CatalogSubject.MATHEMATICS), state.items.map { it.subject })
    }

    @Test
    fun teacherPicksFreelyAndUnknownLevelIsSavedAsB1() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.HU)
        repository.saveRole(UserRole.TEACHER)
        val viewModel = SubjectsViewModel(repository, content, SnackbarController())

        val loaded = viewModel.loaded()
        assertTrue(loaded.items.none { it.selected || it.locked })

        viewModel.onToggled(CatalogSubject.ENGLISH)
        viewModel.onToggled(CatalogSubject.MATHEMATICS)
        viewModel.onLevelSelected(LevelChoice.UNKNOWN)
        val done = CompletableDeferred<UserRole?>()
        viewModel.onNext { done.complete(it) }

        assertEquals(UserRole.TEACHER, withTimeout(5_000) { done.await() })
        assertEquals(
            listOf(UserSubject(3, compulsory = false, level = SubjectLevel.B1), UserSubject(4, compulsory = false)),
            repository.profile.first()!!.subjects,
        )
    }

    @Test
    fun lockedSubjectCannotBeRemoved() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.SK)
        repository.saveRole(UserRole.STUDENT)
        val viewModel = SubjectsViewModel(repository, content, SnackbarController())
        viewModel.loaded()

        viewModel.onToggled(CatalogSubject.SLOVAK)

        assertTrue(viewModel.state.value.items.first { it.subject == CatalogSubject.SLOVAK }.selected)
    }

    @Test
    fun savedSubjectsAndLevelAreShownAgain() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.HU)
        repository.saveRole(UserRole.LEARNER)
        repository.saveSubjects(listOf(UserSubject(3, compulsory = false, level = SubjectLevel.B2)))

        val state = SubjectsViewModel(repository, content, SnackbarController()).loaded()

        assertEquals(listOf(CatalogSubject.ENGLISH), state.items.filter { it.selected }.map { it.subject })
        assertEquals(LevelChoice.B2, state.level)
        assertTrue(state.canContinue)
    }

    @Test
    fun levelChoiceAppearsWithEnglishAndUnlocksNext() = runComposeUiTest {
        var state by mutableStateOf(
            SubjectsUiState(
                role = UserRole.LEARNER,
                items = CatalogSubject.forLanguage(TeachingLanguage.HU).map { SubjectItem(it, false, false, true) },
            )
        )
        setContent {
            AppTheme(darkTheme = false) {
                SubjectsContent(
                    state = state,
                    onToggled = { subject ->
                        state = state.copy(items = state.items.map { if (it.subject == subject) it.copy(selected = !it.selected) else it })
                    },
                    onLevelSelected = { state = state.copy(level = it) },
                    onBack = {}, onNext = {},
                )
            }
        }

        onNodeWithText(text(Res.string.common_next)).assertIsNotEnabled()
        onNodeWithText(text(Res.string.subject_anj)).performClick()
        onNodeWithText(text(Res.string.common_next)).assertIsNotEnabled()
        onNodeWithText(text(Res.string.level_unknown)).performClick()
        onNodeWithText(text(Res.string.subjects_level_unknown_hint)).assertExists()
        onNodeWithText(text(Res.string.common_next)).assertIsEnabled()
        onNodeWithText(text(Res.string.subject_mat)).performClick()
        onNodeWithText(text(Res.string.common_next)).assertIsEnabled()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/subjects. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/subjects").apply { mkdirs() }
        val state = SubjectsUiState(
            role = UserRole.STUDENT,
            items = CatalogSubject.forLanguage(TeachingLanguage.HU).map {
                SubjectItem(it, it.compulsory, it.compulsory, it == CatalogSubject.HUNGARIAN)
            },
            level = LevelChoice.UNKNOWN,
        )
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent {
                        AppTheme(darkTheme = dark) { SubjectsContent(state, {}, {}, {}, {}) }
                    }
                    onNodeWithText(text(Res.string.subjects_title_student)).assertExists()
                    val file = File(dir, "subjects-$language-$name-${if (dark) "dark" else "light"}.png")
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                }
            }
        }
    }
}

/** A content repository whose calls fail, for tests that need only one of them. */
private fun unsupported(): ContentRepository =
    java.lang.reflect.Proxy.newProxyInstance(
        ContentRepository::class.java.classLoader,
        arrayOf(ContentRepository::class.java),
    ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as ContentRepository
