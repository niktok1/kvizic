package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.core.region.ContentLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ntole.kvizic.core.region.Country as WireCountry

/** The domain's copy of the wire's countries, which the client stores and sends, is the wire's. */
class CountryTest {
    @Test
    fun `the countries are the wire's by name`() {
        assertEquals(WireCountry.entries.map { it.name }, Country.entries.map { it.name })
    }

    @Test
    fun `each country's first language is the wire's`() {
        Country.entries.forEach { country ->
            assertEquals(
                ContentLanguages.defaultFor(WireCountry.valueOf(country.name)),
                Country.defaultFor(country),
                country.name,
            )
        }
    }
}
