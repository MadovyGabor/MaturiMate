package com.madovygabor.maturimate.features.userProfile.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** [ExamDateRepository] over the phone's copy of the server table `exam_date` (filled by the sync). */
class ExamDateRepositoryImpl(
    private val database: UserDatabase,
    private val dispatcher: CoroutineDispatcher,
) : ExamDateRepository {

    private val q get() = database.examDateQueries

    override val dates: Flow<List<OfficialExamDate>> = q.selectAll().asFlow().mapToList(dispatcher).map { rows ->
        rows.mapNotNull { row ->
            // a date this version cannot read is left out
            runCatching { LocalDate.parse(row.exam_on) }.getOrNull()?.let {
                OfficialExamDate(row.exam_date_id, row.exam_year.toInt(), row.subject_id, it, row.published_at)
            }
        }
    }

    override suspend fun isAnswered(date: OfficialExamDate): Boolean = withContext(dispatcher) {
        q.selectAnswer(date.id).executeAsOneOrNull()?.let { it >= date.publishedAt } ?: false
    }

    override suspend fun markAnswered(date: OfficialExamDate) {
        withContext(dispatcher) { q.saveAnswer(date.id, date.publishedAt) }
    }
}
