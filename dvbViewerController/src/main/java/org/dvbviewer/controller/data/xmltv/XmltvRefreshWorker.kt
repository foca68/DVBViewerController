package org.dvbviewer.controller.data.xmltv

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.dvbviewer.controller.data.entities.DVBViewerPreferences
import java.util.concurrent.TimeUnit

class XmltvRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() started — runAttemptCount=$runAttemptCount")

        val prefs   = DVBViewerPreferences(applicationContext)
        val enabled = prefs.getBoolean(DVBViewerPreferences.KEY_XMLTV_ENABLED, false)
        Log.d(TAG, "KEY_XMLTV_ENABLED = $enabled")

        if (!enabled) {
            Log.d(TAG, "XMLTV is disabled in preferences — worker exits successfully without action")
            return Result.success()
        }

        val url = prefs.getString(DVBViewerPreferences.KEY_XMLTV_URL).trim()
        Log.d(TAG, "KEY_XMLTV_URL = \"$url\"")

        if (url.isEmpty()) {
            Log.w(TAG, "XMLTV URL is empty — set it in Settings → External EPG")
            return Result.failure()
        }

        return try {
            Log.d(TAG, "Creating XmltvRepository and calling downloadAndParse()")
            val repo = XmltvRepository(applicationContext)
            repo.downloadAndParse(url)
            Log.d(TAG, "doWork() — SUCCESS")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork() FAILED (attempt $runAttemptCount) — will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG      = "XmltvRefreshWorker"
        const val WORK_NAME        = "xmltv_periodic_refresh"

        /**
         * Schedules a periodic refresh job and an immediate one-shot run.
         * Safe to call multiple times (uses KEEP policy).
         */
        fun schedule(context: Context) {
            val prefs = DVBViewerPreferences(context)

            val enabled = prefs.getBoolean(DVBViewerPreferences.KEY_XMLTV_ENABLED, false)
            Log.d(TAG, "schedule() — KEY_XMLTV_ENABLED=$enabled")

            if (!enabled) {
                Log.d(TAG, "schedule() — XMLTV disabled, not scheduling any work")
                return
            }

            val url = prefs.getString(DVBViewerPreferences.KEY_XMLTV_URL).trim()
            Log.d(TAG, "schedule() — URL=\"$url\"")
            if (url.isEmpty()) {
                Log.w(TAG, "schedule() — URL is empty, skipping (configure it in Settings)")
            }

            val intervalHours = prefs.getString(
                DVBViewerPreferences.KEY_XMLTV_REFRESH_INTERVAL,
                DVBViewerPreferences.DEFAULT_XMLTV_REFRESH_INTERVAL
            ).toLongOrNull() ?: 6L
            Log.d(TAG, "schedule() — interval = $intervalHours h")

            val periodic = PeriodicWorkRequestBuilder<XmltvRefreshWorker>(
                intervalHours, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
            Log.i(TAG, "Periodic work enqueued: every $intervalHours h")

            // Immediate one-shot to populate data right away
            val oneShot = OneTimeWorkRequestBuilder<XmltvRefreshWorker>().build()
            WorkManager.getInstance(context).enqueue(oneShot)
            Log.i(TAG, "One-shot work enqueued for immediate execution")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Periodic XMLTV refresh cancelled")
        }
    }
}
