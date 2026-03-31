package com.cinex.player.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.cinex.player.BuildConfig
import com.cinex.player.data.network.GitHubApi
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
                    changelog = cleanChangelog(release.body),
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

    // Usando OkHttpClient para evitar dependência do DownloadManager (que é quebrado no Fire OS)
    private val okHttpClient = okhttp3.OkHttpClient()

    suspend fun downloadAndInstall(updateInfo: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        cleanOldApk(file)

        try {
            val request = okhttp3.Request.Builder()
                .url(updateInfo.apkUrl)
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            response.body?.let { body ->
                val expectedSize = body.contentLength()
                body.byteStream().use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Verifica se o arquivo baixado está completo
                if (expectedSize > 0 && file.length() != expectedSize) {
                    file.delete()
                    return@withContext false
                }
            } ?: return@withContext false

            return@withContext withContext(Dispatchers.Main) {
                installApk(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun installApk(file: File): Boolean {
        if (!file.exists()) return false

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val packageManager = context.packageManager
            val launchIntent = when {
                installIntent.resolveActivity(packageManager) != null -> installIntent
                fallbackIntent.resolveActivity(packageManager) != null -> fallbackIntent
                else -> null
            } ?: return false
            context.startActivity(launchIntent)
            return true
            
            // Hack sugerido para Fire OS: Forçar o fechamento do aplicativo logo após disparar 
            // a intenção de instalação. Isso previne que o gerenciador de pacotes do FireOS mate o app 
            // de forma bruta e cancele a janela de instalação no processo.
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun cleanOldApk(file: File) {
        try {
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

    private fun cleanChangelog(body: String?): String {
        if (body.isNullOrBlank()) return "Melhorias de desempenho e correções de bugs."

        // Remove markdown formatting
        var clean = body
            .replace(Regex("\\*\\*Full Changelog\\*\\*:.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("https?://\\S+"), "")          // Remove URLs
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")  // **bold** → bold
            .replace(Regex("\\*([^*]+)\\*"), "$1")         // *italic* → italic
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "") // Remove # headers
            .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "• ") // Bullet points
            .replace(Regex("\\n{3,}"), "\n\n")             // Múltiplas linhas vazias
            .trim()

        if (clean.isBlank()) return "Melhorias de desempenho e correções de bugs."
        return clean
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
