package net.rakjarz.app

import android.app.Application
import net.rakjarz.corn.Corn

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        Corn.initialize(this)
    }

    override fun onTerminate() {
        Corn.cleanup()
        super.onTerminate()
    }
}