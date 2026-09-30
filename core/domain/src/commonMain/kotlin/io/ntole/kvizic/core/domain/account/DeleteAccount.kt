package io.ntole.kvizic.core.domain.account

import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent

/**
 * Deletes this device's account (see [AccountRepository.deleteAccount]), then has [analytics] forget the
 * player once they have heard it went: the next call plays as a fresh guest, and every event after is
 * joined to nobody before. A deletion that failed leaves both as they were.
 */
public class DeleteAccount(
    private val accounts: AccountRepository,
    private val analytics: Analytics,
) {
    public suspend operator fun invoke() {
        accounts.deleteAccount()
        // The account's last event, before the analytics forget whose it was.
        analytics.track(AnalyticsEvent.ACCOUNT_DELETED)
        analytics.reset()
    }
}
