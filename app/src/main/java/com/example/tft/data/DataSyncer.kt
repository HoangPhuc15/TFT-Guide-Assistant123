package com.example.tft.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.tft.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the latest TFT reference data whenever the device has network
 * access.  Results are cached under `filesDir/data/sets/live` and transparently
 * consumed by [TftRepository].
 */
class DataSyncer private constructor(
    private val context: Context,
    private val client: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleRefresh() {
        scope.launch {
            val report = refreshIfConnected()
            if (report.failures.isNotEmpty()) {
                Log.w(TAG, "Data sync completed with errors: ${report.failures}")
            } else {
                Log.i(TAG, "Data sync completed (${report.successCount} updates)")
            }
        }
    }

    suspend fun refreshIfConnected(): DataSyncReport = withContext(Dispatchers.IO) {
        if (!hasInternet()) {
            return@withContext DataSyncReport(skipped = true)
        }

        val baseDir = File(context.filesDir, LIVE_SET_FOLDER)
        baseDir.mkdirs()

        var updated = 0
        val failures = mutableListOf<String>()
        for (file in DataFile.values()) {
            val url = urlFor(file) ?: continue
            val destination = File(baseDir, file.fileName)
            try {
                val body = fetch(url)
                destination.writeBytes(body)
                updated += 1
                Log.d(TAG, "Fetched ${file.fileName} from $url")
            } catch (io: IOException) {
                failures += file.fileName
                Log.w(TAG, "Unable to download ${file.fileName} from $url", io)
            }
        }

        DataSyncReport(successCount = updated, failures = failures)
    }

    private fun hasInternet(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Throws(IOException::class)
    private fun fetch(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP ${response.code} for $url")
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun urlFor(file: DataFile): String? = when (file) {
        DataFile.CHAMPIONS,
        DataFile.ITEMS,
        DataFile.TRAITS,
        DataFile.AUGMENTS -> "https://ddragon.leagueoflegends.com/cdn/${BuildConfig.DDRAGON_VERSION}/data/en_US/${file.fileName}"
        DataFile.TIPS -> BuildConfig.TIPS_RULES_URL.ifBlank { null }
    }

    data class DataSyncReport(
        val successCount: Int = 0,
        val failures: List<String> = emptyList(),
        val skipped: Boolean = false,
    )

    private enum class DataFile(val fileName: String) {
        CHAMPIONS("tft-champions.json"),
        ITEMS("tft-items.json"),
        TRAITS("tft-traits.json"),
        AUGMENTS("tft-augments.json"),
        TIPS("tips-rules.json"),
    }

    companion object {
        private const val TAG = "TftDataSync"
        private const val LIVE_SET_FOLDER = "data/sets/live"

        fun from(context: Context): DataSyncer {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            return DataSyncer(context.applicationContext, client)
        }
    }
}
