package com.chrispsz.rotinacheck.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrispsz.rotinacheck.update.GitHubSmartUpdate
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin UI bridge to [GitHubSmartUpdate]. The screen composable below is the
 * whole "integration surface": ON_START → [onAppOpened], banner ← [state],
 * actions → the four verbs. Update metadata lives in its own DataStore file,
 * so it survives even a routine "Limpar dados".
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val updater = GitHubSmartUpdate(
        context = app,
        owner = OWNER,
        repo = REPO,
        currentVersion = versionName(),
        scope = viewModelScope,
    )

    val state: StateFlow<GitHubSmartUpdate.State> = updater.state

    val feedback: StateFlow<GitHubSmartUpdate.Feedback?> = updater.feedback

    val installedVersion: String = updater.installedVersion

    fun onAppOpened() = updater.onAppOpened()

    fun checkNow() = updater.checkNow()

    fun ignoreCurrent() = updater.ignoreCurrent()

    fun hideForSession() = updater.hideForSession()

    fun openDownload() = updater.openDownload(getApplication())

    fun consumeFeedback() = updater.consumeFeedback()

    /** Non-deprecated PackageInfoFlags path (minSdk 34 → always available). */
    private fun versionName(): String = runCatching {
        val app = getApplication<Application>()
        app.packageManager
            .getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
            .versionName
    }.getOrNull() ?: ""

    companion object {
        /** The only two values to change when reusing the OTA in another app. */
        private const val OWNER = "Chrispsz"
        private const val REPO = "rotina-check"
    }
}
