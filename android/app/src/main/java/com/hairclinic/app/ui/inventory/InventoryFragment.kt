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
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.StockItem
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.BadgeTone
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import kotlinx.coroutines.launch

class InventoryFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "库存"
        binding.pageSubtitle.text = "各产品当前库存数量"
        binding.searchInput.hint = "搜索产品名"
        binding.setupInventoryTabs(
            this,
            InventorySection.STOCK,
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
                val list = ApiClient.get(requireContext()).listInventory(q)
                adapter.submit(list.map { item ->
                    Item(
                        title = item.name,
                        subtitle = item.priceSummary(),
                        badge = item.stockText(),
                        badgeTone = if (item.stock_qty <= 0) BadgeTone.WARN else BadgeTone.SUCCESS,
                        onClick = { openStock(item) },
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无库存信息，请先添加产品并入库"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openStock(item: StockItem) {
        findNavController().navigate(R.id.stockFragment, StockFragment.args(item))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
