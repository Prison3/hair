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
import com.hairclinic.app.ui.projects.ProjectEditFragment
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
                    val meds = p.medicineText()
                    row.projectMeta.text = if (meds.isBlank()) {
                        "¥${"%.2f".format(p.price)}"
                    } else {
                        "¥${"%.2f".format(p.price)} · $meds"
                    }
                    row.projectCheck.setOnCheckedChangeListener { _, _ -> updateTotal(syncDeal = true) }
                    row.projectQty.doAfterTextChanged { updateTotal(syncDeal = true) }
                    binding.projectBox.addView(row.root)
                    checks += row.projectCheck to row.projectQty
                }
                updateTotal(syncDeal = true)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun catalogTotal(): Double {
        var total = 0.0
        checks.forEachIndexed { index, (cb, qty) ->
            if (cb.isChecked) {
                total += projects[index].price * (qty.text.toString().toIntOrNull() ?: 1)
            }
        }
        return total
    }

    private fun updateTotal(syncDeal: Boolean) {
        val total = catalogTotal()
        binding.catalogTotalText.text = "参考合计 ¥${"%.2f".format(total)}"
        if (syncDeal) {
            binding.dealPrice.setText(
                if (total > 0) ProjectEditFragment.formatPrice(total) else "",
            )
        }
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
        val dealPrice = binding.dealPrice.text?.toString()?.toDoubleOrNull()
        if (dealPrice == null || dealPrice < 0) {
            Toast.makeText(requireContext(), "请填写成交价格", Toast.LENGTH_SHORT).show()
            return
        }
        val customer = customers[binding.customerSpinner.selectedItemPosition]
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val order = ApiClient.get(requireContext()).createOrder(
                    OrderCreate(
                        customer_id = customer.id!!,
                        items = items,
                        deal_price = dealPrice,
                        remark = binding.remark.text?.toString()?.trim().orEmpty(),
                    )
                )
                Toast.makeText(requireContext(), "订单已生成 ${order.order_no}", Toast.LENGTH_LONG).show()
                checks.forEach { (cb, qty) ->
                    cb.isChecked = false
                    qty.setText("1")
                }
                binding.remark.setText("")
                updateTotal(syncDeal = true)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    ProjectEditFragment.apiError(e, "开单失败"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
