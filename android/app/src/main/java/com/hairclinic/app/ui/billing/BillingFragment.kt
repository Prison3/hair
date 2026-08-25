package com.hairclinic.app.ui.billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.data.OrderCreate
import com.hairclinic.app.data.OrderItemIn
import com.hairclinic.app.data.Project
import com.hairclinic.app.databinding.FragmentBillingBinding
import com.hairclinic.app.databinding.ItemBillingProjectBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BillingFragment : Fragment() {
    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!
    private var customers: List<Customer> = emptyList()
    private var projects: List<Project> = emptyList()
    private val projectRows = mutableListOf<ItemBillingProjectBinding>()
    private var selectedCustomer: Customer? = null
    private var searchJob: Job? = null
    private var customerAdapter: ArrayAdapter<String>? = null
    private var applyingPrefill = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.submitBtn.setOnClickListener { submit() }
        binding.addProjectBtn.setOnClickListener { addProjectRow() }
        setupCustomerSearch()
        applyPrefillCustomer()
        load()
    }

    private fun applyPrefillCustomer() {
        val id = arguments?.getInt(ARG_CUSTOMER_ID, -1) ?: -1
        if (id <= 0) return
        val name = arguments?.getString(ARG_CUSTOMER_NAME).orEmpty()
        val phone = arguments?.getString(ARG_CUSTOMER_PHONE).orEmpty()
        val customer = Customer(id = id, name = name.ifBlank { "客户$id" }, phone = phone.ifBlank { "-" })
        selectedCustomer = customer
        applyingPrefill = true
        binding.inputCustomer.setText(customerLabel(customer), false)
        applyingPrefill = false
        binding.customerHint.text = "已选客户，可改搜其他客户"
    }

    private fun setupCustomerSearch() {
        customerAdapter = ArrayAdapter(requireContext(), R.layout.item_spinner, mutableListOf())
        binding.inputCustomer.setAdapter(customerAdapter)
        binding.inputCustomer.threshold = 1
        binding.inputCustomer.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            selectedCustomer = customers.firstOrNull { customerLabel(it) == label }
        }
        binding.inputCustomer.doAfterTextChanged { editable ->
            if (applyingPrefill) return@doAfterTextChanged
            val text = editable?.toString().orEmpty()
            if (selectedCustomer != null && customerLabel(selectedCustomer!!) != text.trim()) {
                selectedCustomer = null
            }
            scheduleCustomerSearch(text.trim())
        }
    }

    private fun scheduleCustomerSearch(q: String) {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(220)
            try {
                val list = ApiClient.get(requireContext()).listCustomers(q.ifBlank { null })
                customers = list
                val labels = list.map { customerLabel(it) }
                customerAdapter?.clear()
                customerAdapter?.addAll(labels)
                customerAdapter?.notifyDataSetChanged()
                if (q.isNotBlank() && binding.inputCustomer.hasFocus()) {
                    binding.inputCustomer.showDropDown()
                }
                selectedCustomer?.let { selected ->
                    if (list.none { it.id == selected.id }) {
                        customers = listOf(selected) + list
                        customerAdapter?.clear()
                        customerAdapter?.addAll(customers.map { customerLabel(it) })
                        customerAdapter?.notifyDataSetChanged()
                    }
                }
                binding.customerHint.text = when {
                    selectedCustomer != null -> "已选 ${customerLabel(selectedCustomer!!)}"
                    list.isEmpty() && q.isBlank() -> "暂无客户，请先在「客户」页录入"
                    list.isEmpty() -> "未找到匹配客户"
                    else -> "支持按姓名、手机号模糊匹配 · ${list.size} 人"
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "客户搜索失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun customerLabel(c: Customer): String = "${c.name}（${c.phone}）"

    private fun resolveCustomer(): Customer? {
        selectedCustomer?.let { return it }
        val text = binding.inputCustomer.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return null
        customers.firstOrNull { customerLabel(it) == text }?.let { return it }
        val matches = customers.filter {
            it.name.contains(text, ignoreCase = true) || it.phone.contains(text)
        }
        return matches.singleOrNull()
    }

    private fun projectNames(): List<String> = projects.map { it.name }

    private fun findProjectByName(name: String): Project? =
        projects.firstOrNull { it.name == name }

    private fun projectMetaText(p: Project): String {
        val meds = p.medicineText()
        return if (meds.isBlank()) {
            "¥${"%.2f".format(p.price)}"
        } else {
            "¥${"%.2f".format(p.price)} · $meds"
        }
    }

    private fun addProjectRow(preset: Project? = null) {
        if (projects.isEmpty()) {
            Toast.makeText(requireContext(), "暂无可用项目", Toast.LENGTH_SHORT).show()
            return
        }
        val row = ItemBillingProjectBinding.inflate(layoutInflater, binding.projectBox, false)
        binding.projectBox.addView(row.root)
        projectRows += row

        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, projectNames())
        row.inputProject.setAdapter(adapter)
        row.inputProject.setOnItemClickListener { _, _, position, _ ->
            val name = adapter.getItem(position) ?: return@setOnItemClickListener
            val project = findProjectByName(name)
            row.projectMeta.text = project?.let { projectMetaText(it) }.orEmpty()
            updateTotal(syncDeal = true)
        }
        row.projectQty.doAfterTextChanged { updateTotal(syncDeal = true) }
        row.removeBtn.setOnClickListener {
            binding.projectBox.removeView(row.root)
            projectRows.remove(row)
            refreshProjectEmpty()
            updateTotal(syncDeal = true)
        }

        val initial = preset ?: projects.first()
        row.inputProject.setText(initial.name, false)
        row.projectMeta.text = projectMetaText(initial)
        refreshProjectEmpty()
        updateTotal(syncDeal = true)
    }

    private fun refreshProjectEmpty() {
        binding.projectEmpty.isVisible = projectRows.isEmpty()
    }

    private fun collectItems(): List<OrderItemIn>? {
        if (projectRows.isEmpty()) {
            Toast.makeText(requireContext(), "请添加项目", Toast.LENGTH_SHORT).show()
            return null
        }
        val items = mutableListOf<OrderItemIn>()
        val seen = mutableSetOf<Int>()
        for (row in projectRows) {
            val name = row.inputProject.text?.toString()?.trim().orEmpty()
            val project = findProjectByName(name)
            if (project?.id == null) {
                Toast.makeText(requireContext(), "请选择项目", Toast.LENGTH_SHORT).show()
                return null
            }
            if (!seen.add(project.id)) {
                Toast.makeText(requireContext(), "同一项目请合并数量，勿重复添加", Toast.LENGTH_SHORT).show()
                return null
            }
            val qty = row.projectQty.text?.toString()?.toIntOrNull() ?: 0
            if (qty <= 0) {
                Toast.makeText(requireContext(), "请填写有效数量", Toast.LENGTH_SHORT).show()
                return null
            }
            items += OrderItemIn(project.id, qty)
        }
        return items
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                projects = api.listProjects(activeOnly = true)
                if (selectedCustomer == null) {
                    scheduleCustomerSearch("")
                } else {
                    scheduleCustomerSearch(selectedCustomer!!.name)
                }
                binding.projectBox.removeAllViews()
                projectRows.clear()
                refreshProjectEmpty()
                if (projects.isNotEmpty()) {
                    addProjectRow()
                }
                updateTotal(syncDeal = true)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun catalogTotal(): Double {
        var total = 0.0
        for (row in projectRows) {
            val name = row.inputProject.text?.toString()?.trim().orEmpty()
            val project = findProjectByName(name) ?: continue
            val qty = row.projectQty.text?.toString()?.toIntOrNull() ?: 1
            total += project.price * qty
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
        val customer = resolveCustomer()
        if (customer == null) {
            Toast.makeText(requireContext(), "请选择客户（可输入姓名或手机号搜索）", Toast.LENGTH_SHORT).show()
            return
        }
        val items = collectItems() ?: return
        val dealPrice = binding.dealPrice.text?.toString()?.toDoubleOrNull()
        if (dealPrice == null || dealPrice < 0) {
            Toast.makeText(requireContext(), "请填写成交价格", Toast.LENGTH_SHORT).show()
            return
        }
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
                if ((arguments?.getInt(ARG_CUSTOMER_ID, -1) ?: -1) > 0) {
                    findNavController().navigateUp()
                    return@launch
                }
                selectedCustomer = null
                binding.inputCustomer.setText("")
                binding.projectBox.removeAllViews()
                projectRows.clear()
                if (projects.isNotEmpty()) addProjectRow()
                binding.remark.setText("")
                updateTotal(syncDeal = true)
                scheduleCustomerSearch("")
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
        searchJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_CUSTOMER_ID = "customer_id"
        const val ARG_CUSTOMER_NAME = "customer_name"
        const val ARG_CUSTOMER_PHONE = "customer_phone"

        fun args(customerId: Int, name: String, phone: String): Bundle = Bundle().apply {
            putInt(ARG_CUSTOMER_ID, customerId)
            putString(ARG_CUSTOMER_NAME, name)
            putString(ARG_CUSTOMER_PHONE, phone)
        }
    }
}
