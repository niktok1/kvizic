package io.ntole.kvizic.region

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

/**
 * An iPhone's or iPad's [RegionHints]: the region its locale is set to and the languages its owner prefers, first
 * first. iOS has no carrier API to say where the phone is.
 */
internal object IosDeviceRegion : DeviceRegion {
    override fun hints(): RegionHints =
        RegionHints(
            localeCountry = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String,
            preferredLanguages = NSLocale.preferredLanguages.filterIsInstance<String>(),
        )
}
