package com.hairclinic.app.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.StockItem
import com.hairclinic.app.data.StockOutRequest
import com.hairclinic.app.databinding.FragmentStockBinding
import com.hairclinic.app.databinding.ItemStockMoveBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class StockFragment : Fragment() {
    private var _binding: FragmentStockBinding? = null
    private val binding get() = _binding!!
    private var itemId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        itemId = arguments?.getInt(ARG_ID, -1) ?: -1
        val name = arguments?.getString(ARG_NAME).orEmpty().ifBlank { "库存" }
        binding.pageTitle.text = name
        binding.stockQtyText.text = "库存 ${arguments?.getInt(ARG_STOCK) ?: 0}"
        binding.costPriceText.text = "进货价 ¥${ProjectEditFragment.formatPrice(arguments?.getDouble(ARG_COST) ?: 0.0)}"
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.inBtn.setOnClickListener {
            findNavController().navigate(R.id.inboundFragment, InboundFragment.args(itemId))
        }
        binding.outBtn.setOnClickListener { outbound() }
        refresh()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                val item = api.getStockItem(itemId)
                bindItem(item)
                val moves = api.listStockMovements(itemId)
                binding.moveBox.removeAllViews()
                binding.moveEmpty.isVisible = moves.isEmpty()
                val pine = ContextCompat.getColor(requireContext(), R.color.pine)
                val warn = ContextCompat.getColor(requireContext(), R.color.cinnabar)
                moves.forEach { m ->
                    val row = ItemStockMoveBinding.inflate(layoutInflater, binding.moveBox, false)
                    val inbound = m.kind == "IN"
                    row.moveKind.text = m.kindLabel()
                    row.moveKind.setTextColor(if (inbound) pine else warn)
                    row.moveKind.setBackgroundResource(
                        if (inbound) R.drawable.bg_badge else R.drawable.bg_badge_warn
                    )
                    row.moveQty.text = "${if (inbound) "+" else "-"}${m.qtyText()}"
                    row.moveTime.text = if (inbound) m.inboundNoText() else m.timeText()
                    val cost = if (m.unit_cost > 0) "¥${ProjectEditFragment.formatPrice(m.unit_cost)}" else ""
                    val reason = m.reasonText()
                    row.moveMeta.text = listOf(
                        m.item_name,
                        cost,
                        if (reason.isNotBlank()) "原因 $reason" else null,
                    ).filter { !it.isNullOrBlank() }.joinToString(" · ").ifBlank { "无原因" }
                    binding.moveBox.addView(row.root)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindItem(item: StockItem) {
        binding.pageTitle.text = item.name
        binding.stockQtyText.text = "库存 ${item.stock_qty}${item.unitLabel()}"
        binding.costPriceText.text = item.costText()
    }

    private fun outbound() {
        val qty = binding.outQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            Toast.makeText(requireContext(), "请填写出库数量", Toast.LENGTH_SHORT).show()
            return
        }
        val reason = binding.outRemark.text?.toString()?.trim().orEmpty()
        if (reason.isBlank()) {
            Toast.makeText(requireContext(), "请填写出库原因", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).stockOut(
                    StockOutRequest(
                        item_id = itemId,
                        quantity = qty,
                        remark = reason,
                    )
                )
                Toast.makeText(requireContext(), "已出库", Toast.LENGTH_SHORT).show()
                binding.outQty.setText("")
                binding.outRemark.setText("")
                refresh()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "出库失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "item_id"
        const val ARG_NAME = "name"
        const val ARG_SPEC = "spec"
        const val ARG_UNIT = "unit"
        const val ARG_STOCK = "stock"
        const val ARG_COST = "cost"

        fun args(item: StockItem): Bundle = Bundle().apply {
            putInt(ARG_ID, item.id)
            putString(ARG_NAME, item.name)
            putString(ARG_SPEC, item.spec)
            putString(ARG_UNIT, item.unitLabel())
            putInt(ARG_STOCK, item.stock_qty)
            putDouble(ARG_COST, item.cost_price)
        }
    }
}
