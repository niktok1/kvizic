package io.ntole.kvizic.region

import java.util.Locale

/** A desktop's [RegionHints]: the JVM's default locale, which it takes from the system's, its region and language. */
internal object JvmDeviceRegion : DeviceRegion {
    override fun hints(): RegionHints {
        val locale = Locale.getDefault()
        return RegionHints(
            localeCountry = locale.country.takeIf { it.isNotBlank() },
            preferredLanguages = listOf(locale.toLanguageTag()).filter { it != UNDETERMINED },
        )
    }

    /** What [Locale.toLanguageTag] says of a locale with no language. */
    private const val UNDETERMINED = "und"
}
