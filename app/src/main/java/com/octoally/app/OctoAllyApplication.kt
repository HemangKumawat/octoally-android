package com.octoally.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter

@HiltAndroidApp
class OctoAllyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    /**
     * Install a global uncaught-exception handler that writes the stack trace
     * to `filesDir/last_crash.txt` before chaining to the default handler.
     *
     * Purpose: when the app crashes on a device where we can't attach logcat
     * (user's phone in the field), the trace is still recoverable — the next
     * launch can surface it, or `adb shell run-as com.octoally.app cat
     * files/last_crash.txt` retrieves it once ADB is available.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = File(filesDir, "last_crash.txt")
                PrintWriter(file).use { pw ->
                    pw.println("thread=${thread.name} ts=${System.currentTimeMillis()}")
                    throwable.printStackTrace(pw)
                }
                Log.e("OctoAlly", "UNCAUGHT on ${thread.name}", throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
