package com.hairclinic.app.ui.customers

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
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.data.formatVisitTime
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.databinding.ItemSimpleBinding
import kotlinx.coroutines.launch

enum class BadgeTone { SUCCESS, GOLD, WARN, MUTED }

data class Item(
    val title: String,
    val subtitle: String = "",
    val badge: String = "",
    val badgeTone: BadgeTone = BadgeTone.SUCCESS,
    val clickable: Boolean = true,
    val onClick: () -> Unit = {},
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
        val ctx = holder.itemView.context
        holder.binding.title.text = item.title
        holder.binding.subtitle.text = item.subtitle
        holder.binding.subtitle.isVisible = item.subtitle.isNotBlank()
        holder.binding.badge.text = item.badge
        holder.binding.badge.isVisible = item.badge.isNotBlank()
        if (item.badge.isNotBlank()) {
            val (bg, color) = when (item.badgeTone) {
                BadgeTone.SUCCESS -> R.drawable.bg_badge to R.color.pine
                BadgeTone.GOLD -> R.drawable.bg_badge_gold to R.color.gold
                BadgeTone.WARN -> R.drawable.bg_badge_warn to R.color.cinnabar
                BadgeTone.MUTED -> R.drawable.bg_badge_muted to R.color.ink_soft
            }
            holder.binding.badge.setBackgroundResource(bg)
            holder.binding.badge.setTextColor(ContextCompat.getColor(ctx, color))
        }
        holder.binding.chevron.isVisible = item.clickable
        holder.binding.root.isClickable = item.clickable
        holder.binding.root.isFocusable = item.clickable
        holder.binding.root.setOnClickListener(if (item.clickable) ({ item.onClick() }) else null)
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
        binding.pageSubtitle.text = "资料、查询与回访"
        binding.addBtn.text = "添加客户"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.searchBtn.setOnClickListener { load() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                load()
                true
            } else {
                false
            }
        }
        binding.addBtn.setOnClickListener { openEditor(null) }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) load()
    }

    private fun openEditor(customer: Customer?) {
        findNavController().navigate(R.id.customerEditFragment, CustomerEditFragment.args(customer))
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val q = binding.searchInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                val list = ApiClient.get(requireContext()).listCustomers(q)
                adapter.submit(list.map { c ->
                    val visitLine = when {
                        c.visit_count > 0 -> "回访 ${formatVisitTime(c.last_visited_at)} · ${c.visit_count} 次"
                        else -> "尚无回访"
                    }
                    Item(
                        title = c.name,
                        subtitle = "${c.phone} · 生日 ${c.birthday ?: "-"}\n$visitLine",
                        badge = c.gender.ifBlank { "未知" },
                        badgeTone = if (c.gender == "女") BadgeTone.GOLD else BadgeTone.SUCCESS,
                        onClick = { openEditor(c) },
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无客户，点击下方添加"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
