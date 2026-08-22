package com.hairclinic.app.ui.customers

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
import androidx.recyclerview.widget.RecyclerView
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.databinding.ItemSimpleBinding
import kotlinx.coroutines.launch

data class Item(
    val title: String,
    val subtitle: String = "",
    val onClick: () -> Unit,
)

class SimpleAdapter : RecyclerView.Adapter<SimpleAdapter.VH>() {
    private val items = mutableListOf<Item>()

    fun submit(data: List<Item>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.title.text = item.title
        holder.binding.subtitle.text = item.subtitle
        holder.binding.subtitle.isVisible = item.subtitle.isNotBlank()
        holder.binding.root.setOnClickListener { item.onClick() }
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemSimpleBinding) : RecyclerView.ViewHolder(binding.root)
}

class CustomersFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "客户"
        binding.pageSubtitle.text = "录入与查询客户资料"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.searchBtn.setOnClickListener { load() }
        binding.addBtn.setOnClickListener { showEditor(null) }
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val q = binding.searchInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                val list = ApiClient.get(requireContext()).listCustomers(q)
                adapter.submit(list.map { c ->
                    Item(
                        title = c.name,
                        subtitle = "${c.phone} · ${c.gender.ifBlank { "未知" }} · ${c.age ?: "-"}岁\n${c.notes.ifBlank { "无备注" }}",
                        onClick = { showEditor(c) },
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无客户，点击右上角新建"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditor(customer: Customer?) {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        fun field(hint: String, value: String) = EditText(requireContext()).also {
            it.hint = hint
            it.setText(value)
            box.addView(it)
        }
        val name = field("姓名", customer?.name.orEmpty())
        val phone = field("手机", customer?.phone.orEmpty())
        val gender = field("性别", customer?.gender.orEmpty())
        val age = field("年龄", customer?.age?.toString().orEmpty())
        val notes = field("备注", customer?.notes.orEmpty())

        AlertDialog.Builder(requireContext())
            .setTitle(if (customer == null) "新建客户" else "编辑客户")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val body = Customer(
                            id = customer?.id,
                            name = name.text.toString().trim(),
                            phone = phone.text.toString().trim(),
                            gender = gender.text.toString().trim(),
                            age = age.text.toString().toIntOrNull(),
                            notes = notes.text.toString().trim(),
                        )
                        val api = ApiClient.get(requireContext())
                        if (customer?.id == null) api.createCustomer(body) else api.updateCustomer(customer.id!!, body)
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
