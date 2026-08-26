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
import com.hairclinic.app.data.StockItem
import com.hairclinic.app.databinding.FragmentBillingBinding
import com.hairclinic.app.databinding.ItemBillingProductBinding
import com.hairclinic.app.databinding.ItemBillingProjectBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class BillingFragment : Fragment() {
    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!
    private var projects: List<Project> = emptyList()
    private var products: List<StockItem> = emptyList()
    private val projectRows = mutableListOf<ItemBillingProjectBinding>()
    private val productRows = mutableListOf<ItemBillingProductBinding>()
    private var selectedCustomer: Customer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.submitBtn.setOnClickListener { submit() }
        binding.addProjectBtn.setOnClickListener { addProjectRow() }
        binding.addProductBtn.setOnClickListener { addProductRow() }
        if (!bindCustomerFromArgs()) {
            Toast.makeText(requireContext(), "请从客户列表进入开单", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }
        load()
    }

    private fun bindCustomerFromArgs(): Boolean {
        val id = arguments?.getInt(ARG_CUSTOMER_ID, -1) ?: -1
        if (id <= 0) return false
        val name = arguments?.getString(ARG_CUSTOMER_NAME).orEmpty()
        val phone = arguments?.getString(ARG_CUSTOMER_PHONE).orEmpty()
        selectedCustomer = Customer(
            id = id,
            name = name.ifBlank { "客户$id" },
            phone = phone.ifBlank { "-" },
        )
        binding.customerText.text = "${selectedCustomer!!.name}（${selectedCustomer!!.phone}）"
        return true
    }

    private fun projectNames(): List<String> = projects.map { it.name }

    private fun productNames(): List<String> = products.map { it.name }

    private fun findProjectByName(name: String): Project? =
        projects.firstOrNull { it.name == name }

    private fun findProductByName(name: String): StockItem? =
        products.firstOrNull { it.name == name }

    private fun projectMetaText(p: Project): String {
        val meds = p.medicineText()
        return if (meds.isBlank()) {
            "¥${"%.2f".format(p.price)}"
        } else {
            "¥${"%.2f".format(p.price)} · $meds"
        }
    }

    private fun productMetaText(p: StockItem): String {
        val price = "参考 ¥${"%.2f".format(p.cost_price)}"
        return "$price · ${p.stockText()}"
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

    private fun addProductRow(preset: StockItem? = null) {
        if (products.isEmpty()) {
            Toast.makeText(requireContext(), "暂无可用产品", Toast.LENGTH_SHORT).show()
            return
        }
        val row = ItemBillingProductBinding.inflate(layoutInflater, binding.productBox, false)
        binding.productBox.addView(row.root)
        productRows += row

        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, productNames())
        row.inputProduct.setAdapter(adapter)
        row.inputProduct.setOnItemClickListener { _, _, position, _ ->
            val name = adapter.getItem(position) ?: return@setOnItemClickListener
            val product = findProductByName(name)
            row.productMeta.text = product?.let { productMetaText(it) }.orEmpty()
            updateTotal(syncDeal = true)
        }
        row.productQty.doAfterTextChanged { updateTotal(syncDeal = true) }
        row.removeBtn.setOnClickListener {
            binding.productBox.removeView(row.root)
            productRows.remove(row)
            refreshProductEmpty()
            updateTotal(syncDeal = true)
        }

        val initial = preset ?: products.first()
        row.inputProduct.setText(initial.name, false)
        row.productMeta.text = productMetaText(initial)
        refreshProductEmpty()
        updateTotal(syncDeal = true)
    }

    private fun refreshProjectEmpty() {
        binding.projectEmpty.isVisible = projectRows.isEmpty()
    }

    private fun refreshProductEmpty() {
        binding.productEmpty.isVisible = productRows.isEmpty()
    }

    private fun collectItems(): List<OrderItemIn>? {
        if (projectRows.isEmpty() && productRows.isEmpty()) {
            Toast.makeText(requireContext(), "请添加项目或产品", Toast.LENGTH_SHORT).show()
            return null
        }
        val items = mutableListOf<OrderItemIn>()
        val seenProjects = mutableSetOf<Int>()
        for (row in projectRows) {
            val name = row.inputProject.text?.toString()?.trim().orEmpty()
            val project = findProjectByName(name)
            if (project?.id == null) {
                Toast.makeText(requireContext(), "请选择项目", Toast.LENGTH_SHORT).show()
                return null
            }
            if (!seenProjects.add(project.id)) {
                Toast.makeText(requireContext(), "同一项目请合并数量，勿重复添加", Toast.LENGTH_SHORT).show()
                return null
            }
            val qty = row.projectQty.text?.toString()?.toIntOrNull() ?: 0
            if (qty <= 0) {
                Toast.makeText(requireContext(), "请填写有效项目数量", Toast.LENGTH_SHORT).show()
                return null
            }
            items += OrderItemIn(project_id = project.id, quantity = qty)
        }
        val seenProducts = mutableSetOf<Int>()
        for (row in productRows) {
            val name = row.inputProduct.text?.toString()?.trim().orEmpty()
            val product = findProductByName(name)
            if (product == null) {
                Toast.makeText(requireContext(), "请选择产品", Toast.LENGTH_SHORT).show()
                return null
            }
            if (!seenProducts.add(product.id)) {
                Toast.makeText(requireContext(), "同一产品请合并数量，勿重复添加", Toast.LENGTH_SHORT).show()
                return null
            }
            val qty = row.productQty.text?.toString()?.toIntOrNull() ?: 0
            if (qty <= 0) {
                Toast.makeText(requireContext(), "请填写有效产品数量", Toast.LENGTH_SHORT).show()
                return null
            }
            items += OrderItemIn(item_id = product.id, quantity = qty)
        }
        return items
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                projects = api.listProjects(activeOnly = true)
                products = api.listInventory()
                binding.projectBox.removeAllViews()
                binding.productBox.removeAllViews()
                projectRows.clear()
                productRows.clear()
                refreshProjectEmpty()
                refreshProductEmpty()
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
        for (row in productRows) {
            val name = row.inputProduct.text?.toString()?.trim().orEmpty()
            val product = findProductByName(name) ?: continue
            val qty = row.productQty.text?.toString()?.toIntOrNull() ?: 1
            total += product.cost_price * qty
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
        val customer = selectedCustomer
        if (customer?.id == null) {
            Toast.makeText(requireContext(), "请从客户列表进入开单", Toast.LENGTH_SHORT).show()
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
                        customer_id = customer.id,
                        items = items,
                        deal_price = dealPrice,
                        remark = binding.remark.text?.toString()?.trim().orEmpty(),
                    )
                )
                Toast.makeText(requireContext(), "订单已生成 ${order.order_no}", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
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
