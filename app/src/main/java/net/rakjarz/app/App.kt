package net.rakjarz.app

import android.app.Application
import android.util.Log
import com.hypertrack.hyperlog.HyperLog
import net.rakjarz.corn.Corn
import java.util.concurrent.TimeUnit

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        Corn.initialize(this)
        HyperLog.initialize(this, TimeUnit.DAYS.toMillis(14).toInt())
        HyperLog.setLogLevel(Log.VERBOSE)
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    override fun onTerminate() {
        Corn.cleanup()
        super.onTerminate()
    }
}