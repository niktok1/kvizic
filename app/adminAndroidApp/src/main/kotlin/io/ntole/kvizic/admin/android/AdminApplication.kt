package io.ntole.kvizic.admin.android

import android.app.Application
import io.ntole.kvizic.admin.di.initAdminKoin

/** Starts the moderation app's DI against the environment this build names (`kvizic.adminEnv`). */
class AdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initAdminKoin(BuildConfig.KVIZIC_ENV)
    }
}
