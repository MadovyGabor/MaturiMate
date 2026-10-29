package com.madovygabor.maturimate.features.learning.data.mapper

import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.SelectById
import com.madovygabor.maturimate.database.content.SelectUnitTasks
import com.madovygabor.maturimate.database.content.Task_accepted_answer
import com.madovygabor.maturimate.database.content.Task_attachment
import com.madovygabor.maturimate.database.content.Task_key
import com.madovygabor.maturimate.database.content.Task_option
import com.madovygabor.maturimate.features.learning.domain.model.AcceptedAnswer
import com.madovygabor.maturimate.features.learning.domain.model.Attachment
import com.madovygabor.maturimate.features.learning.domain.model.KeySlot
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.MediaType
import com.madovygabor.maturimate.features.learning.domain.model.OptionRole
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import com.madovygabor.maturimate.features.learning.domain.model.TaskOption
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.Track
import com.madovygabor.maturimate.features.learning.domain.model.UnitTask
import com.madovygabor.maturimate.database.content.Subject as SubjectRow
import com.madovygabor.maturimate.database.content.Topic as TopicRow

// Maps rows of the content database (generated SQLDelight classes) to domain models.
// The values were checked by the CHECK constraints and the content validator, so unknown codes are a bug.

fun SubjectRow.toDomain() = Subject(
    id = subject_id,
    code = code,
    nameHu = name_hu,
    nameSk = name_sk,
    track = Track.valueOf(track),
    description = description,
)

fun TopicRow.toDomain() = Topic(
    id = topic_id,
    subjectId = subject_id,
    parentTopicId = parent_topic_id,
    title = title,
    description = description,
    orderIndex = order_index.toInt(),
)

fun Learning_unit.toDomain(): LearningUnit = when (unit_type) {
    "SUMMARY" -> LearningUnit.Summary(
        id = learning_unit_id,
        topicId = topic_id,
        title = title,
        bodyText = requireNotNull(body_text) { "SUMMARY unit $learning_unit_id has no text" },
        estimatedMinutes = estimated_minutes.toInt(),
        xpReward = xp_reward.toInt(),
        sortOrder = sort_order.toInt(),
    )
    "PRACTICE" -> LearningUnit.Practice(
        id = learning_unit_id,
        topicId = topic_id,
        title = title,
        estimatedMinutes = estimated_minutes.toInt(),
        roundSize = round_size.toInt(),
        xpReward = xp_reward.toInt(),
        sortOrder = sort_order.toInt(),
    )
    else -> error("unknown unit_type $unit_type")
}

fun SelectUnitTasks.toDomain() = UnitTask(
    taskId = task_id,
    sortOrder = sort_order.toInt(),
    isCore = is_core,
    isActive = is_active,
)

/** Builds a task from its row and the rows it owns. Keys reference options by id; the domain uses labels. */
fun SelectById.toDomain(
    options: List<Task_option>,
    keys: List<Task_key>,
    acceptedAnswers: List<Task_accepted_answer>,
    attachments: List<Task_attachment>,
): Task {
    val labels = options.associate { it.option_id to it.option_label }
    return Task(
        id = task_id,
        topicId = topic_id,
        format = TaskFormat.valueOf(format_code),
        questionText = question_text,
        explanation = explanation,
        maxPoints = max_points.toInt(),
        answerWordLimit = answer_word_limit?.toInt(),
        isActive = is_active,
        options = options.map { TaskOption(it.option_label, it.option_text, OptionRole.valueOf(it.option_role)) },
        key = keys.map { KeySlot(it.answer_slot.toInt(), labels.getValue(it.option_id), it.prompt_option_id?.let(labels::getValue)) },
        acceptedAnswers = acceptedAnswers.map { AcceptedAnswer(it.answer_slot.toInt(), it.answer_text, it.case_sensitive) },
        attachments = attachments.map { Attachment(it.file_url, MediaType.valueOf(it.media_type), it.alt_text) },
    )
}
