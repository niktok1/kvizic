package io.ntole.kvizic.region

import android.content.Context
import android.os.LocaleList
import android.telephony.TelephonyManager

/**
 * An Android device's [RegionHints]: the country of the mobile network the phone is on and of its SIM, which
 * `TelephonyManager` gives with no permission (a country code alone, neither the number nor a place, empty where
 * there is none: a tablet, a phone with no SIM or in flight mode), and the system's locales, first first. Read
 * from [context], the application's.
 */
internal class AndroidDeviceRegion(
    private val context: Context,
) : DeviceRegion {
    override fun hints(): RegionHints {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val locales = LocaleList.getDefault()
        return RegionHints(
            networkCountry = telephony?.networkCountryIso?.takeIf { it.isNotBlank() },
            simCountry = telephony?.simCountryIso?.takeIf { it.isNotBlank() },
            localeCountry = locales.get(0)?.country?.takeIf { it.isNotBlank() },
            preferredLanguages = (0 until locales.size()).map { locales.get(it).toLanguageTag() },
        )
    }
}
