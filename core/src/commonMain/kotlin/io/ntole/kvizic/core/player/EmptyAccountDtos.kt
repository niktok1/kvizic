package io.ntole.kvizic.core.player

import kotlinx.serialization.Serializable

/**
 * How many accounts are empty: no Play Games link, no game played, no answer given, and not seen for at
 * least [idleDays] whole days.
 */
@Serializable
public data class EmptyAccountsDto(
    public val idleDays: Int,
    public val count: Int,
)

/**
 * A moderator deleting every empty account idle for [idleDays] days, which they counted as [expectedCount]: a
 * different count now is refused, so a deletion is never larger than what the moderator was shown.
 */
@Serializable
public data class DeleteEmptyAccountsRequest(
    public val idleDays: Int,
    public val expectedCount: Int,
)

/** How many accounts a [DeleteEmptyAccountsRequest] deleted. */
@Serializable
public data class DeletedAccountsDto(
    public val deleted: Int,
)
