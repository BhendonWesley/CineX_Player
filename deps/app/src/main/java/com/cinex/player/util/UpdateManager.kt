package com.cinex.player.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.cinex.player.BuildConfig
import com.cinex.player.data.network.GitHubApi
import com.cinex.player.data.network.dto.GitHubReleaseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val newVersion: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long
)

@Singleton
class UpdateManager @Inject constructor(
    private val context: Context,
    private val gitHubApi: GitHubApi
) {
    private val prefs = context.getSharedPreferences("cinex_update", Context.MODE_PRIVATE)
    private var currentDownloadId: Long = -1L

    companion object {
        private const val KEY_CHANGELOG_SEEN_VERSION = "changelog_seen_version"
        private const val APK_FILE_NAME = "CineXPlayer-update.apk"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val release = gitHubApi.getLatestRelease()
            val remoteVersion = parseVersion(release.tagName)
            val currentVersion = parseVersion(BuildConfig.VERSION_NAME)

            if (remoteVersion > currentVersion) {
                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext null

                UpdateInfo(
                    newVersion = release.tagName.removePrefix("v"),
                    changelog = release.body ?: "",
                    apkUrl = apkAsset.downloadUrl,
                    apkSize = apkAsset.size
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun downloadAndInstall(updateInfo: UpdateInfo) {
        cleanOldApk()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
            .setTitle("CineX Player v${updateInfo.newVersion}")
            .setDescription("Baixando atualização...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            .setMimeType("application/vnd.android.package-archive")

        currentDownloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == currentDownloadId) {
                    ctx.unregisterReceiver(this)
                    installApk()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk() {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            APK_FILE_NAME
        )
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun cleanOldApk() {
        try {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APK_FILE_NAME
            )
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    fun shouldShowChangelog(): Boolean {
        val seenVersion = prefs.getString(KEY_CHANGELOG_SEEN_VERSION, null)
        return seenVersion != BuildConfig.VERSION_NAME
    }

    fun getChangelogForCurrentVersion(): String? {
        return prefs.getString("changelog_${BuildConfig.VERSION_NAME}", null)
    }

    fun saveChangelogForVersion(version: String, changelog: String) {
        prefs.edit()
            .putString("changelog_$version", changelog)
            .apply()
    }

    fun markChangelogSeen() {
        prefs.edit()
            .putString(KEY_CHANGELOG_SEEN_VERSION, BuildConfig.VERSION_NAME)
            .apply()
    }

    private fun parseVersion(tag: String): List<Int> {
        return tag.removePrefix("v")
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }

    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        val maxLen = maxOf(this.size, other.size)
        for (i in 0 until maxLen) {
            val a = this.getOrElse(i) { 0 }
            val b = other.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
