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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.StockMovement
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.databinding.ItemInboundRowBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class InboundListFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = InboundRecordAdapter { confirmDelete(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "入库"
        binding.pageSubtitle.text = "入库记录"
        binding.searchInput.hint = "搜索药品名 / 入库编号"
        binding.tableHeader.isVisible = true
        binding.tableCol3.isVisible = true
        binding.setupInventoryTabs(
            this,
            InventorySection.INBOUND,
            addLabel = "入库",
            onAdd = { findNavController().navigate(R.id.inboundFragment) },
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
                val list = ApiClient.get(requireContext()).listStockMovements(
                    kind = "IN",
                    q = q,
                    limit = 200,
                )
                adapter.submit(list)
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无入库记录，点击右上角入库"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(record: StockMovement) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除入库记录")
            .setMessage("确定删除「${record.item_name}」${record.inboundNoText()} 的入库记录？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> delete(record) }
            .show()
    }

    private fun delete(record: StockMovement) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).deleteStockMovement(record.id)
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

class InboundRecordAdapter(
    private val onDelete: (StockMovement) -> Unit,
) : RecyclerView.Adapter<InboundRecordAdapter.VH>() {
    private val items = mutableListOf<StockMovement>()

    fun submit(data: List<StockMovement>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemInboundRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.colTime.text = item.inboundNoText()
        holder.binding.colProduct.text = item.item_name
        holder.binding.colQty.text = item.qtyText()
        holder.binding.deleteBtn.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemInboundRowBinding) : RecyclerView.ViewHolder(binding.root)
}
