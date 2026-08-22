package com.hairclinic.app.ui.billing

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.data.OrderCreate
import com.hairclinic.app.data.OrderItemIn
import com.hairclinic.app.data.Project
import com.hairclinic.app.databinding.FragmentBillingBinding
import kotlinx.coroutines.launch

class BillingFragment : Fragment() {
    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!
    private var customers: List<Customer> = emptyList()
    private var projects: List<Project> = emptyList()
    private val checks = mutableListOf<Pair<CheckBox, EditText>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.submitBtn.setOnClickListener { submit() }
        load()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                customers = api.listCustomers()
                projects = api.listProjects(activeOnly = true)
                binding.customerSpinner.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    customers.map { "${it.name}（${it.phone}）" },
                )
                binding.projectBox.removeAllViews()
                checks.clear()
                val ink = ContextCompat.getColor(requireContext(), R.color.ink)
                val inkSoft = ContextCompat.getColor(requireContext(), R.color.ink_soft)
                projects.forEach { p ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_input)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                        lp.bottomMargin = dp(8)
                        layoutParams = lp
                    }
                    val top = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    val cb = CheckBox(requireContext()).apply {
                        text = p.name
                        setTextColor(ink)
                        textSize = 15f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnCheckedChangeListener { _, _ -> updateTotal() }
                    }
                    val qty = EditText(requireContext()).apply {
                        setText("1")
                        hint = "数量"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setTextColor(ink)
                        setHintTextColor(inkSoft)
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip)
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        minWidth = dp(64)
                        setOnFocusChangeListener { _, _ -> updateTotal() }
                    }
                    top.addView(cb)
                    top.addView(qty)
                    val meta = TextView(requireContext()).apply {
                        text = "¥${"%.2f".format(p.price)} · ${p.graft_count} 单位"
                        setTextColor(inkSoft)
                        textSize = 12f
                        setPadding(dp(4), dp(2), 0, 0)
                    }
                    row.addView(top)
                    row.addView(meta)
                    binding.projectBox.addView(row)
                    checks += cb to qty
                }
                updateTotal()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTotal() {
        var total = 0.0
        checks.forEachIndexed { index, (cb, qty) ->
            if (cb.isChecked) {
                total += projects[index].price * (qty.text.toString().toIntOrNull() ?: 1)
            }
        }
        binding.totalText.text = "合计：¥${"%.2f".format(total)}"
    }

    private fun submit() {
        if (customers.isEmpty()) {
            Toast.makeText(requireContext(), "请先录入客户", Toast.LENGTH_SHORT).show()
            return
        }
        val items = mutableListOf<OrderItemIn>()
        checks.forEachIndexed { index, (cb, qty) ->
            if (cb.isChecked) {
                items += OrderItemIn(projects[index].id!!, qty.text.toString().toIntOrNull() ?: 1)
            }
        }
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "请选择项目", Toast.LENGTH_SHORT).show()
            return
        }
        val customer = customers[binding.customerSpinner.selectedItemPosition]
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val order = ApiClient.get(requireContext()).createOrder(
                    OrderCreate(
                        customer_id = customer.id!!,
                        items = items,
                        remark = binding.remark.text?.toString()?.trim().orEmpty(),
                    )
                )
                Toast.makeText(requireContext(), "订单已生成 ${order.order_no}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "开单失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
