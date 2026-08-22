package com.hairclinic.app.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Project
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import kotlinx.coroutines.launch

class ProjectsFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "项目"
        binding.pageSubtitle.text = "植发套餐与价格"
        binding.searchRow.isVisible = false
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.addBtn.setOnClickListener { showEditor(null) }
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = ApiClient.get(requireContext()).listProjects()
                adapter.submit(list.map { p ->
                    Item(
                        title = p.name,
                        subtitle = "¥${"%.2f".format(p.price)} · ${p.graft_count} 单位 · ${if (p.active) "启用" else "停用"}\n${p.description.ifBlank { "无描述" }}",
                        onClick = { showEditor(p) },
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无项目，点击下方添加"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditor(project: Project?) {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        fun field(hint: String, value: String) = EditText(requireContext()).also {
            it.hint = hint
            it.setText(value)
            box.addView(it)
        }
        val name = field("名称", project?.name.orEmpty())
        val price = field("价格", project?.price?.toString().orEmpty())
        val graft = field("毛囊单位", project?.graft_count?.toString() ?: "0")
        val desc = field("描述", project?.description.orEmpty())

        AlertDialog.Builder(requireContext())
            .setTitle(if (project == null) "新建项目" else "编辑项目")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val body = Project(
                            id = project?.id,
                            name = name.text.toString().trim(),
                            price = price.text.toString().toDoubleOrNull() ?: 0.0,
                            graft_count = graft.text.toString().toIntOrNull() ?: 0,
                            description = desc.text.toString().trim(),
                            active = project?.active ?: true,
                        )
                        val api = ApiClient.get(requireContext())
                        if (project?.id == null) api.createProject(body) else api.updateProject(project.id!!, body)
                        load()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
