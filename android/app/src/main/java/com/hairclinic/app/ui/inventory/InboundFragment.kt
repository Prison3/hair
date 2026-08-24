package com.hairclinic.app.ui.inventory

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.StockInRequest
import com.hairclinic.app.data.StockItem
import com.hairclinic.app.databinding.FragmentInboundBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class InboundFragment : Fragment() {
    private var _binding: FragmentInboundBinding? = null
    private val binding get() = _binding!!
    private var inboundDate: String = today()
    private var products: List<StockItem> = emptyList()
    private var selected: StockItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInboundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.inputDate.text = inboundDate
        binding.inputDate.setOnClickListener { pickDate() }
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.submitBtn.setOnClickListener { submit() }
        binding.inputProduct.keyListener = null
        binding.inputUnit.setAdapter(ArrayAdapter(requireContext(), R.layout.item_spinner, UNITS))
        binding.inputUnit.keyListener = null
        selectUnit("个")
        binding.inputProduct.setOnItemClickListener { _, _, position, _ ->
            selected = products.getOrNull(position)
            selectUnit(selected?.unit)
            bindMeta()
        }
        loadProducts()
    }

    private fun loadProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                products = ApiClient.get(requireContext()).listInventory()
                val names = products.map { it.name }
                binding.inputProduct.setAdapter(ArrayAdapter(requireContext(), R.layout.item_spinner, names))
                val presetId = arguments?.getInt(ARG_ITEM_ID, -1) ?: -1
                selected = products.firstOrNull { it.id == presetId }
                    ?: products.firstOrNull { it.name == arguments?.getString(ARG_NAME).orEmpty() }
                if (selected != null) {
                    binding.inputProduct.setText(selected!!.name, false)
                    selectUnit(selected!!.unit)
                } else if (products.size == 1) {
                    selected = products.first()
                    binding.inputProduct.setText(selected!!.name, false)
                    selectUnit(selected!!.unit)
                }
                bindMeta()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载药品失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindMeta() {
        binding.productMeta.isVisible = products.isEmpty()
        binding.productMeta.text = "暂无药品，请先在药品表录入"
    }

    private fun selectUnit(raw: String?) {
        val value = raw?.trim().orEmpty()
        binding.inputUnit.setText(if (value in UNITS) value else "个", false)
    }

    private fun selectedUnit(): String {
        val value = binding.inputUnit.text?.toString()?.trim().orEmpty()
        return if (value in UNITS) value else "个"
    }

    private fun currentProduct(): StockItem? {
        selected?.let { return it }
        val name = binding.inputProduct.text?.toString()?.trim().orEmpty()
        return products.firstOrNull { it.name == name }
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
        val item = currentProduct()
        if (item == null) {
            Toast.makeText(
                requireContext(),
                if (products.isEmpty()) "请先添加药品" else "请选择药品",
                Toast.LENGTH_SHORT,
            ).show()
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
                    StockInRequest(
                        item_id = item.id,
                        name = item.name,
                        spec = item.spec.orEmpty(),
                        quantity = qty,
                        unit = selectedUnit(),
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
        const val ARG_ITEM_ID = "item_id"
        const val ARG_NAME = "name"
        val UNITS = listOf("支", "个", "盒", "次", "套")

        fun args(itemId: Int = -1, name: String? = null): Bundle = Bundle().apply {
            putInt(ARG_ITEM_ID, itemId)
            putString(ARG_NAME, name.orEmpty())
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
