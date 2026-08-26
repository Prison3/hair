package com.hairclinic.app.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Order
import com.hairclinic.app.data.StockMovement
import com.hairclinic.app.data.formatVisitTime
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.databinding.ItemInboundRowBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

/** 出入库共用 stock_movements，用 kind=IN/OUT 区分。 */
class InboundListFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = MovementRecordAdapter(
        onDelete = { confirmDelete(it) },
        onOrderClick = { openOrderDetail(it) },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "流水"
        binding.pageSubtitle.text = "类型 / 编号 / 产品 / 原因分列展示"
        binding.searchInput.hint = "搜索产品名 / 编号 / 原因"
        binding.tableHeader.isVisible = false
        binding.setupInventoryTabs(this, InventorySection.MOVEMENTS)
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
                    kind = null,
                    q = q,
                    limit = 200,
                )
                adapter.submit(list)
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无出入库记录，开单或入库后会出现在这里"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(record: StockMovement) {
        if (record.kind != "IN") {
            Toast.makeText(requireContext(), "出库记录不可删除", Toast.LENGTH_SHORT).show()
            return
        }
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

    private fun openOrderDetail(orderNo: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val order = ApiClient.get(requireContext()).getOrderByNo(orderNo)
                showOrderDetail(order)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    ProjectEditFragment.apiError(e, "订单加载失败"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun showOrderDetail(order: Order) {
        val itemsText = order.items.joinToString("\n") {
            "· ${it.project_name} × ${it.quantity}（标价 ¥${"%.2f".format(it.unit_price)}）"
        }.ifBlank { "无项目" }
        val status = when (order.status) {
            "PENDING" -> "待付款"
            "PAID" -> "已付款"
            "DONE" -> "已完成"
            "CANCELLED" -> "已取消"
            else -> order.status
        }
        val message = buildString {
            appendLine("订单号：${order.order_no}")
            appendLine("状态：$status")
            appendLine("客户：${listOfNotNull(order.customer_name, order.customer_phone).joinToString(" ").ifBlank { "—" }}")
            appendLine("成交金额：¥${"%.2f".format(order.total_amount)}")
            appendLine("下单时间：${formatVisitTime(order.created_at).ifBlank { "—" }}")
            val creator = order.creatorText()
            appendLine("下单账号：${creator.ifBlank { "—" }}")
            if (order.remark.isNotBlank()) appendLine("备注：${order.remark}")
            appendLine()
            appendLine("项目明细：")
            append(itemsText)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("订单详情")
            .setMessage(message.trim())
            .setPositiveButton("关闭", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MovementRecordAdapter(
    private val onDelete: (StockMovement) -> Unit,
    private val onOrderClick: (String) -> Unit,
) : RecyclerView.Adapter<MovementRecordAdapter.VH>() {
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
        val inbound = item.kind == "IN"
        val pine = ContextCompat.getColor(holder.itemView.context, R.color.pine)
        val warn = ContextCompat.getColor(holder.itemView.context, R.color.cinnabar)
        val muted = ContextCompat.getColor(holder.itemView.context, R.color.ink_soft)

        holder.binding.colKind.text = item.kindLabel()
        holder.binding.colKind.setTextColor(if (inbound) pine else warn)
        holder.binding.colKind.setBackgroundResource(
            if (inbound) R.drawable.bg_badge else R.drawable.bg_badge_warn
        )
        holder.binding.colNo.text = item.inboundNoText()
        holder.binding.colProduct.text = item.item_name
        holder.binding.colQty.text = "${if (inbound) "+" else "-"}${item.qtyText()}"
        holder.binding.colQty.setTextColor(if (inbound) pine else warn)

        val orderNo = item.linkedOrderNo()
        val reason = item.reasonText()
        if (!orderNo.isNullOrBlank()) {
            holder.binding.colReason.text = orderNo
            holder.binding.colReason.setTextColor(pine)
            holder.binding.colReason.paint.isUnderlineText = true
            holder.binding.colReason.isClickable = true
            holder.binding.colReason.isFocusable = true
            holder.binding.colReason.setOnClickListener { onOrderClick(orderNo) }
        } else {
            holder.binding.colReason.text = reason.ifBlank { "—" }
            holder.binding.colReason.setTextColor(muted)
            holder.binding.colReason.paint.isUnderlineText = false
            holder.binding.colReason.isClickable = false
            holder.binding.colReason.setOnClickListener(null)
        }

        holder.binding.deleteBtn.isVisible = inbound
        holder.binding.deleteBtn.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemInboundRowBinding) : RecyclerView.ViewHolder(binding.root)
}
