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
import com.hairclinic.app.data.Project
import com.hairclinic.app.data.StockMoveIn
import com.hairclinic.app.databinding.FragmentStockBinding
import com.hairclinic.app.databinding.ItemStockMoveBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class StockFragment : Fragment() {
    private var _binding: FragmentStockBinding? = null
    private val binding get() = _binding!!
    private var projectId: Int = -1
    private var unit: String = "个"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        projectId = arguments?.getInt(ARG_ID, -1) ?: -1
        unit = arguments?.getString(ARG_UNIT).orEmpty().ifBlank { "个" }
        binding.pageTitle.text = arguments?.getString(ARG_NAME).orEmpty().ifBlank { "库存" }
        binding.stockQtyText.text = "库存 ${arguments?.getInt(ARG_STOCK) ?: 0} $unit"
        binding.costPriceText.text = "进货价 ¥${ProjectEditFragment.formatPrice(arguments?.getDouble(ARG_COST) ?: 0.0)}"
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.inBtn.setOnClickListener {
            findNavController().navigate(R.id.inboundFragment, InboundFragment.args(projectId))
        }
        binding.outBtn.setOnClickListener { outbound() }
        refresh()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                val project = api.getProject(projectId)
                bindProject(project)
                val moves = api.listStockMovements(projectId)
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
                    row.moveQty.text = "${if (inbound) "+" else "-"}${m.quantity} $unit"
                    row.moveTime.text = m.timeText()
                    val cost = if (m.unit_cost > 0) "¥${ProjectEditFragment.formatPrice(m.unit_cost)}" else ""
                    val name = m.project_name.ifBlank { "" }
                    row.moveMeta.text = listOf(name, cost, m.remark).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "无备注" }
                    binding.moveBox.addView(row.root)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindProject(project: Project) {
        unit = project.unitLabel()
        binding.pageTitle.text = project.name
        binding.stockQtyText.text = project.stockText()
        binding.costPriceText.text = project.costText()
    }

    private fun outbound() {
        val qty = binding.outQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            Toast.makeText(requireContext(), "请填写出货数量", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).stockOut(
                    StockMoveIn(
                        project_id = projectId,
                        quantity = qty,
                        remark = binding.outRemark.text?.toString()?.trim().orEmpty(),
                    )
                )
                Toast.makeText(requireContext(), "已出货", Toast.LENGTH_SHORT).show()
                binding.outQty.setText("")
                binding.outRemark.setText("")
                refresh()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "出货失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "project_id"
        const val ARG_NAME = "name"
        const val ARG_UNIT = "unit"
        const val ARG_STOCK = "stock"
        const val ARG_COST = "cost"

        fun args(project: Project): Bundle = Bundle().apply {
            putInt(ARG_ID, project.id ?: -1)
            putString(ARG_NAME, project.name)
            putString(ARG_UNIT, project.unitLabel())
            putInt(ARG_STOCK, project.stock_qty)
            putDouble(ARG_COST, project.cost_price)
        }
    }
}
