package com.hairclinic.app.ui.billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.data.OrderCreate
import com.hairclinic.app.data.OrderItemIn
import com.hairclinic.app.data.Project
import com.hairclinic.app.databinding.FragmentBillingBinding
import com.hairclinic.app.databinding.ItemBillingProjectBinding
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

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                customers = api.listCustomers()
                projects = api.listProjects(activeOnly = true)
                val labels = if (customers.isEmpty()) {
                    listOf("暂无客户，请先在「客户」页录入")
                } else {
                    customers.map { "${it.name}（${it.phone}）" }
                }
                val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.item_spinner, labels)
                spinnerAdapter.setDropDownViewResource(R.layout.item_spinner)
                binding.customerSpinner.adapter = spinnerAdapter
                binding.projectBox.removeAllViews()
                checks.clear()
                projects.forEach { p ->
                    val row = ItemBillingProjectBinding.inflate(layoutInflater, binding.projectBox, false)
                    row.projectCheck.text = p.name
                    row.projectMeta.text = buildString {
                        append("¥${"%.2f".format(p.price)} · ${p.specText()}")
                        if (p.isPhysical()) append(" · ${p.stockText()}")
                    }
                    row.projectCheck.setOnCheckedChangeListener { _, _ -> updateTotal() }
                    row.projectQty.doAfterTextChanged { updateTotal() }
                    binding.projectBox.addView(row.root)
                    checks += row.projectCheck to row.projectQty
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
        binding.totalText.text = "¥${"%.2f".format(total)}"
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
                checks.forEach { (cb, qty) ->
                    cb.isChecked = false
                    qty.setText("1")
                }
                binding.remark.setText("")
                updateTotal()
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
