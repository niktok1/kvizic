package io.ntole.kvizic.region

/**
 * What the device says of where it is and what its owner reads ([RegionHints]), read once, at a first launch,
 * and never sent anywhere: only the country and the language picked from it are kept. Each platform's own,
 * bound by its platform module (`platformModule`), Android's with the `Context` Koin holds: the phone's network
 * and SIM country (`TelephonyManager`, which needs no permission for either) and its locales; iOS's locale and
 * preferred languages (there is no carrier API); the JVM's default locale; the browser's languages.
 *
 * A device that fails to say is a device that says nothing ([RegionStore] asks it so): the launch goes on.
 */
fun interface DeviceRegion {
    fun hints(): RegionHints

    companion object {
        /** A device that says nothing: a test's. */
        val None: DeviceRegion = DeviceRegion { RegionHints() }
    }
}
