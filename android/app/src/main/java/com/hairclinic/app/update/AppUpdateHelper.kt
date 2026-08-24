package com.hairclinic.app.update

import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.hairclinic.app.BuildConfig
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.AppReleaseInfo
import com.hairclinic.app.data.Session
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import retrofit2.HttpException

class AppUpdateHelper(private val activity: AppCompatActivity) {
    private var dialog: AlertDialog? = null
    private var updating = false

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    fun checkOnLaunch() {
        activity.lifecycleScope.launch {
            val info = runCatching { fetchInfo() }.getOrNull() ?: return@launch
            if (info.version_code > BuildConfig.VERSION_CODE) {
                showUpdateDialog(info)
            }
        }
    }

    fun promptUpdate(info: AppReleaseInfo) {
        showUpdateDialog(info)
    }

    fun checkFromMenu() {
        if (updating) return
        activity.lifecycleScope.launch {
            Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show()
            val result = runCatching { fetchInfo() }
            val info = result.getOrNull()
            if (info == null) {
                Toast.makeText(
                    activity,
                    result.exceptionOrNull()?.message ?: "检查失败",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            if (info.version_code > BuildConfig.VERSION_CODE) {
                showUpdateDialog(info)
            } else {
                Toast.makeText(
                    activity,
                    "当前已是最新版本 v${BuildConfig.VERSION_NAME}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private suspend fun fetchInfo(): AppReleaseInfo {
        try {
            val raw = ApiClient.get(activity).appInfo()
            return raw.copy(
                download_url = AppUpdater.resolveDownloadUrl(
                    Session.baseUrl(activity),
                    raw.download_url,
                ),
            )
        } catch (e: HttpException) {
            if (e.code() == 404) error("Android 安装包尚未发布。")
            error("检查失败（${e.code()}）")
        }
    }

    private fun showUpdateDialog(info: AppReleaseInfo) {
        if (dialog?.isShowing == true) return
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        val message = view.findViewById<TextView>(R.id.updateMessage)
        val progressBar = view.findViewById<LinearProgressIndicator>(R.id.updateProgress)
        val progressLabel = view.findViewById<TextView>(R.id.updateProgressLabel)
        val sizeMb = (info.size_bytes / 1048576.0).roundToInt()
        message.text = "当前可更新到 v${info.version_name}（约 ${sizeMb} MB）。是否立即下载安装？"

        val dlg = AlertDialog.Builder(activity)
            .setTitle("发现新版本")
            .setView(view)
            .setPositiveButton("更新", null)
            .setNegativeButton("稍后", null)
            .setCancelable(true)
            .create()
        dialog = dlg
        dlg.setOnShowListener {
            val positive = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            val negative = dlg.getButton(AlertDialog.BUTTON_NEGATIVE)
            dlg.setOnCancelListener {
                if (updating) dlg.show()
            }
            negative.setOnClickListener {
                if (!updating) dlg.dismiss()
            }
            positive.setOnClickListener {
                if (updating) return@setOnClickListener
                if (!AppUpdater.canInstallPackages(activity)) {
                    Toast.makeText(activity, "请先允许安装未知来源应用。", Toast.LENGTH_LONG).show()
                    AppUpdater.openInstallPermissionSettings(activity)
                    return@setOnClickListener
                }
                updating = true
                dlg.setCancelable(false)
                positive.isEnabled = false
                negative.isEnabled = false
                progressBar.visibility = android.view.View.VISIBLE
                progressLabel.visibility = android.view.View.VISIBLE
                progressBar.isIndeterminate = true
                progressLabel.text = "准备下载…"
                positive.text = "下载中…"
                activity.lifecycleScope.launch {
                    try {
                        val apk = AppUpdater.downloadApk(
                            context = activity,
                            url = info.download_url,
                            expectedBytes = info.size_bytes,
                            onProgress = { progress ->
                                val fraction = progress.fraction
                                if (fraction != null) {
                                    progressBar.isIndeterminate = false
                                    progressBar.max = 100
                                    progressBar.progress = progress.percent ?: 0
                                    positive.text = "下载中 ${progress.percent}%"
                                } else {
                                    progressBar.isIndeterminate = true
                                }
                                progressLabel.text = progress.label()
                            },
                        )
                        AppUpdater.installApk(activity, apk)
                        dlg.dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(
                            activity,
                            e.message ?: "更新失败，请稍后重试。",
                            Toast.LENGTH_LONG,
                        ).show()
                        dlg.setCancelable(true)
                        positive.isEnabled = true
                        negative.isEnabled = true
                        positive.text = "更新"
                        progressBar.visibility = android.view.View.GONE
                        progressLabel.visibility = android.view.View.GONE
                    } finally {
                        updating = false
                    }
                }
            }
        }
        dlg.show()
    }
}
