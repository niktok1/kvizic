package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.topic.TopicDto

/** DTO to domain translation for a topic: the only place a `TopicDto` and a `Topic` meet. */
internal fun TopicDto.toDomain(): Topic =
    Topic(id = id, nameSr = nameSr, nameEn = nameEn, questionCount = questionCount)
