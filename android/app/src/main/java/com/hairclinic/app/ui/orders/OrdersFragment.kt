package com.hairclinic.app.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import kotlinx.coroutines.launch

class OrdersFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "订单"
        binding.pageSubtitle.text = "消费记录与状态"
        binding.addBtn.isVisible = false
        binding.searchInput.hint = "状态过滤：PAID / DONE，空为全部"
        binding.searchBtn.text = "刷新"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.searchBtn.setOnClickListener { load() }
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val status = binding.searchInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                val list = ApiClient.get(requireContext()).listOrders(status)
                adapter.submit(list.map { o ->
                    val detail = o.items.joinToString(" · ") { "${it.project_name}×${it.quantity}" }
                    Item(
                        title = "${o.order_no}  ·  ¥${"%.2f".format(o.total_amount)}",
                        subtitle = "${o.customer_name ?: ""} ${o.customer_phone ?: ""}\n${statusLabel(o.status)} · $detail",
                        onClick = {},
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
