package com.hairclinic.app.ui.me

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.hairclinic.app.BuildConfig
import com.hairclinic.app.MainActivity
import com.hairclinic.app.R
import com.hairclinic.app.data.AccountUpdateIn
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.AppReleaseInfo
import com.hairclinic.app.data.Session
import com.hairclinic.app.databinding.FragmentMeBinding
import com.hairclinic.app.ui.enterAccount
import com.hairclinic.app.ui.returnToAdminAccount
import com.hairclinic.app.update.AppUpdater
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

class MeFragment : Fragment() {
    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!
    private var releaseInfo: AppReleaseInfo? = null
    private var checking = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refreshProfile()
        binding.currentVersionText.text = "当前版本  v${BuildConfig.VERSION_NAME}"
        binding.editAccountBtn.setOnClickListener { showAccountDialog() }
        binding.switchAccountBtn.setOnClickListener { showSwitchDialog() }
        binding.returnAdminBtn.setOnClickListener { returnToAdmin() }
        binding.checkUpdateBtn.setOnClickListener { checkUpdate() }
        binding.installUpdateBtn.setOnClickListener {
            val info = releaseInfo ?: return@setOnClickListener
            (requireActivity() as MainActivity).appUpdate.promptUpdate(info)
        }
        binding.logoutBtn.setOnClickListener { confirmLogout() }
        checkUpdate(silent = true)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refreshProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshProfile() {
        val name = Session.username(requireContext()).ifBlank { "用户" }
        binding.usernameText.text = name
        binding.avatarLetter.text = name.first().toString()
        val impersonating = Session.isImpersonating(requireContext())
        val role = Session.roleLabel(requireContext())
        val origin = Session.originUsername(requireContext())
        binding.roleText.text = if (impersonating) {
            "以 $role 身份登录"
        } else {
            "心尚植发 · $role"
        }
        binding.accountHint.text = if (impersonating) {
            "当前账号：$name（$role），由管理员 $origin 切换。权限与该账号一致，可返回管理员，或修改当前账号资料。"
        } else {
            "当前账号：$name（$role）。可修改用户名和登录密码。"
        }
        binding.switchAccountBtn.isVisible = Session.isAdmin(requireContext()) && !impersonating
        binding.returnAdminBtn.isVisible = impersonating
    }

    private fun checkUpdate(silent: Boolean = false) {
        if (checking) return
        checking = true
        if (!silent) binding.latestVersionText.text = "最新版本  检查中…"
        binding.checkUpdateBtn.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val raw = ApiClient.get(requireContext()).appInfo()
                val info = raw.copy(
                    download_url = AppUpdater.resolveDownloadUrl(
                        Session.baseUrl(requireContext()),
                        raw.download_url,
                    ),
                )
                releaseInfo = info
                val sizeMb = (info.size_bytes / 1048576.0).roundToInt()
                binding.latestVersionText.text = "最新版本  v${info.version_name}（约 ${sizeMb} MB）"
                val hasUpdate = info.version_code > BuildConfig.VERSION_CODE
                binding.installUpdateBtn.isVisible = hasUpdate
                binding.updateStatusText.text = if (hasUpdate) "发现新版本，建议更新。" else "当前已是最新版本。"
                binding.updateStatusText.setTextColor(
                    requireContext().getColor(if (hasUpdate) R.color.cinnabar else R.color.pine),
                )
                if (!silent && !hasUpdate) {
                    Toast.makeText(requireContext(), "当前已是最新版本", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                releaseInfo = null
                binding.installUpdateBtn.isVisible = false
                binding.latestVersionText.text = "最新版本  ${apiErrorMessage(e)}"
                binding.updateStatusText.text = ""
                if (!silent) Toast.makeText(requireContext(), apiErrorMessage(e), Toast.LENGTH_LONG).show()
            } finally {
                checking = false
                _binding?.checkUpdateBtn?.isEnabled = true
            }
        }
    }

    private fun showSwitchDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val me = Session.username(requireContext())
                val list = ApiClient.get(requireContext()).listStaff()
                    .filter { it.username != me }
                if (list.isEmpty()) {
                    toast("没有可切换的账号")
                    return@launch
                }
                val labels = list.map { "${it.username}（${it.role_label}）" }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("切换登录账号")
                    .setItems(labels) { _, which ->
                        val staff = list.getOrNull(which) ?: return@setItems
                        switchTo(staff.id, staff.username, staff.role)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } catch (e: Exception) {
                toast(apiErrorMessage(e))
            }
        }
    }

    private fun switchTo(staffId: Int, username: String, role: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = ApiClient.get(requireContext()).loginAsStaff(staffId)
                toast("已切换到 ${token.username.ifBlank { username }}")
                enterAccount(token, role)
            } catch (e: Exception) {
                toast(apiErrorMessage(e))
            }
        }
    }

    private fun returnToAdmin() {
        val origin = Session.originUsername(requireContext()).ifBlank { "管理员" }
        AlertDialog.Builder(requireContext())
            .setTitle("返回管理员")
            .setMessage("返回账号 $origin ？")
            .setPositiveButton("返回") { _, _ ->
                if (!returnToAdminAccount()) toast("无法返回管理员")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAccountDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_account, null)
        val usernameInput = view.findViewById<TextInputEditText>(R.id.accountUsername)
        val currentPasswordInput = view.findViewById<TextInputEditText>(R.id.accountCurrentPassword)
        val newPasswordInput = view.findViewById<TextInputEditText>(R.id.accountNewPassword)
        val confirmPasswordInput = view.findViewById<TextInputEditText>(R.id.accountConfirmPassword)
        usernameInput.setText(Session.username(requireContext()))

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("修改账号密码")
            .setView(view)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val currentPassword = currentPasswordInput.text?.toString().orEmpty()
                val newPassword = newPasswordInput.text?.toString().orEmpty()
                val confirmPassword = confirmPasswordInput.text?.toString().orEmpty()
                when {
                    username.length < 2 -> {
                        toast("用户名至少 2 个字符")
                        return@setOnClickListener
                    }
                    currentPassword.isEmpty() -> {
                        toast("请输入当前密码")
                        return@setOnClickListener
                    }
                    newPassword.isNotEmpty() && newPassword.length < 6 -> {
                        toast("新密码至少 6 位")
                        return@setOnClickListener
                    }
                    newPassword != confirmPassword -> {
                        toast("两次输入的新密码不一致")
                        return@setOnClickListener
                    }
                    username == Session.username(requireContext()) && newPassword.isEmpty() -> {
                        toast("请修改用户名或设置新密码")
                        return@setOnClickListener
                    }
                }
                saveBtn.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val result = ApiClient.get(requireContext()).updateAccount(
                            AccountUpdateIn(
                                current_password = currentPassword,
                                username = username,
                                new_password = newPassword.ifBlank { null },
                            ),
                        )
                        Session.saveToken(requireContext(), result.access_token)
                        Session.saveUsername(requireContext(), result.username)
                        Session.saveRole(requireContext(), result.role)
                        (activity as? MainActivity)?.applyRoleTabs()
                        (activity as? MainActivity)?.refreshUsername()
                        refreshProfile()
                        toast("账号已更新")
                        dialog.dismiss()
                    } catch (e: Exception) {
                        toast(apiErrorMessage(e))
                        saveBtn.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmLogout() {
        val name = Session.username(requireContext()).ifBlank { "当前账号" }
        AlertDialog.Builder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定退出账号 $name ？")
            .setPositiveButton("退出") { _, _ ->
                Session.clear(requireContext())
                val options = NavOptions.Builder()
                    .setPopUpTo(findNavController().graph.id, true)
                    .setLaunchSingleTop(true)
                    .build()
                findNavController().navigate(R.id.loginFragment, null, options)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun apiErrorMessage(e: Exception): String {
        if (e is HttpException) {
            val raw = e.response()?.errorBody()?.string().orEmpty()
            runCatching {
                val detail = JSONObject(raw).opt("detail")
                if (detail is String && detail.isNotBlank()) return detail
            }
            if (e.code() == 401) return "登录已过期，请重新登录"
            if (e.code() == 404) return "安装包尚未发布"
        }
        return e.message ?: "操作失败"
    }
}
