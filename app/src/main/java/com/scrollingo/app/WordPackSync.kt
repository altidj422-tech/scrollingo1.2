package com.scrollingo.app

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Keeps the on-device word packs fresh by downloading larger/newer versions
 * over WiFi (an unmetered connection). This is the "online" half of the hybrid
 * content model: the quiz itself NEVER waits on the network — it always reads
 * the local pack (a downloaded one if present, otherwise the bundled words).
 * The sync just swaps in better content in the background when it can.
 *
 * Host layout expected at [BuildConfig.WORDPACK_BASE_URL]:
 *   manifest.json        e.g. {"es":3,"fr":2,"de":2,"it":2,"pt":2}
 *   words_es.json        same shape as the bundled assets, plus "version"
 *   words_fr.json ...
 *
 * If the base URL is empty (default) or unreachable, the app simply keeps using
 * its bundled words — nothing breaks.
 */
object WordPackSync {

    private const val TAG = "ScrollingoSync"
    private const val UNIQUE_WORK = "wordpack_sync"
    private const val VERSIONS_PREFS = "wordpack_versions"

    fun enqueue(context: Context) {
        if (BuildConfig.WORDPACK_BASE_URL.isBlank()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi / unmetered only
            .build()

        val request = PeriodicWorkRequestBuilder<WordPackWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun localVersion(context: Context, code: String): Int =
        context.getSharedPreferences(VERSIONS_PREFS, Context.MODE_PRIVATE).getInt(code, 0)

    fun setLocalVersion(context: Context, code: String, version: Int) =
        context.getSharedPreferences(VERSIONS_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(code, version).apply()
}

class WordPackWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    private companion object {
        const val TAG = "ScrollingoSync"
    }

    override fun doWork(): Result {
        val base = BuildConfig.WORDPACK_BASE_URL
        if (base.isBlank()) return Result.success()

        return try {
            val manifestRaw = fetch(url(base, "manifest.json"))
                ?: return Result.success() // no host configured / 404 -> nothing to do
            val versions = JSONObject(manifestRaw)

            var updated = false
            for (code in Dictionary.codes) {
                val remote = versions.optInt(code, -1)
                if (remote < 0 || remote <= WordPackSync.localVersion(applicationContext, code)) continue

                val packRaw = fetch(url(base, "words_$code.json")) ?: continue
                if (Dictionary.parseOrNull(packRaw) == null) {
                    Log.w(TAG, "Skipping invalid pack for $code")
                    continue
                }

                writeAtomically(Dictionary.localPackFile(applicationContext, code), packRaw)
                WordPackSync.setLocalVersion(applicationContext, code, remote)
                updated = true
                Log.i(TAG, "Updated $code pack to v$remote")
            }

            if (updated) Dictionary.clearCache()
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Sync failed, will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result.success() // malformed data shouldn't trigger endless retries
        }
    }

    /** GET the URL. Returns body on 200, null on 404/non-200, throws on IO. */
    private fun fetch(spec: String): String? {
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            target.writeText(content, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun url(base: String, name: String): String =
        base.trimEnd('/') + "/" + name
}
