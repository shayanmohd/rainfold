package com.mohdshayan.rainfold

import android.app.Application
import com.mohdshayan.rainfold.di.ServiceLocator

/**
 * Registered in the manifest as android:name=".App". Wires the service locator before any screen,
 * service, widget or tile can ask for it, and builds the billing repository once on the main thread
 * so no gate first builds it on another thread.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.unlock
    }
}
