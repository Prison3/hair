package com.hairclinic.app.ui.staff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Session
import com.hairclinic.app.data.Staff
import com.hairclinic.app.data.StaffCreate
import com.hairclinic.app.data.StaffUpdate
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.BadgeTone
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class StaffFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "用户管理"
        binding.pageSubtitle.text = "添加店长或管理员账号"
        binding.searchInput.hint = "搜索用户名"
        binding.addBtn.text = "添加用户"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.addBtn.setOnClickListener { showEditor(null) }
        binding.searchBtn.setOnClickListener { load() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                load()
                true
            } else false
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val q = binding.searchInput.text?.toString()?.trim().orEmpty()
                val list = ApiClient.get(requireContext()).listStaff()
                    .filter { q.isBlank() || it.username.contains(q, ignoreCase = true) }
                val me = Session.username(requireContext())
                adapter.submit(list.map { staff ->
                    Item(
                        title = staff.username,
                        subtitle = if (staff.username == me) "当前登录账号" else "点击可重置密码",
                        badge = staff.role_label,
                        badgeTone = if (staff.role == Session.ROLE_ADMIN) BadgeTone.GOLD else BadgeTone.SUCCESS,
                        onClick = { showEditor(staff) },
                        onDelete = if (staff.username == me) null else ({ confirmDelete(staff) }),
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无用户，点击下方添加"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), ProjectEditFragment.apiError(e, "加载失败"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditor(staff: Staff?) {
        val view = layoutInflater.inflate(R.layout.dialog_staff, null)
        val usernameInput = view.findViewById<TextInputEditText>(R.id.staffUsername)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.staffPassword)
        val roleInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.staffRole)
        val roles = listOf("店长", "管理员")
        roleInput.setAdapter(ArrayAdapter(requireContext(), R.layout.item_spinner, roles))
        roleInput.keyListener = null
        val isEdit = staff != null
        if (staff != null) {
            usernameInput.setText(staff.username)
            roleInput.setText(staff.role_label, false)
            passwordInput.hint = "不改可留空"
        } else {
            roleInput.setText("店长", false)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) "编辑用户" else "添加用户")
            .setView(view)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString().orEmpty()
                val role = if (roleInput.text?.toString()?.trim() == "管理员") Session.ROLE_ADMIN else Session.ROLE_MANAGER
                when {
                    username.length < 2 -> {
                        toast("用户名至少 2 个字符")
                        return@setOnClickListener
                    }
                    !isEdit && password.length < 6 -> {
                        toast("密码至少 6 位")
                        return@setOnClickListener
                    }
                    isEdit && password.isNotEmpty() && password.length < 6 -> {
                        toast("密码至少 6 位")
                        return@setOnClickListener
                    }
                }
                saveBtn.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val api = ApiClient.get(requireContext())
                        if (staff == null) {
                            api.createStaff(StaffCreate(username, password, role))
                        } else {
                            api.updateStaff(
                                staff.id,
                                StaffUpdate(
                                    username = username,
                                    password = password.ifBlank { null },
                                    role = role,
                                ),
                            )
                        }
                        toast("已保存")
                        dialog.dismiss()
                        load()
                    } catch (e: Exception) {
                        toast(ProjectEditFragment.apiError(e, "保存失败"))
                        saveBtn.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(staff: Staff) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除用户")
            .setMessage("确定删除「${staff.username}」？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> delete(staff) }
            .show()
    }

    private fun delete(staff: Staff) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).deleteStaff(staff.id)
                toast("已删除")
                load()
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
}
