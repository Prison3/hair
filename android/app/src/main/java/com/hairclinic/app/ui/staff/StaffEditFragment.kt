package com.hairclinic.app.ui.staff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Session
import com.hairclinic.app.data.Staff
import com.hairclinic.app.data.StaffCreate
import com.hairclinic.app.data.StaffUpdate
import com.hairclinic.app.databinding.FragmentStaffEditBinding
import com.hairclinic.app.ui.enterAccount
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class StaffEditFragment : Fragment() {
    private var _binding: FragmentStaffEditBinding? = null
    private val binding get() = _binding!!
    private var staffId: Int = -1
    private var originalUsername: String = ""
    private var originalRole: String = Session.ROLE_MANAGER

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStaffEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        staffId = arguments?.getInt(ARG_ID, -1) ?: -1
        originalUsername = arguments?.getString(ARG_USERNAME).orEmpty()
        originalRole = arguments?.getString(ARG_ROLE).orEmpty().ifBlank { Session.ROLE_MANAGER }
        val isEdit = staffId > 0
        val isMe = isEdit && originalUsername == Session.username(requireContext())

        binding.pageTitle.text = if (isEdit) "编辑用户" else "添加用户"
        binding.pageSubtitle.text = if (isEdit) "修改用户名、密码或角色后保存" else "设置用户名、密码和角色后保存"
        binding.passwordLayout.hint = if (isEdit) "密码（不改可留空）" else "密码"
        binding.loginBtn.isVisible = isEdit && !isMe
        binding.deleteBtn.isVisible = isEdit && !isMe

        val roles = listOf("店长", "管理员")
        binding.inputRole.setAdapter(ArrayAdapter(requireContext(), R.layout.item_spinner, roles))
        binding.inputRole.keyListener = null
        if (isEdit) {
            binding.inputUsername.setText(originalUsername)
            binding.inputRole.setText(if (originalRole == Session.ROLE_ADMIN) "管理员" else "店长", false)
        } else {
            binding.inputRole.setText("店长", false)
        }

        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.cancelBtn.setOnClickListener { findNavController().navigateUp() }
        binding.saveBtn.setOnClickListener { save() }
        binding.loginBtn.setOnClickListener { confirmSwitch() }
        binding.deleteBtn.setOnClickListener { confirmDelete() }
    }

    private fun selectedRole(): String =
        if (binding.inputRole.text?.toString()?.trim() == "管理员") Session.ROLE_ADMIN else Session.ROLE_MANAGER

    private fun save() {
        val username = binding.inputUsername.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val isEdit = staffId > 0
        when {
            username.length < 2 -> {
                toast("用户名至少 2 个字符")
                return
            }
            !isEdit && password.length < 6 -> {
                toast("密码至少 6 位")
                return
            }
            isEdit && password.isNotEmpty() && password.length < 6 -> {
                toast("密码至少 6 位")
                return
            }
        }
        binding.saveBtn.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                if (!isEdit) {
                    api.createStaff(StaffCreate(username, password, selectedRole()))
                } else {
                    api.updateStaff(
                        staffId,
                        StaffUpdate(
                            username = username,
                            password = password.ifBlank { null },
                            role = selectedRole(),
                        ),
                    )
                    if (originalUsername == Session.username(requireContext())) {
                        Session.saveUsername(requireContext(), username)
                    }
                }
                toast("已保存")
                findNavController().navigateUp()
            } catch (e: Exception) {
                toast(ProjectEditFragment.apiError(e, "保存失败"))
                binding.saveBtn.isEnabled = true
            }
        }
    }

    private fun confirmSwitch() {
        val name = binding.inputUsername.text?.toString()?.trim().orEmpty().ifBlank { originalUsername }
        val roleLabel = if (selectedRole() == Session.ROLE_ADMIN) "管理员" else "店长"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("切换登录")
            .setMessage("以「$name」（$roleLabel）登录？之后可在「我的」返回管理员。")
            .setNegativeButton("取消", null)
            .setPositiveButton("切换") { _, _ -> switchTo() }
            .show()
    }

    private fun switchTo() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = ApiClient.get(requireContext()).loginAsStaff(staffId)
                toast("已切换到 ${token.username}")
                enterAccount(token)
            } catch (e: Exception) {
                toast(ProjectEditFragment.apiError(e, "切换失败"))
            }
        }
    }

    private fun confirmDelete() {
        val name = binding.inputUsername.text?.toString()?.trim().orEmpty().ifBlank { originalUsername }.ifBlank { "该用户" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除用户")
            .setMessage("确定删除「$name」？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> delete() }
            .show()
    }

    private fun delete() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).deleteStaff(staffId)
                toast("已删除")
                findNavController().navigateUp()
            } catch (e: Exception) {
                toast(ProjectEditFragment.apiError(e, "删除失败"))
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "staff_id"
        const val ARG_USERNAME = "username"
        const val ARG_ROLE = "role"

        fun args(staff: Staff? = null): Bundle = Bundle().apply {
            putInt(ARG_ID, staff?.id ?: -1)
            putString(ARG_USERNAME, staff?.username.orEmpty())
            putString(ARG_ROLE, staff?.role.orEmpty())
        }
    }
}
