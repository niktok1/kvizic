package io.ntole.kvizic.core.domain.region

/**
 * Where the player plays from, which decides the questions they are asked; their language decides only how
 * those read. The wire's countries, copied, since the domain cannot see them: `CountryTest` in `:core:data`
 * pins the names and [defaultFor]. [UNKNOWN] is one this build has no entry for.
 */
public enum class Country {
    RS,
    HR,
    BA,
    SI,
    MK,
    ME,
    UNKNOWN,
    ;

    public companion object {
        /**
         * The content language a player in [country] reads by default, by tag, which a first launch takes
         * when the device's own language is none the app offers. Montenegro reads Serbian, which the client
         * shows in Latin; a country this build does not know, Serbian.
         */
        public fun defaultFor(country: Country): String =
            when (country) {
                HR -> "hr"
                BA -> "bs"
                SI -> "sl"
                MK -> "mk"
                RS, ME, UNKNOWN -> "sr-Cyrl"
            }
    }
}
