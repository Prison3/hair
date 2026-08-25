package com.hairclinic.app.ui.inventory

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
import com.hairclinic.app.data.StockItem
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class ProductListFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "产品"
        binding.pageSubtitle.text = "产品档案，入库前请先录入"
        binding.searchInput.hint = "搜索产品名"
        binding.setupInventoryTabs(
            this,
            InventorySection.PRODUCTS,
            addLabel = "添加",
            onAdd = { findNavController().navigate(R.id.productEditFragment, ProductEditFragment.args()) },
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
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
                val q = binding.searchInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                val list = ApiClient.get(requireContext()).listInventory(q)
                adapter.submit(list.map { item ->
                    Item(
                        title = item.name,
                        onClick = { openEditor(item) },
                        onDelete = { confirmDelete(item) },
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无产品，点击右上角添加"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openEditor(item: StockItem) {
        findNavController().navigate(R.id.productEditFragment, ProductEditFragment.args(item))
    }

    private fun confirmDelete(item: StockItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除产品")
            .setMessage("确定删除「${item.name}」？相关入库记录也会一起删除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> delete(item) }
            .show()
    }

    private fun delete(item: StockItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).deleteStockItem(item.id)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), ProjectEditFragment.apiError(e, "删除失败"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
