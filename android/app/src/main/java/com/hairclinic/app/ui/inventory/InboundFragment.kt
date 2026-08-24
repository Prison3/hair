package com.hairclinic.app.ui.inventory

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Project
import com.hairclinic.app.data.StockMoveIn
import com.hairclinic.app.databinding.FragmentInboundBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch
import java.util.Calendar

class InboundFragment : Fragment() {
    private var _binding: FragmentInboundBinding? = null
    private val binding get() = _binding!!
    private var products: List<Project> = emptyList()
    private var selected: Project? = null
    private var inboundDate: String = today()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInboundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.inputDate.text = inboundDate
        binding.inputDate.setOnClickListener { pickDate() }
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.submitBtn.setOnClickListener { submit() }
        binding.productSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selected = products.getOrNull(position)
                fillPriceHint()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selected = null
            }
        }
        loadProducts()
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                products = ApiClient.get(requireContext()).listInventory()
                val labels = if (products.isEmpty()) {
                    listOf("暂无产品，请先在「项目」里添加")
                } else {
                    products.map { "${it.name}（库存 ${it.stock_qty} ${it.unitLabel()}）" }
                }
                binding.productSpinner.adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.item_spinner,
                    labels,
                ).also { it.setDropDownViewResource(R.layout.item_spinner) }
                val presetId = arguments?.getInt(ARG_ID, -1) ?: -1
                val index = products.indexOfFirst { it.id == presetId }.takeIf { it >= 0 } ?: 0
                if (products.isNotEmpty()) {
                    binding.productSpinner.setSelection(index)
                    selected = products[index]
                    fillPriceHint()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fillPriceHint() {
        val project = selected ?: return
        if (project.cost_price > 0) {
            binding.inputPrice.setText(ProjectEditFragment.formatPrice(project.cost_price))
        }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        val current = binding.inputDate.text?.toString().orEmpty()
        if (current.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            val parts = current.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                inboundDate = "%04d-%02d-%02d".format(y, m + 1, d)
                binding.inputDate.text = inboundDate
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun submit() {
        val project = selected
        if (project?.id == null) {
            Toast.makeText(requireContext(), "请选择产品名", Toast.LENGTH_SHORT).show()
            return
        }
        val date = binding.inputDate.text?.toString()?.trim().orEmpty()
        if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            Toast.makeText(requireContext(), "请选择进货日期", Toast.LENGTH_SHORT).show()
            return
        }
        val price = binding.inputPrice.text?.toString()?.toDoubleOrNull()
        if (price == null) {
            Toast.makeText(requireContext(), "请填写价格", Toast.LENGTH_SHORT).show()
            return
        }
        val qty = binding.inputQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            Toast.makeText(requireContext(), "请填写数量", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).stockIn(
                    StockMoveIn(
                        project_id = project.id,
                        quantity = qty,
                        unit_cost = price,
                        moved_at = date,
                    )
                )
                Toast.makeText(requireContext(), "已入库", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "入库失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "project_id"

        fun args(projectId: Int? = null): Bundle = Bundle().apply {
            putInt(ARG_ID, projectId ?: -1)
        }

        fun today(): String {
            val cal = Calendar.getInstance()
            return "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}
