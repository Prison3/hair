package com.hairclinic.app.ui.staff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Session
import com.hairclinic.app.data.Staff
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.BadgeTone
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import com.hairclinic.app.ui.enterAccount
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
        binding.pageSubtitle.text = "添加账号，或切换登录到任意用户"
        binding.searchInput.hint = "搜索用户名"
        binding.addBtn.text = "添加用户"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.addBtn.setOnClickListener { openEditor(null) }
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
                    val isMe = staff.username == me
                    Item(
                        title = staff.username,
                        subtitle = when {
                            isMe -> "当前登录账号"
                            else -> "点击编辑，或点登录切换到此账号"
                        },
                        badge = staff.role_label,
                        badgeTone = if (staff.role == Session.ROLE_ADMIN) BadgeTone.GOLD else BadgeTone.SUCCESS,
                        onClick = { openEditor(staff) },
                        actionLabel = "登录",
                        onAction = if (isMe) null else ({ confirmSwitch(staff) }),
                        onDelete = if (isMe) null else ({ confirmDelete(staff) }),
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无用户，点击下方添加"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), ProjectEditFragment.apiError(e, "加载失败"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openEditor(staff: Staff?) {
        findNavController().navigate(R.id.staffEditFragment, StaffEditFragment.args(staff))
    }

    private fun confirmSwitch(staff: Staff) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("切换登录")
            .setMessage("以「${staff.username}」（${staff.role_label}）登录？之后可在「我的」返回管理员。")
            .setNegativeButton("取消", null)
            .setPositiveButton("切换") { _, _ -> switchTo(staff) }
            .show()
    }

    private fun switchTo(staff: Staff) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = ApiClient.get(requireContext()).loginAsStaff(staff.id)
                toast("已切换到 ${token.username}")
                enterAccount(token, staff.role)
            } catch (e: Exception) {
                toast(ProjectEditFragment.apiError(e, "切换失败"))
            }
        }
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
