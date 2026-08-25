package com.hairclinic.app.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.BadgeTone
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import kotlinx.coroutines.launch

class OrdersFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    private val statusFilters = listOf(
        "全部" to null,
        "待付款" to "PENDING",
        "已付款" to "PAID",
        "已完成" to "DONE",
        "已取消" to "CANCELLED",
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "订单"
        binding.pageSubtitle.text = "消费记录与状态"
        binding.addBtn.isVisible = false
        binding.searchInput.isVisible = false
        binding.filterSpinner.isVisible = true
        binding.searchBtn.text = "刷新"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.searchBtn.setOnClickListener { load() }

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner,
            statusFilters.map { it.first },
        )
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner)
        binding.filterSpinner.adapter = spinnerAdapter
        binding.filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                load()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val position = binding.filterSpinner.selectedItemPosition.coerceAtLeast(0)
                val status = statusFilters.getOrNull(position)?.second
                val list = ApiClient.get(requireContext()).listOrders(status)
                adapter.submit(list.map { o ->
                    val detail = o.items.joinToString(" · ") { "${it.project_name}×${it.quantity}" }
                    val customer = listOfNotNull(o.customer_name, o.customer_phone)
                        .filter { it.isNotBlank() }
                        .joinToString("  ")
                    val creator = o.creatorText()
                    Item(
                        title = o.order_no,
                        subtitle = buildString {
                            append(customer.ifBlank { "—" })
                            append("\n¥${"%.2f".format(o.total_amount)} · ${detail.ifBlank { "无项目" }}")
                            if (creator.isNotBlank()) append("\n下单账号 $creator")
                        },
                        badge = statusLabel(o.status),
                        badgeTone = statusTone(o.status),
                        clickable = false,
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无订单"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun statusLabel(s: String): String = when (s) {
        "PENDING" -> "待付款"
        "PAID" -> "已付款"
        "DONE" -> "已完成"
        "CANCELLED" -> "已取消"
        else -> s
    }

    private fun statusTone(s: String): BadgeTone = when (s) {
        "PENDING" -> BadgeTone.GOLD
        "PAID" -> BadgeTone.SUCCESS
        "DONE" -> BadgeTone.SUCCESS
        "CANCELLED" -> BadgeTone.WARN
        else -> BadgeTone.MUTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
