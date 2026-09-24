package dev.catsradar.app.reporting

import com.google.firebase.crashlytics.FirebaseCrashlytics

class CrashlyticsNonFatalReporter : NonFatalReporter {
    override fun record(error: Throwable) = FirebaseCrashlytics.getInstance().recordException(error)
}
