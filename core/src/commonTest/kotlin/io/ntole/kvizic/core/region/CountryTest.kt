package io.ntole.kvizic.core.region

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryTest {
    @Test
    fun theNamesOnTheWireNeverChange() {
        assertEquals(listOf("RS", "HR", "BA", "SI", "MK", "ME", "UNKNOWN"), Country.entries.map { it.name })
    }

    @Test
    fun aNameParsesInAnyCaseTrimmed() {
        assertEquals(Country.HR, Country.parse("HR"))
        assertEquals(Country.ME, Country.parse(" me "))
        assertEquals(Country.BA, Country.parse("Ba"))
    }

    @Test
    fun noValueIsNoCountrySaid() {
        assertNull(Country.parse(null))
        assertNull(Country.parse(""))
        assertNull(Country.parse("  "))
    }

    @Test
    fun aNameThisBuildDoesNotKnowIsUnknown() {
        assertEquals(Country.UNKNOWN, Country.parse("AT"))
        assertEquals(Country.UNKNOWN, Country.parse("UNKNOWN"))
        assertEquals(Country.UNKNOWN, Country.parse("Србија"))
    }
}
