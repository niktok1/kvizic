package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.core.region.Country as WireCountry

/**
 * The domain's country as the wire names it: the same entry by name, which `CountryTest` holds the two enums
 * to, so this cannot fail.
 */
internal fun Country.toWire(): WireCountry = WireCountry.valueOf(name)
